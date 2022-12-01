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
import bisq.monitor.monitor.tor.TorNode;
import bisq.monitor.monitor.utils.ThreadGate;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.HiddenServiceSocket;

import java.io.File;
import java.util.Properties;

/**
 * A Metric to measure the startup time of a Tor Hidden Service on a already
 * running Tor.
 *
 * @author Florian Reimair
 */
@Slf4j
public class TorHiddenServiceStartupTime extends MonitorTask {
    private final String hiddenServiceDirectory = getName();
    private final ThreadGate gate = new ThreadGate();

    public TorHiddenServiceStartupTime(Properties properties, Reporter reporter) {
        super(properties, reporter);
    }

    @Override
    protected void execute() {
        int localPort = Integer.parseInt(configuration.getProperty("localPort", "9998"));
        int servicePort = Integer.parseInt(configuration.getProperty("servicePort", "9999"));

        // clear directory, so we get a new onion address every time
        new File(TorNode.getTorDir() + "/" + hiddenServiceDirectory).delete();
        gate.engage();
        long start = System.currentTimeMillis();
        HiddenServiceSocket hiddenServiceSocket = new HiddenServiceSocket(localPort, hiddenServiceDirectory, servicePort);
        hiddenServiceSocket.addReadyListener(socket -> {
            reporter.report(new Metric(getName(), System.currentTimeMillis() - start));
            gate.proceed();
            return null;
        });

        gate.await();
        hiddenServiceSocket.close();
    }
}
