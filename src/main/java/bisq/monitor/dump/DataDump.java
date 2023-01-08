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

package bisq.monitor.dump;

import bisq.core.app.misc.AppSetup;
import bisq.core.offer.OfferBookService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.monitor.dump.handlers.OffersHandler;
import bisq.monitor.dump.handlers.TradeStatisticsHandler;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import com.google.inject.Injector;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataDump {
    @Setter
    private Injector injector;
    private OfferBookService offerBookService; // pin to not get GC'ed
    private PriceFeedService priceFeedService;
    private TradeStatisticsManager tradeStatisticsManager;
    private P2PService p2pService;
    private AppSetup appSetup;

    public DataDump() {
    }

    public void startApplication() {
        p2pService = injector.getInstance(P2PService.class);
        offerBookService = injector.getInstance(OfferBookService.class);
        priceFeedService = injector.getInstance(PriceFeedService.class);
        tradeStatisticsManager = injector.getInstance(TradeStatisticsManager.class);

        // We need the price feed for market based offers
        priceFeedService.setCurrencyCode("USD");
        p2pService.addP2PServiceListener(new BootstrapListener() {
            @Override
            public void onUpdatedDataReceived() {
                // we need to have tor ready
                log.info("onBootstrapComplete: we start requestPriceFeed");
                priceFeedService.requestPriceFeed(price -> log.info("requestPriceFeed. price=" + price),
                        (errorMessage, throwable) -> log.warn("Exception at requestPriceFeed: " + throwable.getMessage()));
            }
        });

        appSetup = injector.getInstance(DataDumpAppSetup.class);
        appSetup.start();
    }

    public void shutDown() {
        injector.getInstance(TradeStatisticsHandler.class).shutDown();
        injector.getInstance(OffersHandler.class).shutDown();

    }
}
