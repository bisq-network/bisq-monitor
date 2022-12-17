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

import bisq.common.util.CompletableFutureUtil;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * We execute all tasks serial. Once all tasks are completed we check if we have passed the min. interval time and if
 * so we repeat running all tasks. It can be that tasks have their own interval time not passed and skip execution.
 * If we are too fast we put the thread on sleep for the interval time to not execute the while loop too often.
 * There is no guarantee for tasks that their interval gets met if the sum of all other tasks take longer.
 * But there is a guarantee that the task will not execute faster as the defined interval.
 * The reason why we do not want to have parallel execution here is that tasks could interfere each other's results
 * if Tor network get more load from parallel tasks. We prefer to have the tasks in isolated conditions to get more
 * reliable results.
 */
@Slf4j
public class MonitorTaskRunner {
    private final static long INTERVAL = 5000;
    private final List<MonitorTask> monitorTasks = new ArrayList<>();
    private long lastRunTs;
    private volatile boolean stopped;

    public MonitorTaskRunner() {
    }

    public void add(MonitorTask monitorTask) {
        monitorTasks.add(monitorTask);
    }

    public void start() {
        while (!stopped) {
            if (lastRunTs > System.currentTimeMillis() - INTERVAL) {
                log.info("We iterate the loop too fast. We put the thread on sleep and try again to see if any " +
                        "task is ready to run again");
                try {
                    Thread.sleep(INTERVAL);
                } catch (InterruptedException ignore) {
                }
            }

            lastRunTs = System.currentTimeMillis();
            log.info("Start to run all tasks");
            // Each task run call is blocking until completed.
            // Once a task is completed we are ready for the next task
            monitorTasks.forEach(task -> {
                try {
                    if (!stopped && task.canRun()) {
                        log.info("Run task '{}'", task.getName());
                        task.run();
                    }
                } catch (Throwable t) {
                    log.error("Error at run task {}. Error: {}", task.getClass().getSimpleName(), t.getMessage());
                }
            });
            if (!stopped) {
                log.info("All tasks have been completed");
            }
        }
    }

    public CompletableFuture<Void> shutDown() {
        stopped = true;
        Set<CompletableFuture<Void>> futures = new HashSet<>();
        monitorTasks.forEach(task -> futures.add(task.shutDown()));
        return CompletableFutureUtil.allOf(futures)
                .handle((__, throwable) -> {
                    if (Tor.getDefault() != null) {
                        log.info("Shut down tor");
                        Tor.getDefault().shutdown();
                        log.info("Tor shutdown completed");
                    }
                    return null;
                });
    }
}