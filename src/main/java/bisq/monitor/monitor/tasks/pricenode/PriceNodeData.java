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

package bisq.monitor.monitor.tasks.pricenode;

import bisq.core.provider.ProvidersRepository;
import bisq.monitor.monitor.MonitorTask;
import bisq.monitor.monitor.tor.OnionParser;
import bisq.monitor.monitor.utils.MonitorHttpClient;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.NodeAddress;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import lombok.extern.slf4j.Slf4j;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Request price and fee data from all price nodes.
 */
@Slf4j
public class PriceNodeData extends MonitorTask {
    private final Set<String> excluded = new HashSet<>();
    private final Set<String> priceNodes = new HashSet<>();

    public PriceNodeData(Properties properties, Reporter reporter) {
        super(properties, reporter);

        String hosts = configuration.getProperty("hosts", "");
        if (hosts == null || hosts.isEmpty()) {
            priceNodes.addAll(ProvidersRepository.DEFAULT_NODES.stream().map(e -> e.replace("/", "")).collect(Collectors.toSet()));
        } else {
            priceNodes.addAll(List.of(hosts.split(",")));
        }
        priceNodes.add("https://price.bisq.wiz.biz");

        excluded.add("NON_EXISTING_SYMBOL");
        excluded.addAll(Set.of(configuration.getProperty("excluded", "").split(",")));
    }

    @Override
    protected void execute() {
        try {
            Socks5Proxy proxy = priceNodes.toString().contains(".onion") ?
                    Objects.requireNonNull(Tor.getDefault()).getProxy() : null;
            for (String address : priceNodes) {
                try {
                    if (address.contains(".onion")) {
                        NodeAddress nodeAddress = OnionParser.getNodeAddress(address);
                        MonitorHttpClient httpClient = MonitorHttpClient.config(nodeAddress.getHostName(), nodeAddress.getPort(), proxy);
                        String host = nodeAddress.getHostNameWithoutPostFix();
                        reportFees(host, httpClient.getWithTor("/getFees/"));
                        reportPrices(host, httpClient.getWithTor("/getAllMarketPrices/"));
                    } else {
                        MonitorHttpClient httpClient = MonitorHttpClient.config(address);
                        NodeAddress nodeAddress = new NodeAddress(address);
                        String host = nodeAddress.getHostNameWithoutPostFix();
                        reportFees(host, httpClient.get("/getFees"));
                        reportPrices(host, httpClient.get("/getAllMarketPrices"));
                    }
                } catch (IOException e) {
                    log.error(e.toString());
                }
            }
        } catch (TorCtlException e) {
            e.printStackTrace();
        }
    }

    private void reportFees(String address, String json) {
        String btcTxFee = new JsonParser().parse(json).getAsJsonObject()
                .get("dataMap").getAsJsonObject()
                .get("btcTxFee").getAsString();
        reporter.report(new Metric(getName(), "fee." + address, btcTxFee));
    }

    private void reportPrices(String host, String json) {
        new JsonParser().parse(json).getAsJsonObject().get("data").getAsJsonArray()
                .forEach(item -> {
                    JsonObject priceItem = item.getAsJsonObject();
                    String currencyCode = priceItem.get("currencyCode").getAsString();
                    if (!excluded.contains(currencyCode)) {
                        String price = String.format("%.12f", priceItem.get("price").getAsDouble());
                        reporter.report(new Metric(getName() + ".price." + host, currencyCode, price));
                    }
                });
    }
}
