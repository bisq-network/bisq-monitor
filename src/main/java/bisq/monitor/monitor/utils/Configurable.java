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

package bisq.monitor.monitor.utils;

import java.util.Properties;

public abstract class Configurable {
    protected Properties configuration = new Properties();
    private String name = getClass().getSimpleName();

    public void configure(final Properties properties) {
        Properties myProperties = new Properties();
        properties.forEach((k, v) -> {
            String key = (String) k;
            if (key.startsWith(getName()))
                myProperties.put(key.substring(key.indexOf(".") + 1), v);
        });

        this.configuration = myProperties;
    }

    protected String getName() {
        return name;
    }
}
