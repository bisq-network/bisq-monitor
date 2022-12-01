/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.monitor;

import bisq.monitor.monitor.tasks.tornetwork.TorStartupTime;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Monitor executable for the Bisq network.
 *
 * @author Florian Reimair
 */
@Slf4j
public class Monitor {

    public Monitor(File appDir, Properties properties, Reporter reporter) {
        List<MonitorTask> monitorTasks = new ArrayList<>();
        monitorTasks.add(new TorStartupTime(properties, appDir, reporter));
        monitorTasks.forEach(MonitorTask::init);
    }

    public CompletableFuture<Void> shutDown() {
        return CompletableFuture.runAsync(() -> {
            MonitorTask.haltAllMetrics();

            log.info("shutting down tor...");
            Tor tor = Tor.getDefault();
            if (tor != null) {
                tor.shutdown();
            }
        });
    }
}
