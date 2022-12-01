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

import lombok.extern.slf4j.Slf4j;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Report to graphite server using the pickle protocol
 * See: <a href="https://graphite.readthedocs.io/en/latest/feeding-carbon.html">...</a>
 * <p>
 * Partly based on https://github.com/BrightcoveOS/metrics-graphite-pickle/blob/master/src/main/java/com/brightcove/metrics/reporting/GraphitePickleReporter.java
 * and https://github.com/ufctester/apache-jmeter/blob/master/src/components/org/apache/jmeter/visualizers/backend/graphite/PickleGraphiteMetricsSender.java
 */
@Slf4j
public class BatchWriter {
    private static final char APPEND = 'a';
    private static final char LIST = 'l';
    private static final char LONG = 'L';
    private static final char MARK = '(';
    private static final char STOP = '.';
    private static final char STRING = 'S';
    private static final char TUPLE = 't';
    private static final char QUOTE = '\'';
    private static final char LF = '\n';

    private final String host;
    private final int port;

    public BatchWriter(Properties properties) {
        String[] tokens = properties.getProperty("GraphiteReporter.serviceUrl").split(":");
        host = tokens[0];
        port = Integer.parseInt(properties.getProperty("GraphiteReporter.picklePort"));
    }

    // https://graphite.readthedocs.io/en/latest/feeding-carbon.html
    public CompletableFuture<Boolean> report(Collection<Metric> metrics) {
        return CompletableFuture.supplyAsync(() -> {
            try (Socket socket = new Socket(host, port);
                 OutputStream outputStream = socket.getOutputStream()) {
                String payload = serializeMetricItems(metrics);
                int length = payload.length();
                byte[] header = ByteBuffer.allocate(4).putInt(length).array();
                outputStream.write(header);
                Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
                writer.write(payload);
                writer.flush();
                return true;
            } catch (Throwable e) {
                log.warn("Error writing to Graphite: {}", e.getMessage());
                return false;
            }
        });
    }

    private static String serializeMetricItems(Collection<Metric> metrics) {
        StringBuilder pickled = new StringBuilder(metrics.size() * 75);
        pickled.append(MARK).append(LIST);

        for (Metric tuple : metrics) {
            // begin outer tuple
            pickled.append(MARK);

            // the metric name is a string.
            pickled.append(STRING)
                    // the single quotes are to match python's repr("abcd")
                    .append(QUOTE).append(tuple.getPath()).append(QUOTE).append(LF);

            // begin the inner tuple
            pickled.append(MARK);

            // timestamp is a long
            pickled.append(LONG).append(tuple.getTimeStampInSec())
                    // the trailing L is to match python's repr(long(1234))             
                    .append(LONG).append(LF);

            // and the value is a string.
            pickled.append(STRING).append(QUOTE).append(tuple.getValue()).append(QUOTE).append(LF);

            pickled.append(TUPLE) // end inner tuple
                    .append(TUPLE); // end outer tuple

            pickled.append(APPEND);
        }

        // every pickle ends with STOP
        pickled.append(STOP);
        return pickled.toString();
    }
}
