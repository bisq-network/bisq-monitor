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
import java.util.stream.Collectors;

@Slf4j
public class GraphiteReporter extends Reporter {
    private final LineWriter lineWriter;
    private final BatchWriter batchWriter;
    private final Set<Metrics> pending = new ConcurrentHashSet<>();
    private final int delayForBatchingSec;
    private final int minItemsForBatching;
    private final int maxItemsForBatching;
    private Timer timer;

    public GraphiteReporter(Properties properties) {
        super();
        lineWriter = new LineWriter(properties);
        batchWriter = new BatchWriter(properties);
        delayForBatchingSec = Integer.parseInt(properties.getProperty("GraphiteReporter.delayForBatchingSec", "1"));
        minItemsForBatching = Integer.parseInt(properties.getProperty("GraphiteReporter.minItemsForBatching", "5"));
        maxItemsForBatching = Integer.parseInt(properties.getProperty("GraphiteReporter.maxItemsForBatching", "1000"));
    }

    public void report(Metrics metrics) {
        pending.add(metrics);

        if (timer == null) {
            // We wait a bit if more items arrive, so we can batch them
            timer = UserThread.runAfter(this::sendPending, delayForBatchingSec);
        }
    }

    @Override
    public void report(Set<Metrics> metrics) {
        pending.addAll(metrics);

        sendPending();
    }

    private void sendPending() {
        Set<Metrics> batch = pending.stream().limit(maxItemsForBatching).collect(Collectors.toSet());
        Set<Metrics> candidates = new HashSet<>(batch);
        pending.removeAll(batch);

        if (candidates.size() >= minItemsForBatching) {
           batchWriter.report(candidates);
        } else {
            candidates.forEach(lineWriter::report);
        }
        if (timer != null) {
            timer.stop();
            timer = null;
        }

        if (!pending.isEmpty()) {
            timer = UserThread.runAfter(this::sendPending, delayForBatchingSec);
        }
    }

    @Override
    public void shutDown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
