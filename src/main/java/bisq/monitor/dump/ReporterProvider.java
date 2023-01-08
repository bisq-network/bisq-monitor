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

import bisq.monitor.reporter.ConsoleReporter;
import bisq.monitor.reporter.GraphiteReporter;
import bisq.monitor.reporter.Reporter;
import lombok.Setter;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;
import java.util.Properties;

@Singleton
public class ReporterProvider {
    @Setter
    private static Optional<Properties> monitorProperties = Optional.empty();
    private Reporter reporter;

    @Inject
    public ReporterProvider() {
    }

    public Reporter getReporter() {
        if (reporter == null) {
            if (monitorProperties.isPresent()) {
                Properties properties = monitorProperties.get();
                reporter = "true".equals(properties.getProperty("GraphiteReporter.enabled", "false")) ?
                        new GraphiteReporter(properties) : new ConsoleReporter();
            } else {
                reporter = new ConsoleReporter();
            }
        }
        return reporter;
    }
}