package bisq.monitor.server;/*
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

import bisq.core.monitor.IntegerValueItem;
import bisq.core.monitor.ReportingItem;
import bisq.core.monitor.StringValueItem;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Util {
    public static Map<String, String> getOperatorByNodeAddress(String baseCurrencyNetwork) {
        Map<String, String> map = new HashMap<>();
        String fileName = baseCurrencyNetwork.toLowerCase();
        DefaultSeedNodeRepository.readSeedNodePropertyFile(fileName)
                .ifPresent(bufferedReader -> {
                    bufferedReader.lines().forEach(line -> {
                        if (!line.startsWith("#")) {
                            String[] strings = line.split(" \\(@");
                            String node = strings.length > 0 ? strings[0] : "n/a";
                            String operator = strings.length > 1 ? strings[1].replace(")", "") : "n/a";
                            map.put(node, operator);
                        }
                    });
                });
        return map;
    }

    public static String getNodeId(List<ReportingItem> reportingItems, Map<String, String> seedNodeOperatorByAddress) {
        return reportingItems.stream()
                .filter(e -> e.getPath().equals("node.address"))
                .filter(e -> e instanceof StringValueItem)
                .map(e -> ((StringValueItem) e).getValue())
                .map(address -> mapAddressToId(address, seedNodeOperatorByAddress))
                .findAny()
                .orElse("Undefined");
    }

    public static String mapAddressToId(String address, Map<String, String> seedNodeOperatorByAddress) {
        return seedNodeOperatorByAddress.get(address) + "@" + address.replace(".onion:8000", "");
    }

    public static Optional<ReportingItem> find(List<ReportingItem> reportingItems, String path) {
        return reportingItems.stream()
                .filter(e -> e.getPath().equals(path))
                .findAny();
    }

    public static Optional<String> findStringValue(List<ReportingItem> reportingItems, String path) {
        return find(reportingItems, path)
                .filter(e -> e instanceof StringValueItem)
                .map(e -> (StringValueItem) e)
                .map(StringValueItem::getValue);
    }

    public static Optional<Integer> findIntegerValue(List<ReportingItem> reportingItems, String path) {
        return find(reportingItems, path)
                .filter(e -> e instanceof IntegerValueItem)
                .map(e -> (IntegerValueItem) e)
                .map(IntegerValueItem::getValue);
    }
}