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

package bisq.monitor.server.handlers;

import bisq.common.util.Hex;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.Util;
import bisq.seednode.reporting.ReportingItems;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.Set;

@Slf4j
public class NodeLoadHandler extends ReportingHandler {
    public NodeLoadHandler(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void report(ReportingItems reportingItems) {
        super.report(reportingItems, "node", Set.of("address", "version", "commitHash", "jvmStartTime"));
        String address = Util.cleanAddress(reportingItems.getAddress());
        String path = "seedNodes." + address + ".seedReport.node.";
        Util.findLongValue(reportingItems, "node.jvmStartTimeInSec")
                .ifPresent(jvmStartTime -> {
                    long running = System.currentTimeMillis() / 1000 - jvmStartTime;
                    sendReport(new Metrics(path + "jvmRunningInSec", running));
                });

        Util.findStringValue(reportingItems, "node.version").ifPresent(version -> {
            try {
                int versionAsInt = Integer.parseInt(version.replace(".", ""));
                sendReport(new Metrics(path + "versionAsInt", versionAsInt));
            } catch (Throwable ignore) {
            }
        });
        Util.findStringValue(reportingItems, "node.commitHash").ifPresent(commitHash -> {
            try {
                // Use left 4 bytes
                int commitHashAsInt = new BigInteger(Hex.decode(commitHash.substring(0, 8))).intValue();
                // Need unsigned int for grafana
                long unsignedLong = Integer.toUnsignedLong(commitHashAsInt);
                sendReport(new Metrics(path + "commitHash", unsignedLong));
            } catch (Throwable e) {
                log.error("Could not convert commit hash. commitHash={}; error={}", commitHash, e.toString());
            }
        });
    }
}