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

package bisq.monitor.tor;

import bisq.monitor.Metric;
import bisq.monitor.clearnet.metric.MarketStats;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.tor.metrics.*;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Monitor executable for the Bisq network.
 *
 * @author Florian Reimair
 */
@Slf4j
public class TorBasedMonitor {

    public TorBasedMonitor(Properties properties, Reporter reporter) {
        List<Metric> metrics = new ArrayList<>();
        metrics.add(new TorStartupTime(reporter));
        metrics.add(new TorRoundTripTime(reporter));
        metrics.add(new TorHiddenServiceStartupTime(reporter));
        metrics.add(new P2PRoundTripTime(reporter));
        metrics.add(new P2PNetworkLoad(reporter));
        metrics.add(new P2PSeedNodeSnapshot(reporter));
        metrics.add(new P2PMarketStats(reporter));
        metrics.add(new PriceNodeStats(reporter));
        metrics.add(new MarketStats(reporter));
        metrics.add(new PriceNodeStats(reporter));

        // configure triggers execution if enabled
        metrics.forEach(metric -> metric.configure(properties));
    }

    public void shutDown() {
        Metric.haltAllMetrics();

        log.info("shutting down tor...");
        Tor tor = Tor.getDefault();
        if (tor != null) {
            tor.shutdown();
        }
    }
}
