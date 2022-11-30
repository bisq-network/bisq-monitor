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

import bisq.common.util.Hex;
import bisq.core.monitor.ReportingItems;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.handlers.*;
import lombok.extern.slf4j.Slf4j;
import spark.Request;
import spark.Response;

import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class RequestHandler {
    private final Set<ReportingHandler> reportingHandlers = new HashSet<>();

    public RequestHandler(Properties properties, Reporter reporter) {
        Map<String, String> seedNodeOperatorByAddress = Util.getOperatorByNodeAddress(properties.getProperty("baseCurrencyNetwork"));

        reportingHandlers.add(new DaoStateHandler(reporter, seedNodeOperatorByAddress));
        reportingHandlers.add(new NetworkDataHandler(reporter, seedNodeOperatorByAddress));
        reportingHandlers.add(new NodeLoadHandler(reporter, seedNodeOperatorByAddress));
        reportingHandlers.add(new NetworkLoadHandler(reporter, seedNodeOperatorByAddress));
    }

    public void shutdown() {
    }

    public String onRequest(Request request, Response response) {
        String hex = request.body();
        checkArgument(hex != null && !hex.trim().isEmpty());
        try {
            ReportingItems reportingItems = ReportingItems.fromProtoMessageAsBytes(Hex.decode(hex));
            reportingHandlers.forEach(handler -> CompletableFuture.runAsync(() -> handler.report(reportingItems)));
            response.status(200);
        } catch (Throwable t) {
            log.error("Error at onRequest", t);
            response.status(500);
        }
        return "";
    }
}