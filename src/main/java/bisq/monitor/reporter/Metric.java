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

package bisq.monitor.reporter;

import lombok.Value;

@Value
public class Metric {
    public static final String ROOT = "bisq_v2";
    String path;
    String value;
    long timeStampInSec;


    public Metric(String path, String value, long timeStampInSec) {
        this.path = ROOT + "." + path;
        this.value = value;
        this.timeStampInSec = timeStampInSec;
    }

    public Metric(String path, String value) {
        this(path, value, System.currentTimeMillis() / 1000);
    }

    public Metric(String prefix, String key, String value) {
        this(prefix.isEmpty() ? key : prefix + "." + key, value);
    }

    public Metric(String prefix, String key, String value, long timeStampInSec) {
        this(prefix.isEmpty() ? key : prefix + "." + key, value, timeStampInSec);
    }

    public Metric(String path, int value, long timeStampInSec) {
        this(path, String.valueOf(value), timeStampInSec);
    }

    public Metric(String path, int value) {
        this(path, String.valueOf(value));
    }

    public Metric(String path, long value, long timeStampInSec) {
        this(path, String.valueOf(value), timeStampInSec);
    }

    public Metric(String path, double value) {
        this(path, String.valueOf(value));
    }

    public Metric(String path, double value, long timeStampInSec) {
        this(path, String.valueOf(value), timeStampInSec);
    }

    @Override
    public String toString() {
        return "MetricItem{" +
                "\r\n     path='" + path + '\'' +
                ",\r\n     value='" + value + '\'' +
                ",\r\n     timeStampInSec=" + timeStampInSec +
                "\r\n}";
    }
}