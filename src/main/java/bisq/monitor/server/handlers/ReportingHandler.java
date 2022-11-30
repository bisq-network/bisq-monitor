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

import bisq.core.monitor.DoubleValueItem;
import bisq.core.monitor.IntegerValueItem;
import bisq.core.monitor.ReportingItems;
import bisq.core.monitor.StringValueItem;
import bisq.monitor.reporter.MetricItem;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.Util;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public abstract class ReportingHandler {
    protected final Reporter reporter;
    protected final Map<String, String> seedNodeOperatorByAddress;
    private final Set<MetricItem> sentReports = new HashSet<>();

    public ReportingHandler(Reporter reporter, Map<String, String> seedNodeOperatorByAddress) {
        this.reporter = reporter;
        this.seedNodeOperatorByAddress = seedNodeOperatorByAddress;
    }

    public abstract void report(ReportingItems reportingItems);

    public void report(ReportingItems reportingItems, String group) {
        report(reportingItems, group, new HashSet<>());
    }

    public void report(ReportingItems reportingItems, String group, Set<String> excludedKeys) {
        String nodeId = Util.getNodeId(reportingItems, seedNodeOperatorByAddress);
        reportingItems.stream()
                .filter(item -> item.getGroup().equals(group))
                .filter(item -> !excludedKeys.contains(item.getKey()))
                .map(item -> {
                    String path = item.getPath() + "." + nodeId;
                    if (item instanceof IntegerValueItem) {
                        return new MetricItem(path, ((IntegerValueItem) item).getValue());
                    } else if (item instanceof DoubleValueItem) {
                        return new MetricItem(path, ((DoubleValueItem) item).getValue());
                    }
                    if (item instanceof StringValueItem) {
                        return new MetricItem(path, ((StringValueItem) item).getValue());
                    } else {
                        return null;
                    }
                })
                .forEach(this::sendReport);
    }

    protected void sendReport(MetricItem reportItem) {
        if (notYetSent(reportItem)) {
            sentReports.add(reportItem);
            reporter.report(reportItem);
        }
    }

    private boolean notYetSent(MetricItem reportItem) {
        return !sentReports.contains(reportItem);
    }
}
