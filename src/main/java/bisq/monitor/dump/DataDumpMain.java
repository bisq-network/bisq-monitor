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
import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.app.misc.ModuleForAppWithP2p;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.Properties;

@Slf4j
public class DataDumpMain extends ExecutableForAppWithP2p {
    private static final String VERSION = "1.0.0";
    private DataDump dataDump;

    public DataDumpMain() {
        super("Bisq Statsnode", "bisq-statistics", "bisq_statistics", VERSION);
    }

    public static void main(String[] args, Properties monitorProperties) {
        ReporterProvider.setMonitorProperties(Optional.of(monitorProperties));
        log.info("BisqNetworkDataMonitorMain.VERSION: " + VERSION);
        new DataDumpMain().execute(args);
    }

    public static void main(String[] args) {
        log.info("BisqNetworkDataMonitorMain.VERSION: " + VERSION);
        new DataDumpMain().execute(args);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);

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
}
