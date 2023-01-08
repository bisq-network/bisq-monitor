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

import lombok.extern.slf4j.Slf4j;
import spark.Spark;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Slf4j
public class Server {

    public Server() {
    }

    public void start(int port, RequestHandler requestHandler) {
        try {
            Spark.port(port);
            Spark.post("/", requestHandler::onRequest);
            log.info("Server listening on port {}", port);
        } catch (Throwable t) {
            Spark.stop();
            log.error("Server setup failed", t);
        }
    }

    public CompletableFuture<Void> shutDown() {
        return CompletableFuture.runAsync(() -> {
            Spark.stop();
            try {
                // Spark does not offer a call back when the server is stopped (it starts a thread for it), 
                // so we give a bit of time to be sure it gets gracefully shut down.
                Thread.sleep(500);
            } catch (InterruptedException ignore) {
            }
        }, Executors.newSingleThreadExecutor());
    }
}
