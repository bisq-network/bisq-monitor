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

package bisq.monitor.clearnet.metric;

import bisq.monitor.Metric;
import bisq.monitor.reporter.MetricItem;
import bisq.monitor.reporter.Reporter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Uses the markets API to retrieve market volume data.
 *
 * @author Florian Reimair
 */
@Slf4j
public class MarketStats extends Metric {
    private String baseUrl;
    // poor mans JSON parser
    private final Pattern marketPattern = Pattern.compile("\"market\" ?: ?\"([a-z_]+)\"");
    private final Pattern amountPattern = Pattern.compile("\"amount\" ?: ?\"([\\d\\.]+)\"");
    private final Pattern volumePattern = Pattern.compile("\"volume\" ?: ?\"([\\d\\.]+)\"");
    private final Pattern timestampPattern = Pattern.compile("\"trade_date\" ?: ?([\\d]+)");

    private Long lastRun = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10));

    public MarketStats(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        baseUrl = configuration.getProperty("url");
    }

    @Override
    protected void execute() {
        try {
            // assemble query
            long ts = System.currentTimeMillis();
            long now = TimeUnit.MILLISECONDS.toSeconds(ts);
            String query = "/api/trades?format=json&market=all&timestamp_from=" + lastRun + "&timestamp_to=" + now;
            lastRun = now; // thought about adding 1 second but what if a trade is done exactly in this one second?

            log.info("Request market data from: {}", baseUrl + query);
            // connect
            URLConnection connection = new URL(baseUrl + query).openConnection();

            // prepare to receive data
            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String line, all = "";
            while ((line = in.readLine()) != null)
                all += ' ' + line;
            in.close();

            long requestDuration = System.currentTimeMillis() - ts;
            log.info("Received market data after {} ms.\n{}", requestDuration, all);

            Set<MetricItem> metricItems = new HashSet<>();
            Arrays.stream(all.substring(0, all.length() - 2).split("}")).forEach(trade -> {
                Matcher marketMatcher = marketPattern.matcher(trade);
                Matcher amountMatcher = amountPattern.matcher(trade);
                Matcher timestampMatcher = timestampPattern.matcher(trade);
                marketMatcher.find();
                if (marketMatcher.group(1).endsWith("btc")) {
                    amountMatcher = volumePattern.matcher(trade);
                }
                amountMatcher.find();
                timestampMatcher.find();
                String key = "volume." + marketMatcher.group(1);
                String value = amountMatcher.group(1);
                String timeStampInMs = timestampMatcher.group(1);
                long timeStampInSec = Long.parseLong(timeStampInMs) / 1000;
                metricItems.add(new MetricItem(getName(), key, value, timeStampInSec));
            });
            metricItems.add(new MetricItem(getName(), "requestDuration", requestDuration));
            reporter.report(metricItems);
        } catch (IllegalStateException ignore) {
            // no match found
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
