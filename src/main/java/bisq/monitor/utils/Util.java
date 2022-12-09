package bisq.monitor.utils;/*
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

import bisq.core.monitor.LongValueItem;
import bisq.core.monitor.ReportingItem;
import bisq.core.monitor.StringValueItem;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Util {
    //todo use from Utilities once merged.
    public static ExecutorService newCachedThreadPool(int maximumPoolSize) {
        return new ThreadPoolExecutor(0, maximumPoolSize,
                60, TimeUnit.SECONDS,
                new SynchronousQueue<>());
    }

    public static Set<String> getSeedNodeAddresses(String baseCurrencyNetwork) {
        Set<String> addresses = new HashSet<>();
        String fileName = baseCurrencyNetwork.toLowerCase();
        DefaultSeedNodeRepository.readSeedNodePropertyFile(fileName)
                .ifPresent(bufferedReader -> {
                    bufferedReader.lines().forEach(line -> {
                        if (!line.startsWith("#")) {
                            String[] strings = line.split(" \\(@");
                            if (strings.length > 0) {
                                addresses.add(strings[0]);
                            }
                        }
                    });
                });
        return addresses;
    }

    public static String cleanAddress(String address) {
        return address.replace(".onion:8000", "");
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

    public static Optional<Long> findLongValue(List<ReportingItem> reportingItems, String path) {
        return find(reportingItems, path)
                .filter(e -> e instanceof LongValueItem)
                .map(e -> (LongValueItem) e)
                .map(LongValueItem::getValue);
    }
}