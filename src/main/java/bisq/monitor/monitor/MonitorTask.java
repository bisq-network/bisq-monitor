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
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class MonitorTask {
    protected final Properties properties;
    protected final Reporter reporter;
    protected final TorNode torNode;
    protected final boolean runSerial;
    private final long interval;
    private final boolean enabled;

    private long lastRunTs;
    protected boolean shutDownInProgress;

    public MonitorTask(Properties properties, Reporter reporter, TorNode torNode, boolean runSerial) {
        this.properties = properties;
        this.reporter = reporter;
        this.torNode = torNode;


        String className = getClass().getSimpleName();
        interval = Integer.parseInt(properties.getProperty("Monitor." + className + ".interval", "600")) * 1000L;
        String runSerialFromProperties = properties.getProperty("Monitor." + className + ".runSerial", "");
        if ("".equals(runSerialFromProperties)) {
            this.runSerial = runSerial;
        } else {
            this.runSerial = "true".equals(runSerialFromProperties);
        }

        enabled = "true".equals(properties.getProperty("Monitor." + className + ".enabled", "false"));

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

    abstract public CompletableFuture<Void> shutDown();
}