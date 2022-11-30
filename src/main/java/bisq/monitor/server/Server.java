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

import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;
import spark.Spark;

import java.util.Properties;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class Server {
    private final RequestHandler requestHandler;

    public Server(Properties properties, Reporter reporter) {
        requestHandler = new RequestHandler(properties, reporter);
        try {
            int port = Integer.parseInt(properties.getProperty("Server.port"));
            Spark.port(port);

            boolean useTLS = "true".equals(properties.getProperty("Server.useTLS"));
            if (useTLS) {
                String keystoreFile = properties.getProperty("Server.keystoreFile");
                String keystorePassword = properties.getProperty("Server.keystorePassword");
                String truststoreFile = properties.getProperty("Server.truststoreFile");
                String truststorePassword = properties.getProperty("Server.truststorePassword");
                Spark.secure(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
            }
            Spark.post("/", requestHandler::onRequest);
            log.info("Server setup for listening on port {}", port);
        } catch (Throwable t) {
            Spark.stop();
            log.error("Server setup failed", t);
        }
    }

    public CompletableFuture<Void> shutDown() {
        return CompletableFuture.runAsync(() -> {
            Spark.stop();
            requestHandler.shutdown();
        });
    }
}
