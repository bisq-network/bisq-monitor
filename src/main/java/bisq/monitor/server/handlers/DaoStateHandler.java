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

package bisq.monitor.server.handlers;

import bisq.common.util.Tuple2;
import bisq.core.monitor.ReportingItems;
import bisq.monitor.reporter.MetricItem;
import bisq.monitor.reporter.Reporter;
import bisq.monitor.server.Util;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public class DaoStateHandler extends ReportingHandler {
    private final Map<Tuple2<Integer, Integer>, Map<String, Map<String, Map<String, MetricItem>>>> map = new ConcurrentHashMap<>();

    public DaoStateHandler(Reporter reporter, Map<String, String> seedNodeOperatorByAddress) {
        super(reporter, seedNodeOperatorByAddress);
    }

    @Override
    public void report(ReportingItems reportingItems) {
        super.report(reportingItems, "dao", Set.of("daoStateChainHeight", "blockTimeIsSec", "numBsqBlocks",
                "daoStateHash", "proposalHash", "blindVoteHash"));
        try {
            int height = Util.findIntegerValue(reportingItems, "dao.daoStateChainHeight").orElseThrow();
            int blockTimeIsSec = Util.findIntegerValue(reportingItems, "dao.blockTimeIsSec").orElseThrow();
            String nodeId = Util.getNodeId(reportingItems, seedNodeOperatorByAddress);
            pruneMap(map, height);

            String daoStateHash = Util.findStringValue(reportingItems, "dao.daoStateHash").orElseThrow();
            fillHashValue(map, nodeId, height, blockTimeIsSec, "daoStateHash", daoStateHash);

            String proposalHash = Util.findStringValue(reportingItems, "dao.proposalHash").orElseThrow();
            fillHashValue(map, nodeId, height, blockTimeIsSec, "proposalHash", proposalHash);

            String blindVoteHash = Util.findStringValue(reportingItems, "dao.blindVoteHash").orElseThrow();
            fillHashValue(map, nodeId, height, blockTimeIsSec, "blindVoteHash", blindVoteHash);

            Set<MetricItem> metricItems = getMetricItems(map);
            metricItems.add(new MetricItem("dao.height." + nodeId, height, blockTimeIsSec));
            metricItems.forEach(this::sendReport);
        } catch (Throwable ignore) {
        }
    }

    private static void fillHashValue(Map<Tuple2<Integer, Integer>, Map<String, Map<String, Map<String, MetricItem>>>> map,
                                      String nodeId,
                                      int height,
                                      int blockTimeIsSec,
                                      String hashType,
                                      String hashValue) {
        Tuple2<Integer, Integer> blockHeightTuple = new Tuple2<>(height, blockTimeIsSec);
        map.putIfAbsent(blockHeightTuple, new HashMap<>());
        Map<String, Map<String, Map<String, MetricItem>>> mapByHashType = map.get(blockHeightTuple);
        mapByHashType.putIfAbsent(hashType, new HashMap<>());
        Map<String, Map<String, MetricItem>> setByHashValue = mapByHashType.get(hashType);
        setByHashValue.putIfAbsent(hashValue, new HashMap<>());
        Map<String, MetricItem> metricItemByNodeId = setByHashValue.get(hashValue);
        metricItemByNodeId.putIfAbsent(nodeId, null);
    }

    private static Set<MetricItem> getMetricItems(Map<Tuple2<Integer, Integer>, Map<String, Map<String, Map<String, MetricItem>>>> map) {
        Set<MetricItem> metricItems = new HashSet<>();
        map.forEach((blockHeightTuple, mapByHashType) -> {
            int blockTimeIsSec = blockHeightTuple.second;
            mapByHashType.forEach((hashType, byHashValue) -> {
                Comparator<Map.Entry<String, Map<String, MetricItem>>> entryComparator = Comparator.comparing(e -> e.getValue().size());
                List<String> rankedHashBuckets = byHashValue.entrySet().stream()
                        .sorted(entryComparator.reversed())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
                List<Integer> rankedBucketSizes = byHashValue.entrySet().stream()
                        .sorted(entryComparator.reversed())
                        .map(e -> e.getValue().size())
                        .collect(Collectors.toList());
                byHashValue.forEach((hashValue, metricItemByNodeId) -> {
                    // index 0 is the majority bucket. All others are set to 1 or if not determined we set it to -1
                    int index = Math.min(1, rankedHashBuckets.indexOf(hashValue));
                    // In case we have same size buckets we consider it not determined
                    if (rankedBucketSizes.size() > 1) {
                        if (rankedBucketSizes.get(0).equals(rankedBucketSizes.get(1))) {
                            index = -1;
                        }
                    }

                    int finalIndex = index;
                    Map<String, MetricItem> updated = metricItemByNodeId.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey,
                                    entry -> new MetricItem("dao." + hashType + "." + entry.getKey(), finalIndex, blockTimeIsSec)));
                    metricItemByNodeId.putAll(updated);
                    metricItems.addAll(updated.values());
                });
            });
        });
        return metricItems;
    }

    private void pruneMap(Map<Tuple2<Integer, Integer>, Map<String, Map<String, Map<String, MetricItem>>>> map, int height) {
        int minHeight = height - 2;
        var pruned = map.entrySet().stream()
                .filter(e -> e.getKey().first > minHeight)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        map.clear();
        map.putAll(pruned);
    }
}