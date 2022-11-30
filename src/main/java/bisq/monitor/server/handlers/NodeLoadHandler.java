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
import bisq.core.monitor.ReportingItems;
import bisq.monitor.reporter.MetricItem;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.Util;
import lombok.extern.slf4j.Slf4j;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

@Slf4j
public class NodeLoadHandler extends ReportingHandler {
    public NodeLoadHandler(Reporter reporter, Map<String, String> seedNodeOperatorByAddress) {
        super(reporter, seedNodeOperatorByAddress);
    }

    @Override
    public void report(ReportingItems reportingItems) {
        super.report(reportingItems, "node", Set.of("address", "version", "commitHash", "jvmStartTime"));
        String nodeId = Util.getNodeId(reportingItems, seedNodeOperatorByAddress);
        Util.findIntegerValue(reportingItems, "node.jvmStartTimeInSec")
                .ifPresent(jvmStartTime -> {
                    long running = System.currentTimeMillis() / 1000 - jvmStartTime;
                    sendReport(new MetricItem("node.jvmRunningInSec." + nodeId, running));
                });

        Util.findStringValue(reportingItems, "node.version").ifPresent(version -> {
            try {
                int versionAsInt = Integer.parseInt(version.replace(".", ""));
                sendReport(new MetricItem("node.versionAsInt." + nodeId, versionAsInt));
            } catch (Throwable ignore) {
            }
        });
        Util.findStringValue(reportingItems, "node.commitHash").ifPresent(commitHash -> {
            try {
                int commitHashAsInt = new BigInteger(Hex.decode(commitHash)).intValue();
                sendReport(new MetricItem("node.commitHashAsInt." + nodeId, commitHashAsInt));
            } catch (Throwable ignore) {
            }
        });
    }
}