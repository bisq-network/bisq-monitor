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

/**
 * DataDump is a headless Bisq application with the minimal setup for receiving all relevant network data
 * like trade statistics or DAO data.
 * It sends reports via the provided Reporter for the data we want to have available in Grafana.
 * It has to run as standalone application.
 * <p>
 * Data we are interested in  are:
 * - Trade statistics
 * - DAO data
 * - Offer data
 */
package bisq.monitor.dump;