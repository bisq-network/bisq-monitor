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

import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.NodeAddress;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorSocket;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class MonitorTask {
    protected final Properties properties;
    protected final Reporter reporter;
    protected final int socketTimeout;
    protected final boolean runSerial;
    private final long interval;
    private final boolean enabled;
    private final File torDir;
    private long lastRunTs;
    protected boolean shutDownInProgress;


    public MonitorTask(Properties properties, Reporter reporter, File appDir, boolean runSerial) {
        this.properties = properties;
        this.reporter = reporter;

        socketTimeout = (int) TimeUnit.SECONDS.toMillis(Integer.parseInt(properties.getProperty("Monitor.socketTimeoutInSec", "120")));

        String className = getClass().getSimpleName();
        interval = Integer.parseInt(properties.getProperty("Monitor." + className + ".interval", "600")) * 1000L;
        String runSerialFromProperties = properties.getProperty("Monitor." + className + ".runSerial", "");
        if ("".equals(runSerialFromProperties)) {
            this.runSerial = runSerial;
        } else {
            this.runSerial = "true".equals(runSerialFromProperties);
        }

        enabled = "true".equals(properties.getProperty("Monitor." + className + ".enabled", "false"));

        torDir = new File(appDir, "tor");
    }

    protected String getName() {
        return getClass().getSimpleName();
    }

    public abstract void run();

    public boolean canRun() {
        if (!enabled) {
            return false;
        }
        if (lastRunTs > System.currentTimeMillis() - interval) {
            log.info("Skip {} because we have not passed our interval time", getName());
            return false;
        }

        lastRunTs = System.currentTimeMillis();
        return true;
    }

    protected String getAddressForMetric(NodeAddress nodeAddress) {
        return nodeAddress.getHostName().contains(".onion") ?
                nodeAddress.getHostNameWithoutPostFix() :
                nodeAddress.getFullAddress()
                        .replace("http://", "")
                        .replace("https://", "");
    }

    protected Socket getSocket(NodeAddress nodeAddress) throws IOException {
        String hostName = nodeAddress.getHostName();
        if (hostName.contains(".onion")) {
            TorSocket torSocket = new TorSocket(hostName, nodeAddress.getPort(), null);
            torSocket.setSoTimeout(socketTimeout);
            return torSocket;
        } else {
            return new Socket(hostName, nodeAddress.getPort());
        }
    }

    protected void maybeCreateTor() {
        if (Tor.getDefault() != null) {
            return;
        }
        try {
            Tor.setDefault(new NativeTor(torDir, null, null));
        } catch (Throwable e) {
            log.error("Could not create tor. ", e);
            torDir.delete();
            throw new RuntimeException(e);
        }
    }

    protected Socks5Proxy getProxy() {
        maybeCreateTor();
        try {
            return Tor.getDefault().getProxy();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    protected void shutdownTor() {
        try {
            if (Tor.getDefault() != null) {
                Tor.getDefault().shutdown();
                Tor.setDefault(null);
            }
        } catch (Throwable e) {
            log.error("Error at shut down tor. ", e);
        }

    }

    abstract public CompletableFuture<Void> shutDown();
}