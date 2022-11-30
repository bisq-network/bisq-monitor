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

package bisq.monitor.reporter;

import bisq.common.Timer;
import bisq.common.UserThread;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.ConcurrentHashSet;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Reports our findings to a graphite service.
 *
 * @author Florian Reimair
 */
@Slf4j
public class GraphiteReporter extends Reporter {
    private final LineWriter lineWriter;
    private final BatchWriter batchWriter;
    private final Set<MetricItem> pending = new ConcurrentHashSet<>();
    private final int delayForBatchingSec;
    private final int minItemsForBatching;
    private Timer timer;

    public GraphiteReporter(Properties properties) {
        super();
        lineWriter = new LineWriter(properties);
        batchWriter = new BatchWriter(properties);
        delayForBatchingSec = Integer.parseInt(properties.getProperty("GraphiteReporter.delayForBatchingSec", "1"));
        minItemsForBatching = Integer.parseInt(properties.getProperty("GraphiteReporter.minItemsForBatching", "5"));
    }

    public void report(MetricItem metricItem) {
        pending.add(metricItem);

        if (timer == null) {
            // We wait a bit if more items arrive, so we can batch them
            timer = UserThread.runAfter(this::sendPending, delayForBatchingSec);
        }
    }

    @Override
    public void report(Set<MetricItem> metricItems) {
        pending.addAll(metricItems);

        sendPending();
    }

    private void sendPending() {
        Set<MetricItem> clone = new HashSet<>(pending);
        pending.clear();
        if (clone.size() >= minItemsForBatching) {
            batchWriter.report(clone);
        } else {
            clone.forEach(lineWriter::report);
        }
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    @Override
    public void shutDown() {
        if (timer != null) {
            timer.stop();
        }
    }
}
