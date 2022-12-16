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

import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.CompletableFutureUtil;
import bisq.core.proto.network.CoreNetworkProtoResolver;
import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.Util;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.peers.keepalive.messages.Ping;
import bisq.network.p2p.peers.keepalive.messages.Pong;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opens socket connections to all seed nodes sends a Ping message and report the round trip time when we get back the Pong.
 */
@Slf4j
public class SeedNodeRoundTripTime extends MonitorTask {
    private final Map<String, Long> startTimeByNonce = new HashMap<>();
    private final CoreNetworkProtoResolver networkProtoResolver;
    private final List<CompletableFuture<NetworkEnvelope>> allFutures = new CopyOnWriteArrayList<>();
    private final Set<String> addresses;
    private final ExecutorService executor;

    public SeedNodeRoundTripTime(Properties properties, Reporter reporter, File appDir, boolean runSerial) {
        super(properties, reporter, appDir, runSerial);
        addresses = Util.getSeedNodeAddresses(properties.getProperty("baseCurrencyNetwork"));
        networkProtoResolver = new CoreNetworkProtoResolver(Clock.systemDefaultZone());

        executor = Util.newCachedThreadPool(20);
    }

    @Override
    public void run() {
        try {
            maybeCreateTor();
            allFutures.clear();
            addresses.forEach(address -> {
                log.info("Send request to '{}'", address);
                NodeAddress nodeAddress = new NodeAddress(address);
                String mode = runSerial ? "serial" : "parallel";
                String path = "seedNodes." + getAddressForMetric(nodeAddress) + ".rtt." + mode;
                int nonce = new Random().nextInt();
                startTimeByNonce.put(nodeAddress.getFullAddress() + nonce, System.currentTimeMillis());
                NetworkEnvelope request = new Ping(nonce, 0);
                CompletableFuture<NetworkEnvelope> future = sendMessage(nodeAddress, request)
                        .whenComplete((response, throwable) -> {

                            if (throwable == null) {
                                log.info("Received '{}' from '{}' for request '{}'", response, address, request);
                                if (response instanceof Pong) {
                                    Pong pong = (Pong) response;
                                    String key = nodeAddress.getFullAddress() + pong.getRequestNonce();
                                    if (startTimeByNonce.containsKey(key)) {
                                        long startTime = startTimeByNonce.get(key);
                                        long rrt = System.currentTimeMillis() - startTime;
                                        reporter.report(new Metrics(path, rrt));
                                    }
                                }
                            } else {
                                reporter.report(new Metrics(path, -1));
                            }
                        });
                if (runSerial) {
                    try {
                        // join throws an (unchecked) exception if completed exceptionally.
                        // We use it to enforce completing in serial use case and ignore the exception as it got 
                        // handled already at whenComplete.
                        future.join();
                    } catch (Throwable ignore) {
                    }
                } else {
                    allFutures.add(future);
                }
            });

            // If we used parallel mode we wait until all futures have completed before we return to caller to 
            // execute next task.
            if (!runSerial) {
                CompletableFutureUtil.allOf(allFutures).join();
                allFutures.clear();
            }
        } catch (Throwable e) {
            if (!shutDownInProgress) {
                log.error("Error at SeedNodeRoundTripTime.run", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutDown() {
        shutDownInProgress = true;
        return CompletableFuture.runAsync(() -> {
            new ArrayList<>(allFutures).forEach(future -> future.cancel(true));
            executor.shutdownNow();
        }, Executors.newSingleThreadExecutor());
    }

    private CompletableFuture<NetworkEnvelope> sendMessage(NodeAddress nodeAddress, NetworkEnvelope request) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = getSocket(nodeAddress)) {
                // socket.setSoTimeout(SOCKET_TIMEOUT);
                socket.setSoTimeout(30000);
                OutputStream outputStream = socket.getOutputStream();
                protobuf.NetworkEnvelope requestProto = request.toProtoNetworkEnvelope();
                requestProto.writeDelimitedTo(outputStream);
                outputStream.flush();

                // Wait blocking for response
                protobuf.NetworkEnvelope responseProto = protobuf.NetworkEnvelope.parseDelimitedFrom(socket.getInputStream());
                return networkProtoResolver.fromProto(responseProto);
            } catch (IOException e) {
                if (!shutDownInProgress) {
                    log.error("Error when sending {} to {}. Exception: {}", request, nodeAddress, e.getMessage());
                }
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    protected String getName() {
        String postFix = runSerial ? ".serial" : ".parallel";
        return getClass().getSimpleName() + postFix;
    }
}
