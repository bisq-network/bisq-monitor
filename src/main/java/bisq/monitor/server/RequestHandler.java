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

import bisq.core.monitor.ReportingItems;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.handlers.*;
import bisq.monitor.utils.Util;
import lombok.extern.slf4j.Slf4j;
import spark.Request;
import spark.Response;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class RequestHandler {
    private final Set<ReportingHandler> reportingHandlers = new HashSet<>();
    private final ExecutorService executor;

    public RequestHandler(Reporter reporter) {
        reportingHandlers.add(new DaoStateHandler(reporter));
        reportingHandlers.add(new NetworkDataHandler(reporter));
        reportingHandlers.add(new NodeLoadHandler(reporter));
        reportingHandlers.add(new NetworkLoadHandler(reporter));

        executor = Util.newCachedThreadPool(20);
    }

    public String onRequest(Request request, Response response) {
        byte[] protoMessageAsBytes = request.bodyAsBytes();
        try {
            checkArgument(protoMessageAsBytes != null && protoMessageAsBytes.length > 0);
            ReportingItems reportingItems = ReportingItems.fromProtoMessageAsBytes(protoMessageAsBytes);
            log.info("Received from {} reportingItems {}", request.userAgent(), reportingItems);
            try {
                reportingHandlers.forEach(handler -> CompletableFuture.runAsync(() -> {
                    try {
                        handler.report(reportingItems);
                    } catch (Throwable t) {
                        log.error("Error at report call on {}. Error message: {}", handler.getClass().getSimpleName(), t.getMessage());
                    }
                }, executor));
            } catch (Throwable t) {
                log.error("Error at onRequest", t);
            }
            response.status(200);
        } catch (Throwable t) {
            log.error("Error at onRequest", t);
            response.status(500);
        }

        return "";
    }

    public CompletableFuture<Void> shutDown() {
        return CompletableFuture.runAsync(executor::shutdownNow, Executors.newSingleThreadExecutor());
    }
}