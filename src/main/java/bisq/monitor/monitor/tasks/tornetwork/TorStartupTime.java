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

package bisq.monitor.monitor.tasks.tornetwork;

import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.Torrc;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Properties;

/**
 * A Metric to measure the deployment and startup time of the packaged Tor
 * binaries.
 *
 * @author Florian Reimair
 */
@Slf4j
public class TorStartupTime extends MonitorTask {
    private final File torDir;
    private Torrc torOverrides;

    public TorStartupTime(Properties properties, File appDir, Reporter reporter) {
        super(properties, reporter);
        this.torDir = new File(appDir, "TorStartupTime");
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        synchronized (this) {
            LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
            //todo why?
            overrides.put("SOCKSPort", configuration.getProperty("socksPort", "90500"));

            try {
                torOverrides = new Torrc(overrides);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void execute() {
        torDir.delete();
        Tor tor = null;
        long start = System.currentTimeMillis();
        try {
            tor = new NativeTor(torDir, null, torOverrides);
            reporter.report(new Metric(getName(), System.currentTimeMillis() - start));
        } catch (TorCtlException e) {
            log.error("Error at starting tor", e);
        } finally {
            if (tor != null) {
                tor.shutdown();
            }
        }
    }
}
