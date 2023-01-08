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

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.core.offer.OfferBookService;
import bisq.core.provider.price.PriceFeedService;
import bisq.monitor.dump.ReporterProvider;
import bisq.monitor.reporter.Metrics;
import bisq.monitor.reporter.Reporter;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.P2PService;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Coin;
import org.bitcoinj.params.MainNetParams;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@Slf4j
public class OffersHandler {
    private static final String OFFERS_PATH = "offers";

    private enum Age {
        YEAR(TimeUnit.DAYS.toMillis(365)),
        MONTH(TimeUnit.DAYS.toMillis(30)),
        WEEK(TimeUnit.DAYS.toMillis(7)),
        DAY(TimeUnit.DAYS.toMillis(1)),
        RECENT(0);

        @Getter
        private final long age;

        Age(long age) {
            this.age = age;
        }
    }

    @Setter
    private static Optional<Properties> monitorProperties = Optional.empty();

    private final Reporter reporter;
    private final OfferBookService offerBookService;
    private final BootstrapListener bootstrapListener;
    private final long intervalInSec;
    private boolean allServicesInitialized;
    private boolean priceProvided;
    private boolean initialized;
    private Timer timer;

    @Inject
    public OffersHandler(ReporterProvider reporterProvider,
                         OfferBookService offerBookService,
                         PriceFeedService priceFeedService,
                         P2PService p2pService) {
        this.reporter = reporterProvider.getReporter();
        this.offerBookService = offerBookService;

        intervalInSec = monitorProperties.map(properties -> Long.parseLong(properties.getProperty("DataDump.OffersHandler.intervalInSec", "60")))
                .orElse(60L);

        priceFeedService.setCurrencyCode("USD");
        bootstrapListener = new BootstrapListener() {
            @Override
            public void onUpdatedDataReceived() {
                // we need to have tor ready
                log.info("onBootstrapComplete: we start requestPriceFeed");
                priceFeedService.requestPriceFeed(price -> {
                            if (!initialized) {
                                priceProvided = true;
                                init();
                                p2pService.removeP2PServiceListener(bootstrapListener);
                            }
                        },
                        (errorMessage, throwable) -> log.warn("Exception at requestPriceFeed: " + throwable.getMessage()));
            }
        };
        p2pService.addP2PServiceListener(bootstrapListener);
    }

    public void onAllServicesInitialized() {
        allServicesInitialized = true;
        init();
    }

    private void init() {
        if (allServicesInitialized && priceProvided && !initialized) {
            initialized = true;
            timer = UserThread.runPeriodically(this::run, intervalInSec);
            run();
        }
    }

    private void run() {
        AtomicLong totalAmount = new AtomicLong();
        long now = System.currentTimeMillis();
        Map<String, AtomicInteger> numOffersByVersion = new HashMap<>();
        Map<Age, AtomicInteger> numOffersByAge = new HashMap<>();
        numOffersByAge.put(Age.YEAR, new AtomicInteger());
        numOffersByAge.put(Age.MONTH, new AtomicInteger());
        numOffersByAge.put(Age.WEEK, new AtomicInteger());
        numOffersByAge.put(Age.DAY, new AtomicInteger());
        numOffersByAge.put(Age.RECENT, new AtomicInteger());

        offerBookService.getOffers().forEach(offer -> {
            totalAmount.addAndGet(offer.getAmount().getValue());
            numOffersByVersion.putIfAbsent(offer.getVersionNr(), new AtomicInteger(0));
            numOffersByVersion.get(offer.getVersionNr()).incrementAndGet();
            numOffersByAge.get(getAgeCategory(now, offer.getDate().getTime())).incrementAndGet();
        });

        numOffersByAge.forEach((key, value) -> reporter.report(new Metrics(OFFERS_PATH + ".offerAge." + key.name().toLowerCase(), value.get())));

        String value = new MainNetParams().getMonetaryFormat().noCode().format(Coin.valueOf(totalAmount.get())).toString();
        reporter.report(new Metrics(OFFERS_PATH + ".totalAmount", value));
        numOffersByVersion.forEach((version, count) ->
                reporter.report(new Metrics(OFFERS_PATH + ".numOffersByVersion." + version.replace(".", "_"), count.get())));
    }

    private Age getAgeCategory(long now, long offerCreationDate) {
        long age = now - offerCreationDate;
        if (age > Age.YEAR.getAge()) {
            return Age.YEAR;
        } else if (age > Age.MONTH.getAge()) {
            return Age.MONTH;
        } else if (age > Age.WEEK.getAge()) {
            return Age.WEEK;
        } else if (age > Age.DAY.getAge()) {
            return Age.DAY;
        } else {
            return Age.RECENT;
        }
    }

    public void shutDown() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}