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

import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.Util;
import bisq.network.p2p.NodeAddress;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Opens socket connections to the given hosts and report time how long it took.
 * If we have a connection once established and the socket closed, and later re-connect it is much faster.
 */
@Slf4j
public class TorConnectionTime extends MonitorTask {
    private final List<String> addresses;
    private final boolean restartTor;
    private final ExecutorService executor;

    public TorConnectionTime(Properties properties, Reporter reporter, File appDir, boolean restartTor) {
        super(properties, reporter, appDir, true);

        this.restartTor = restartTor;
        addresses = List.of(properties.getProperty("Monitor.TorConnectionTime.hosts", "").split(","));
        executor = Util.newCachedThreadPool(addresses.size());
    }

    @Override
    public void run() {
        try {
            addresses.forEach(address -> {
                maybeCreateTor();
                log.info("Connect to '{}'", address);
                NodeAddress nodeAddress = new NodeAddress(address);
                try {
                    // join throws an (unchecked) exception if completed exceptionally.
                    // We use it to enforce completing in serial use case and ignore the exception as it got 
                    // handled already at whenComplete.
                    connect(nodeAddress)
                            .whenComplete((duration, throwable) -> {
                                String mode = restartTor ? ".restartTor" : ".reconnect";
                                String path = "torNetwork.onionServices." + getAddressForMetric(nodeAddress) + ".connectionTime." + mode;
                                if (throwable == null) {
                                    reporter.report(new Metrics(path, duration));
                                } else {
                                    reporter.report(new Metrics(path, -1));
                                }
                            })
                            .join();
                } catch (Throwable ignore) {
                }
                if (restartTor) {
                    shutdownTor();
                }
            });
        } catch (Throwable ignore) {
        }
    }

    @Override
    public CompletableFuture<Void> shutDown() {
        shutDownInProgress = true;
        return CompletableFuture.runAsync(executor::shutdownNow, Executors.newSingleThreadExecutor());
    }

    private CompletableFuture<Long> connect(NodeAddress nodeAddress) {
        return CompletableFuture.supplyAsync(() -> {
            long ts = System.currentTimeMillis();
            try (Socket socket = getSocket(nodeAddress)) {
                log.info("Connection established to '{}'. socket={}", nodeAddress, socket);
                return System.currentTimeMillis() - ts;
            } catch (IOException e) {
                log.error("Error when connecting to {}. Exception: {}", nodeAddress, e.getMessage());
                throw new RuntimeException(e);
            }
        }, executor);
    }

    @Override
    protected String getName() {
        String postFix = restartTor ? ".restartTor" : ".reconnect";
        return getClass().getSimpleName() + postFix;
    }

}
