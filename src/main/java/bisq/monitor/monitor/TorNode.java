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

import bisq.common.UserThread;
import bisq.network.p2p.NodeAddress;
import bisq.tor.Tor;
import bisq.tor.TorServerSocket;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@Slf4j
public class TorNode {
    @Getter
    private final int socketTimeout;
    public Tor tor;
    private final File torDir;

    public TorNode(Properties properties, File appDir) {
        torDir = new File(appDir, "tor");
        socketTimeout = (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(properties.getProperty("Monitor.socketTimeoutInSec", "120")));
    }

    public void maybeCreateTor() {
        if (tor != null) {
            return;
        }
        try {
            tor = new Tor(torDir.getAbsolutePath());
            tor.start();
        } catch (Throwable e) {
            log.error("Could not create tor. We delete the tor dir. ", e);
            boolean deleted = torDir.delete();
            if (!deleted) {
                log.error("Deleting tor dir {} failed. We try to create tor again after 2 seconds. " +
                        "If it fails again we shut down.", torDir.getAbsolutePath());
                UserThread.runAfter(() -> {
                    torDir.delete();
                    try {
                        if (tor != null) {
                            log.error("Tor.getDefault() would be expected to be null");
                            tor.shutdown();
                        }
                        tor = new Tor(torDir.getAbsolutePath());
                        tor.start();
                    } catch (Throwable e2) {
                        log.error("Cannot create tor. We shut down. ", e2);
                        MonitorMain.shutDown(1);
                    }
                }, 2);
            }
            throw new RuntimeException(e);
        }
    }

    public Socket getSocket(NodeAddress nodeAddress) throws IOException {
        String hostName = nodeAddress.getHostName();
        if (hostName.contains(".onion")) {
            Socket socket = tor.getSocket(null); // Blocking call. Takes 5-15 sec usually.
            socket.setSoTimeout(socketTimeout);
            socket.connect(new InetSocketAddress(hostName, nodeAddress.getPort()));
            return socket;
        } else {
            return new Socket(hostName, nodeAddress.getPort());
        }
    }

    public Socks5Proxy getProxy() {
        maybeCreateTor();
        try {
            return tor.getSocks5Proxy(null);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void shutDown() {
        try {
            if (tor != null) {
                tor.shutdown();
                tor = null;
            }
        } catch (Throwable e) {
            log.error("Error at shut down tor. ", e);
        }

    }

    public TorServerSocket getTorServerSocket() throws IOException {
        return tor.getTorServerSocket();
    }
}