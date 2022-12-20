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

import bisq.common.util.Utilities;
import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.monitor.TorNode;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.tor.TorServerSocket;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * Opens socket connections to the given hosts and report time how long it took.
 * If we have a connection once established and the socket closed, and later re-connect it is much faster.
 */
@Slf4j
public class TorHiddenServiceStartupTime extends MonitorTask {
    private CompletableFuture<TorServerSocket> future;

    public TorHiddenServiceStartupTime(Properties properties, Reporter reporter, TorNode torNode) {
        super(properties, reporter, torNode, true);
    }

    @Override
    public void run() {
        try {
            torNode.maybeCreateTor();
            long ts = System.currentTimeMillis();
            future = CompletableFuture.supplyAsync(() -> {
                try {
                    TorServerSocket torServerSocket = torNode.getTorServerSocket();
                    torServerSocket.bind(9999, "default");
                    return torServerSocket;
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, Utilities.getSingleThreadExecutor("createHS"));
            future.join();
            reporter.report(new Metrics("torNetwork.hiddenServicePublishingTime", System.currentTimeMillis() - ts));
        } catch (Throwable e) {
            if (!shutDownInProgress) {
                log.error("Error at TorHiddenServiceStartupTime.run", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutDown() {
        shutDownInProgress = true;
        return CompletableFuture.runAsync(() -> {
            if (future != null) {
                future.cancel(true);
            }
        }, Executors.newSingleThreadExecutor());
    }
}
