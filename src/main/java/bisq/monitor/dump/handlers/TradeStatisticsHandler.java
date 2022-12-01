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

package bisq.monitor.dump.handlers;

import bisq.core.locale.CurrencyUtil;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatistics3StorageService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.util.FormattingUtils;
import bisq.core.util.VolumeUtil;
import bisq.monitor.dump.ReporterProvider;
import bisq.monitor.reporter.Metric;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.storage.P2PDataStorage;
import javafx.collections.SetChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.params.MainNetParams;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Singleton
@Slf4j
public class TradeStatisticsHandler {
    private final static String PREFIX = "TradeMetrics";

    private final Set<P2PDataStorage.ByteArray> alreadyProcessed = new HashSet<>();
    private final Reporter reporter;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final TradeStatistics3StorageService tradeStatistics3StorageService;

    @Inject
    public TradeStatisticsHandler(ReporterProvider reporterProvider,
                                  TradeStatisticsManager tradeStatisticsManager,
                                  TradeStatistics3StorageService tradeStatistics3StorageService) {
        this.reporter = reporterProvider.getReporter();
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.tradeStatistics3StorageService = tradeStatistics3StorageService;
    }

    public void onAllServicesInitialized() {
        long minDate = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10000);
        tradeStatistics3StorageService.getMapOfAllData().values().stream()
                .filter(e -> e instanceof TradeStatistics3)
                .map(e -> (TradeStatistics3) e)
                .filter(TradeStatistics3::isValid)
                .filter(tradeStatistics -> tradeStatistics.getDateAsLong() > minDate)
                .map(this::toMetrics)
                .forEach(this::sendReports);
        tradeStatisticsManager.getObservableTradeStatisticsSet().addListener((SetChangeListener<TradeStatistics3>) change -> {
            TradeStatistics3 newItem = change.getElementAdded();
            if (isNotProcessed(newItem)) {
                Set<Metric> reportItems = toMetrics(newItem);
                sendReports(reportItems);
            }
        });
    }

    private Set<Metric> toMetrics(TradeStatistics3 tradeStatistics) {
        alreadyProcessed.add(new P2PDataStorage.ByteArray(tradeStatistics.getHash()));
        Set<Metric> metrics = new HashSet<>();

        long timeStampInSec = tradeStatistics.getDateAsLong() / 1000;
        String market = CurrencyUtil.getCurrencyPair(tradeStatistics.getCurrency()).replace("/", "_");

        String path = PREFIX + "." + "price" + "." + market;
        String value = FormattingUtils.formatPrice(tradeStatistics.getTradePrice());
        metrics.add(new Metric(path, value, timeStampInSec));

        path = PREFIX + "." + "amount" + "." + market;
        value = new MainNetParams().getMonetaryFormat().noCode().format(tradeStatistics.getTradeAmount()).toString();
        metrics.add(new Metric(path, value, timeStampInSec));

        path = PREFIX + "." + "volume" + "." + market;
        value = VolumeUtil.formatVolume(tradeStatistics.getTradeVolume());
        metrics.add(new Metric(path, value, timeStampInSec));

        return metrics;
    }

    private void sendReports(Set<Metric> reportItems) {
        log.error(reportItems.toString());
        reportItems.forEach(reporter::report);
    }

    private boolean isNotProcessed(TradeStatistics3 tradeStatistics) {
        return !alreadyProcessed.contains(new P2PDataStorage.ByteArray(tradeStatistics.getHash()));
    }

}