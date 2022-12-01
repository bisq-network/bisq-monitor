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

import java.util.Map;
import java.util.Set;

public abstract class Reporter {

    protected Reporter() {
    }

    abstract public void report(Metrics metrics);

    abstract public void report(Set<Metrics> metrics);

    public void shutDown() {
    }

    public void report(Map<String, String> map, String prefix) {
        map.entrySet().stream()
                .map(entry -> new Metrics(prefix, entry.getKey(), entry.getValue()))
                .forEach(this::report);
    }
}
