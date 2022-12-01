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

import bisq.monitor.monitor.tasks.bisqnetwork.P2PMarketStats;
import bisq.monitor.monitor.tasks.bisqnetwork.P2PNetworkLoad;
import bisq.monitor.monitor.tasks.pricenode.PriceNodeData;
import bisq.monitor.monitor.tasks.seed.SeedNodeRoundTripTime;
import bisq.monitor.monitor.tasks.tornetwork.TorConnectionTime;
import bisq.monitor.monitor.tasks.tornetwork.TorHiddenServiceStartupTime;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.Util;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
public class TorBasedMonitor {

    public TorBasedMonitor(Properties properties, Reporter reporter) {
        Map<String, String> seedNodeOperatorByAddress = Util.getOperatorByNodeAddress(properties.getProperty("baseCurrencyNetwork"));
        List<MonitorTask> monitorTasks = new ArrayList<>();
        monitorTasks.add(new TorConnectionTime(properties, reporter));
        monitorTasks.add(new TorHiddenServiceStartupTime(properties, reporter));
        monitorTasks.add(new PriceNodeData(properties, reporter));
        monitorTasks.add(new SeedNodeRoundTripTime(properties, reporter, seedNodeOperatorByAddress));
        monitorTasks.add(new P2PNetworkLoad(properties, reporter));
        monitorTasks.add(new P2PMarketStats(properties, reporter, seedNodeOperatorByAddress));
        monitorTasks.forEach(MonitorTask::init);
    }

    public void shutDown() {
        MonitorTask.haltAllMetrics();

        log.info("shutting down tor...");
        Tor tor = Tor.getDefault();
        if (tor != null) {
            tor.shutdown();
        }
    }
}
