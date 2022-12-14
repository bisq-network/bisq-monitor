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

package bisq.monitor.server;


import bisq.common.UserThread;
import bisq.common.app.AsciiLogo;
import bisq.common.app.Log;
import bisq.common.util.Utilities;
import bisq.monitor.monitor.MonitorMain;
import bisq.monitor.reporter.ConsoleReporter;
import bisq.monitor.reporter.GraphiteReporter;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.PropertiesUtil;
import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Signal;

import java.io.File;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public class ServerMain {
    private static Server server;
    private static RequestHandler requestHandler;
    private static Reporter reporter;

    /**
     * @param args Can be empty or is property file path
     */
    public static void main(String[] args) {
        Properties properties;
        if (args.length == 0) {
            properties = PropertiesUtil.getProperties();
        } else {
            properties = PropertiesUtil.getProperties(args[0].replace("--config=", ""));
        }

        setup(properties);

        reporter = "true".equals(properties.getProperty("GraphiteReporter.enabled", "false")) ?
                new GraphiteReporter(properties) : new ConsoleReporter();
        requestHandler = new RequestHandler(reporter);
        int port = Integer.parseInt(properties.getProperty("Server.port", "13003"));
        server = new Server();
        server.start(port, requestHandler);

        keepRunning();
    }

    public static void setup(Properties properties) {
        Thread.currentThread().setName("ServerMain");

        String appName = properties.getProperty("Server.appDir", "bisq-monitor-server");
        File appDir = new File(Utilities.getUserDataDir(), appName);
        if (!appDir.exists() && !appDir.mkdir()) {
            log.warn("make appDir failed");
        }

        String logPath = Paths.get(appDir.getPath(), "bisq").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        log.info("Log file at: {}.log", logPath);
        AsciiLogo.showAsciiLogo();

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(MonitorMain.class.getSimpleName() + "-UserThread")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));


        Signal.handle(new Signal("INT"), signal -> ServerMain.shutDown());
        Signal.handle(new Signal("TERM"), signal -> ServerMain.shutDown());
    }

    public static void shutDown() {
        log.info("ShutDown started");
        server.shutDown()
                .thenRun(() -> requestHandler.shutDown())
                .thenRun(() -> reporter.shutDown())
                .whenComplete((__, throwable) -> {
                    if (throwable != null) {
                        log.info("Error at shutdown.", throwable);
                    }
                    log.info("ShutDown completed");
                    System.exit(0);
                });
    }

    public static void keepRunning() {
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignore) {
        }
    }
}
