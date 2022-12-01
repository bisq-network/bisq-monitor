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
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Opens socket connections to the given hosts and report time how long it took.
 * If we have a connection once established and the socket closed, and later re-connect it is much faster.
 */
@Slf4j
public class TorStartupTime extends MonitorTask {

    public TorStartupTime(Properties properties, Reporter reporter, File appDir) {
        super(properties, reporter, appDir, true);
    }

    @Override
    public void run() {
        try {
            shutdownTor();
            long ts = System.currentTimeMillis();
            maybeCreateTor();
            reporter.report(new Metrics("torNetwork.torStartupTime", System.currentTimeMillis() - ts));
        } catch (Throwable e) {
            if (!shutDownInProgress) {
                log.error("Error at TorStartupTime.run", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutDown() {
        shutDownInProgress = true;
        return CompletableFuture.completedFuture(null);
    }
}
