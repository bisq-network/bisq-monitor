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

import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.util.Utilities;
import bisq.core.locale.Res;
import bisq.monitor.monitor.utils.Configurable;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;

import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class MonitorTask extends Configurable implements Runnable {
    private static final String INTERVAL = "interval";
    private static ScheduledExecutorService executor;
    protected final Reporter reporter;
    private ScheduledFuture<?> scheduler;

    protected MonitorTask(Properties properties, Reporter reporter) {
        super.configure(properties);

        this.reporter = reporter;
        reporter.configure(properties);

        if (executor == null) {
            executor = new ScheduledThreadPoolExecutor(6);
        }

        BaseCurrencyNetwork baseCurrencyNetwork = BaseCurrencyNetwork.valueOf(properties.getProperty("baseCurrencyNetwork", "BTC_REGTEST"));
        Version.setBaseCryptoNetworkId(baseCurrencyNetwork.ordinal());
        Res.setup();
    }

    public void init() {
        // decide whether to enable or disable the task
        boolean isEnabled = configuration.getProperty("enabled", "false").equals("true");
        if (configuration.isEmpty() || !isEnabled || !configuration.containsKey(INTERVAL)) {
            stop();

            // some informative log output
            if (configuration.isEmpty())
                log.error("{} is not configured at all. Will not run.", getName());
            else if (!isEnabled)
                log.debug("{} is deactivated. Will not run.", getName());
            else if (!configuration.containsKey(INTERVAL))
                log.error("{} is missing mandatory '" + INTERVAL + "' property. Will not run.", getName());
            else
                log.error("{} is mis-configured. Will not run.", getName());
        } else if (!started()) {
            // check if this Metric got activated after being disabled.
            // if so, resume execution
            start();
            log.info("{} started", getName());
        }
    }

    private void stop() {
        if (scheduler != null)
            scheduler.cancel(false);
    }

    private void start() {
        scheduler = executor.scheduleWithFixedDelay(this, 0,
                Long.parseLong(configuration.getProperty(INTERVAL)), TimeUnit.SECONDS);
    }

    boolean started() {
        if (scheduler != null)
            return !scheduler.isCancelled();
        else
            return false;
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("MonitorTask: " + getName());

            // execute all the things
            synchronized (this) {
                log.info("{} started", getName());
                execute();
                log.info("{} done", getName());
            }
        } catch (Throwable e) {
            log.error("Error at executing monitor task", e);
        }
    }

    /**
     * Gets scheduled repeatedly.
     */
    protected abstract void execute();

    /**
     * initiate an orderly shutdown on all metrics. Blocks until all metrics are
     * shut down or after one minute.
     */
    public static void haltAllMetrics() {
        Utilities.shutdownAndAwaitTermination(executor, 5, TimeUnit.SECONDS);
    }
}
