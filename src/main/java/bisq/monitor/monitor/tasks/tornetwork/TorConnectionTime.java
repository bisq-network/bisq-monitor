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
import bisq.monitor.monitor.tor.OnionParser;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.NodeAddress;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import java.io.IOException;
import java.util.Properties;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Open a tor socket connection to the given hosts and measure the time how long it took for establishing the connection.
 */
@Slf4j
public class TorConnectionTime extends MonitorTask {
    public TorConnectionTime(Properties properties, Reporter reporter) {
        super(properties, reporter);
    }

    @Override
    protected void execute() {
        SocksSocket socket;
        NodeAddress nodeAddress = null;
        try {
            Tor tor = Tor.getDefault();
            checkNotNull(tor, "tor must not be null");
            Socks5Proxy proxy = tor.getProxy();
            for (String host : configuration.getProperty("hosts", "").split(",")) {
                nodeAddress = OnionParser.getNodeAddress(host);
                long start = System.currentTimeMillis();
                // Connect to node
                socket = new SocksSocket(proxy, nodeAddress.getHostName(), nodeAddress.getPort());
                reporter.report(new Metric(getName(), nodeAddress.getHostNameWithoutPostFix(), String.valueOf(System.currentTimeMillis() - start)));
                socket.close();
            }
        } catch (TorCtlException | IOException e) {
            if (nodeAddress != null) {
                log.error("Error while connecting to {}", nodeAddress);
            }
            log.error("Error at connection to host", e);
        }
    }
}
