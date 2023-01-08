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

package bisq.monitor.dump;

import bisq.common.ClockWatcher;
import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;
import bisq.core.app.BisqExecutable;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.node.full.RpcService;
import bisq.core.payment.TradeLimits;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.network.p2p.P2PService;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public abstract class ExecutableForDataDump extends BisqExecutable {
    private TradeLimits tradeLimits;

    public ExecutableForDataDump(String fullName, String scriptName, String appName, String version) {
        super(fullName, scriptName, appName, version);
    }

    @Override
    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    public void onSetupComplete() {
        log.info("onSetupComplete");
    }

    @Override
    protected void startApplication() {
        // Pin that as it is used in PaymentMethods and verification in TradeStatistics
        tradeLimits = injector.getInstance(TradeLimits.class);
    }


    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("Start graceful shutDown");
        if (isShutdownInProgress) {
            return;
        }

        isShutdownInProgress = true;

        if (injector == null) {
            log.info("Shut down called before injector was created");
            resultHandler.handleResult();
            System.exit(EXIT_SUCCESS);
        }

        // We do not use the UserThread to avoid that the timeout would not get triggered in case the UserThread
        // would get blocked by a shutdown routine.
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                log.warn("Graceful shutdown not completed in 5 sec.");
                exitWithFailure();
            }
        }, 5000);

        try {
            injector.getInstance(ClockWatcher.class).shutDown();
            injector.getInstance(PriceFeedService.class).shutDown();
            injector.getInstance(TradeStatisticsManager.class).shutDown();
            injector.getInstance(RpcService.class).shutDown();
            injector.getInstance(DaoSetup.class).shutDown();
            injector.getInstance(P2PService.class).shutDown(() -> {
                log.info("P2PService shutdown completed");
                module.close(injector);
                flushAndExit(resultHandler, EXIT_SUCCESS);
            });
        } catch (Throwable t) {
            log.error("App shutdown failed with an exception", t);
            exitWithFailure();
        }
    }

    private void exitWithFailure() {
        // This method does not wait for shutdown hooks. We have a 1 min. timeout at the Tor library which 
        // blocks the shutdown when System.exit() is used 
        Runtime.getRuntime().halt(EXIT_FAILURE);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
