/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.monitor.monitor.tasks;

import bisq.core.provider.PriceFeedNodeAddressProvider;
import bisq.monitor.monitor.MonitorHttpClient;
import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.monitor.TorNode;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.utils.Util;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Request price and fee data from all price nodes.
 */
@Slf4j
public class PriceNodeData extends MonitorTask {
    private final Set<String> excluded = new HashSet<>();
    private final Set<String> addresses = new HashSet<>();
    private final ExecutorService executor;

    public PriceNodeData(Properties properties, Reporter reporter, TorNode torNode) {
        super(properties, reporter, torNode, true);

        String hosts = properties.getProperty("Monitor.PriceNodeData.hosts", "");
        if (hosts == null || hosts.isEmpty()) {
            addresses.addAll(PriceFeedNodeAddressProvider.DEFAULT_NODES.stream()
                    .map(address -> {
                        if (address.endsWith("/")) {
                            return address.substring(0, address.length() - 1);
                        } else {
                            return address;
                        }
                    })
                    .collect(Collectors.toSet()));
        } else {
            addresses.addAll(List.of(hosts.split(",")));
        }
        addresses.add("https://price.bisq.wiz.biz");

        excluded.add("NON_EXISTING_SYMBOL");
        excluded.addAll(Set.of(properties.getProperty("Monitor.PriceNodeData.excluded", "").split(",")));

        executor = Util.newCachedThreadPool(20);
    }

    @Override
    public void run() {
        try {
            torNode.maybeCreateTor();
            Socks5Proxy proxy = addresses.toString().contains(".onion") ?
                    torNode.getProxy() : null;
            addresses.forEach(address1 -> {
                if (!shutDownInProgress) {
                    log.info("Send request to '{}'", address1);
                    try {
                        CompletableFuture.runAsync(() -> {
                            String address = address1;
                            try {
                                if (address.contains(".onion")) {
                                    address = getAddressWithoutProtocol(address);
                                    MonitorHttpClient httpClient = MonitorHttpClient.config(address, 80, proxy, torNode.getSocketTimeout());
                                    String host = address.replace(".onion", "");
                                    reportFees(host, httpClient.getWithTor("/getFees/"));
                                    reportPrices(host, httpClient.getWithTor("/getAllMarketPrices/"));
                                } else {
                                    MonitorHttpClient httpClient = MonitorHttpClient.config(address, torNode.getSocketTimeout());
                                    address = getAddressWithoutProtocol(address);
                                    reportFees(address, httpClient.get("/getFees"));
                                    reportPrices(address, httpClient.get("/getAllMarketPrices"));
                                }
                            } catch (IOException e) {
                                if (!shutDownInProgress) {
                                    log.error("Could not connect to {}. {}", address, e.getMessage());
                                    reporter.report(new Metrics(getBasePath(address) + ".error", -1));
                                }
                            }
                        }, executor).join();
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable e) {
            if (!shutDownInProgress) {
                log.error("Error at PriceNodeData.run", e);
            }
        }
    }

    @Override
    public CompletableFuture<Void> shutDown() {
        shutDownInProgress = true;
        return CompletableFuture.runAsync(executor::shutdownNow, Executors.newSingleThreadExecutor());
    }


    private void reportFees(String address, String json) {
        String btcTxFee = new JsonParser().parse(json).getAsJsonObject()
                .get("dataMap").getAsJsonObject()
                .get("btcTxFee").getAsString();
        reporter.report(new Metrics(getBasePath(address) + "fee", btcTxFee));
    }

    private void reportPrices(String address, String json) {
        new JsonParser().parse(json).getAsJsonObject().get("data").getAsJsonArray()
                .forEach(item -> {
                    JsonObject priceItem = item.getAsJsonObject();
                    String currencyCode = priceItem.get("currencyCode").getAsString();
                    if (!excluded.contains(currencyCode)) {
                        String price = String.format("%.12f", priceItem.get("price").getAsDouble());
                        reporter.report(new Metrics(getBasePath(address) + "price." + currencyCode, price));
                    }
                });
    }

    private String getBasePath(String address) {
        address = address.replace(".", "_");
        return "priceNodes." + address + ".";
    }

    private String getAddressWithoutProtocol(String address) {
        return address.replace("http://", "").replace("https://", "");
    }
}
