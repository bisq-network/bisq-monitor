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

import com.google.common.base.Charsets;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Report to graphite server using the plaintext protocol
 * See: <a href="https://graphite.readthedocs.io/en/latest/feeding-carbon.html">...</a>
 */
@Slf4j
public class LineWriter {
    private final String host;
    private final int port;

    public LineWriter(Properties properties) {
        super();
        String[] tokens = properties.getProperty("GraphiteReporter.serviceUrl").split(":");
        host = tokens[0];
        port = Integer.parseInt(tokens[1]);
    }

    public CompletableFuture<Boolean> report(Metric metric) {
        // trailing line break is needed
        String payload = metric.getPath() + " " + metric.getValue() + " " + metric.getTimeStampInSec() + "\n";
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket(host, port)) {
                socket.getOutputStream().write(payload.getBytes(Charsets.UTF_8));
                return true;
            } catch (Exception e) {
                log.warn("Error writing to Graphite: {}", e.getMessage());
                return false;
            }
        });
    }
}
