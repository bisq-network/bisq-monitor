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

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.Version;
import bisq.common.handlers.ResultHandler;
import bisq.core.app.misc.ModuleForAppWithP2p;
import bisq.monitor.dump.handlers.OffersHandler;
import bisq.monitor.dump.handlers.TradeStatisticsHandler;
import bisq.monitor.utils.PropertiesUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Properties;

@Slf4j
public class DataDumpMain extends ExecutableForDataDump {
    private DataDump dataDump;

    public DataDumpMain(String appDir) {
        super("DataDumpMain", "", appDir, Version.VERSION);
    }

    public static void main(String[] args, Properties monitorProperties) {
        ReporterProvider.setMonitorProperties(Optional.of(monitorProperties));
        TradeStatisticsHandler.setMonitorProperties(Optional.of(monitorProperties));
        OffersHandler.setMonitorProperties(Optional.of(monitorProperties));

        String appDir = monitorProperties.getProperty("DataDump.appDir", "bisq-monitor-datadump");
        new DataDumpMain(appDir).execute(args);
    }

    public static void main(String[] args) {
        main(args, PropertiesUtil.getProperties());
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        keepRunning();
    }

    @Override
    protected void addCapabilities() {
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                dataDump = new DataDump();
                UserThread.execute(this::onApplicationLaunched);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
    }

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        log.error("Shut down because of unhandled exception", throwable);
        System.exit(1);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new ModuleForAppWithP2p(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        dataDump.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        super.startApplication();
        dataDump.startApplication();
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        dataDump.shutDown();
        super.gracefulShutDown(resultHandler);
    }
}
