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
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.Util;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
public abstract class ReportingHandler {
    protected final Reporter reporter;
    private final Set<Metrics> sentReports = new HashSet<>();

    public ReportingHandler(Reporter reporter) {
        this.reporter = reporter;
    }

    public abstract void report(ReportingItems reportingItems);

    public void report(ReportingItems reportingItems, String group) {
        report(reportingItems, group, new HashSet<>());
    }

    public void report(ReportingItems reportingItems, String group, Set<String> excludedKeys) {
        String address = Util.cleanAddress(reportingItems.getAddress());
        reportingItems.stream()
                .filter(item -> item.getGroup().equals(group))
                .filter(item -> !excludedKeys.contains(item.getKey()))
                .map(item -> {
                    String path = "seedNodes." + address + ".seedReport." + item.getPath();
                    if (item instanceof IntegerValueItem) {
                        return new Metrics(path, ((IntegerValueItem) item).getValue());
                    } else if (item instanceof DoubleValueItem) {
                        return new Metrics(path, ((DoubleValueItem) item).getValue());
                    }
                    if (item instanceof StringValueItem) {
                        return new Metrics(path, ((StringValueItem) item).getValue());
                    } else {
                        return null;
                    }
                })
                .forEach(this::sendReport);
    }

    protected void sendReport(Metrics reportItem) {
        if (notYetSent(reportItem)) {
            sentReports.add(reportItem);
            reporter.report(reportItem);
        }
    }

    private boolean notYetSent(Metrics reportItem) {
        return !sentReports.contains(reportItem);
    }
}
