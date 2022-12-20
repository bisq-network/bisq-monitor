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

import bisq.monitor.monitor.tasks.*;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Monitor {
    private final MonitorTaskRunner monitorTaskRunner = new MonitorTaskRunner();
    private final TorNode torNode;

    public Monitor(Properties properties, Reporter reporter, File appDir) {
        torNode = new TorNode(properties, appDir);
        monitorTaskRunner.add(new TorStartupTime(properties, reporter, torNode));
        monitorTaskRunner.add(new TorHiddenServiceStartupTime(properties, reporter, torNode));
        monitorTaskRunner.add(new PriceNodeData(properties, reporter, torNode));
        monitorTaskRunner.add(new SeedNodeRoundTripTime(properties, reporter, torNode, false));
        monitorTaskRunner.add(new SeedNodeRoundTripTime(properties, reporter, torNode, true));
        monitorTaskRunner.add(new TorConnectionTime(properties, reporter, torNode, false));
        monitorTaskRunner.add(new TorConnectionTime(properties, reporter, torNode, true));
    }

    public void start() {
        monitorTaskRunner.start();
    }

    public CompletableFuture<Void> shutDown() {
        return monitorTaskRunner.shutDown().thenRunAsync(torNode::shutDown);
    }
}
