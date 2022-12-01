/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.monitor.tasks;

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.core.account.witness.AccountAgeWitnessStore;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.trade.statistics.TradeStatistics3Store;
import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.monitor.tor.AvailableTor;
import bisq.monitor.monitor.tor.OnionParser;
import bisq.monitor.monitor.utils.ThreadGate;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.CloseConnectionMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.TorNetworkNode;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Contacts a list of hosts and asks them for all the data excluding persisted messages. The
 * answers are then compiled into buckets of message types. Based on these
 * buckets, the Metric reports (for each host) the message types observed and
 * their number.
 *
 * @author Florian Reimair
 */
@Slf4j
public abstract class TorBasedMonitorTask extends MonitorTask implements MessageListener {
    private static final String HOSTS = "hosts";
    private static final String TOR_PROXY_PORT = "run.torProxyPort";
    private static final String DATABASE_DIR = "run.dbDir";

    protected final Map<NodeAddress, Statistics<?>> bucketsPerHost = new ConcurrentHashMap<>();
    private final ThreadGate gate = new ThreadGate();
    protected final Set<byte[]> hashes = new TreeSet<>(Arrays::compare);
    protected final Map<String, String> seedNodeOperatorByAddress;
    private final List<String> seedNodes;

    /**
     * Statistics Interface for use with derived classes.
     *
     * @param <T> the value type of the statistics implementation
     */
    public abstract static class Statistics<T> {
        protected final Map<String, T> buckets = new HashMap<>();

        abstract public void log(Object message);

        public Map<String, T> values() {
            return buckets;
        }

        public void reset() {
            buckets.clear();
        }
    }

    public TorBasedMonitorTask(Properties properties, Reporter reporter, Map<String, String> seedNodeOperatorByAddress) {
        super(properties, reporter);
        this.seedNodeOperatorByAddress = seedNodeOperatorByAddress;
        seedNodes = new ArrayList<>(seedNodeOperatorByAddress.keySet());
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        if (hashes.isEmpty() && configuration.getProperty(DATABASE_DIR) != null) {
            File dir = new File(configuration.getProperty(DATABASE_DIR));
            String networkPostfix = "_" + BaseCurrencyNetwork.values()[Version.getBaseCurrencyNetwork()].toString();
            try {
                CorePersistenceProtoResolver persistenceProtoResolver = new CorePersistenceProtoResolver(null, null);

                //TODO will not work with historical data... should be refactored to re-use code for reading resource files
                TradeStatistics3Store tradeStatistics3Store = new TradeStatistics3Store();
                PersistenceManager<TradeStatistics3Store> tradeStatistics3PersistenceManager = new PersistenceManager<>(dir,
                        persistenceProtoResolver, null);
                tradeStatistics3PersistenceManager.initialize(tradeStatistics3Store,
                        tradeStatistics3Store.getDefaultStorageFileName() + networkPostfix,
                        PersistenceManager.Source.NETWORK);
                TradeStatistics3Store persistedTradeStatistics3Store = tradeStatistics3PersistenceManager.getPersisted();
                if (persistedTradeStatistics3Store != null) {
                    tradeStatistics3Store.getMap().putAll(persistedTradeStatistics3Store.getMap());
                }
                hashes.addAll(tradeStatistics3Store.getMap().keySet().stream()
                        .map(byteArray -> byteArray.bytes).collect(Collectors.toSet()));

                AccountAgeWitnessStore accountAgeWitnessStore = new AccountAgeWitnessStore();
                PersistenceManager<AccountAgeWitnessStore> accountAgeWitnessPersistenceManager = new PersistenceManager<>(dir,
                        persistenceProtoResolver, null);
                accountAgeWitnessPersistenceManager.initialize(accountAgeWitnessStore,
                        accountAgeWitnessStore.getDefaultStorageFileName() + networkPostfix,
                        PersistenceManager.Source.NETWORK);
                AccountAgeWitnessStore persistedAccountAgeWitnessStore = accountAgeWitnessPersistenceManager.getPersisted();
                if (persistedAccountAgeWitnessStore != null) {
                    accountAgeWitnessStore.getMap().putAll(persistedAccountAgeWitnessStore.getMap());
                }
                hashes.addAll(accountAgeWitnessStore.getMap().keySet().stream()
                        .map(byteArray -> byteArray.bytes).collect(Collectors.toSet()));
            } catch (NullPointerException e) {
                // in case there is no store file
                log.error("There is no storage file where there should be one: {}", dir.getAbsolutePath());
            }
        }
    }

    @Override
    protected void execute() {
        // start the network node
        NetworkNode networkNode = new TorNetworkNode(Integer.parseInt(configuration.getProperty(TOR_PROXY_PORT, "9054")),
                new CoreNetworkProtoResolver(Clock.systemDefaultZone()), false,
                new AvailableTor("unused"), null);
        // we do not need to start the networkNode, as we do not need the HS
        //networkNode.start(this);

        // clear our buckets
        bucketsPerHost.clear();

        getRequests().forEach(request -> send(networkNode, request));

        report();
    }

    protected abstract List<NetworkEnvelope> getRequests();

    protected void send(NetworkNode networkNode, NetworkEnvelope message) {
        ArrayList<Thread> threadList = new ArrayList<>();
        // for each configured host
        String hosts = configuration.getProperty(HOSTS, "");
        List<String> nodes = hosts != null && !hosts.isEmpty() ? List.of(hosts.split(",")) : seedNodes;
        for (String current : nodes) {
            threadList.add(new Thread(() -> {
                try {
                    NodeAddress node = OnionParser.getNodeAddress(current);
                    aboutToSend(message);
                    SettableFuture<Connection> future = networkNode.sendMessage(node, message);
                    Futures.addCallback(future, new FutureCallback<>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            connection.addMessageListener(TorBasedMonitorTask.this);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            gate.proceed();
                            log.error("Sending {} failed. That is expected if the peer is offline.\n\tException={}",
                                    message.getClass().getSimpleName(), throwable.getMessage());
                        }
                    }, MoreExecutors.directExecutor());

                } catch (Exception e) {
                    gate.proceed(); // release the gate on error
                    e.printStackTrace();
                }
            }, "Thread-" + current));
        }

        gate.engage(threadList.size());

        // start all threads and wait until they all finished. We do that so we can
        // minimize the time between querying the hosts and therefore the chance of
        // inconsistencies.
        threadList.forEach(Thread::start);

        gate.await();
    }

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (processMessage(networkEnvelope, connection)) {
            gate.proceed();
        } else if (networkEnvelope instanceof CloseConnectionMessage) {
            gate.unlock();
        } else {
            log.warn("Got an unexpected message of type <{}>",
                    networkEnvelope.getClass().getSimpleName());
        }
        connection.removeMessageListener(this);
    }

    protected abstract boolean processMessage(NetworkEnvelope networkEnvelope, Connection connection);

    protected void aboutToSend(NetworkEnvelope message) {
    }

    public abstract void report();

}
