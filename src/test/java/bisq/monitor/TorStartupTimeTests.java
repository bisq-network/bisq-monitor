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

package bisq.monitor;


import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import bisq.monitor.metric.Metric;
import bisq.monitor.metric.TorStartupTime;

public class TorStartupTimeTests {

    private class DummyReporter extends Reporter {

        private long result;

        @Override
        public void report(long value) {
            result = value;

        }
        public long results() {
            return result;
        }

        @Override
        public void report(Map<String, String> values) {
            report(Long.parseLong(values.values().iterator().next()));
        }

        @Override
        public void report(Map<String, String> values, String prefix) {
            report(values);
        }

    }

    @Test
    public void run() throws Exception {

        DummyReporter reporter = new DummyReporter();

        // configure
        Properties configuration = new Properties();
        configuration.put("TorStartupTime.enabled", "true");
        configuration.put("TorStartupTime.run.interval", "2");
        configuration.put("TorStartupTime.run.socksPort", "9999");

        Metric DUT = new TorStartupTime(reporter);
        DUT.configure(configuration);

        // start
        DUT.start();

        // give it some time and then stop
        Thread.sleep(15 * 1000);
        DUT.shutdown();

        // observe results
        Assert.assertTrue(reporter.results() > 0);
    }
}
