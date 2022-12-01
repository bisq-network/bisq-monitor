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


import bisq.common.UserThread;
import bisq.common.app.Log;
import bisq.common.config.Config;
import bisq.common.util.Utilities;
import bisq.core.setup.CoreNetworkCapabilities;
import bisq.monitor.PropertiesUtil;
import bisq.monitor.monitor.tor.AvailableTor;
import bisq.monitor.monitor.tor.TorNode;
import bisq.monitor.reporter.ConsoleReporter;
import bisq.monitor.reporter.GraphiteReporter;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.Server;
import ch.qos.logback.classic.Level;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Signal;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Slf4j
public class MonitorMain {
    private static boolean stopped;
    private static Monitor monitor;
    @Nullable
    private static Server server;
    @Nullable
    private static TorNode torNode;
    @Nullable
    private static TorBasedMonitor torBasedMonitor;

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

        String appName = properties.getProperty("appDir");
        File appDir = new File(Utilities.getUserDataDir(), appName);
        if (!appDir.exists() && !appDir.mkdir()) {
            log.warn("make appDir failed");
        }
        setup(appDir);

        CoreNetworkCapabilities.setSupportedCapabilities(new Config());

        Reporter reporter = "true".equals(properties.getProperty("GraphiteReporter.enabled", "false")) ?
                new GraphiteReporter(properties) : new ConsoleReporter();
        boolean useTor = properties.getProperty("useTor").equals("true");
        boolean useServer = properties.getProperty("useServer").equals("true");

        CompletableFuture.runAsync(() -> {
                    monitor = new Monitor(appDir, properties, reporter);
                }, Utilities.getSingleThreadExecutor("Monitor"))
                .thenRunAsync(() -> {
                    if (useServer) {
                        server = new Server(properties, reporter);
                    }
                }, Utilities.getSingleThreadExecutor("Server"))
                .thenRunAsync(() -> {
                    if (useTor) {
                        AvailableTor.setAppDir(appDir);
                        torNode = new TorNode(appDir);
                    }
                }, Utilities.getSingleThreadExecutor("TorNode"))
                .thenRunAsync(() -> {
                    if (useTor) {
                        torBasedMonitor = new TorBasedMonitor(properties, reporter);
                    }
                }, Utilities.getSingleThreadExecutor("TorBasedMonitor"));

        keepRunning();
    }

    public static void setup(File appDir) {
        String logPath = Paths.get(appDir.getPath(), "bisq").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(MonitorMain.class.getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        Signal.handle(new Signal("INT"), signal -> UserThread.execute(MonitorMain::shutDown));
        Signal.handle(new Signal("TERM"), signal -> UserThread.execute(MonitorMain::shutDown));

        Runtime.getRuntime().addShutdownHook(new Thread(MonitorMain::shutDown, "Shutdown Hook"));
    }

    public static void shutDown() {
        stopped = true;
        if (monitor == null) {
            System.exit(0);
            return;
        }

        monitor.shutDown()
                .thenRun(() -> {
                    if (server != null) {
                        server.shutDown();
                    }
                }).thenRun(() -> {
                    if (torBasedMonitor != null) {
                        torBasedMonitor.shutDown();
                    }
                })
                .thenRun(() -> {
                    if (torNode != null) {
                        torNode.shutDown();
                    }
                })
                .thenRun(() -> System.exit(0));
    }

    public static void keepRunning() {
        while (!stopped) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
