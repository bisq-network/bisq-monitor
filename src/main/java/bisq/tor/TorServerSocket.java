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

package bisq.tor;

import bisq.common.util.FileUtils;
import bisq.common.util.NetworkUtils;
import lombok.extern.slf4j.Slf4j;
import net.freehaven.tor.control.TorControlConnection;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@Slf4j
public class TorServerSocket extends ServerSocket {
    private final String hsDirPath;
    private final TorController torController;
    private Optional<OnionAddress> onionAddress = Optional.empty();

    public TorServerSocket(String torDirPath, TorController torController) throws IOException {
        this.hsDirPath = torDirPath + File.separator + Constants.HS_DIR;
        this.torController = torController;
    }

    public CompletableFuture<OnionAddress> bindAsync(int hiddenServicePort, Executor executor) {
        return bindAsync(hiddenServicePort, "default", executor);
    }

    public CompletableFuture<OnionAddress> bindAsync(int hiddenServicePort, String id, Executor executor) {
        return bindAsync(hiddenServicePort, NetworkUtils.findFreeSystemPort(), id, executor);
    }

    public CompletableFuture<OnionAddress> bindAsync(int hiddenServicePort,
                                                     int localPort,
                                                     String id,
                                                     Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            Thread.currentThread().setName("TorServerSocket.bindAsync-" + id);
            try {
                return bind(hiddenServicePort, localPort, id);
            } catch (IOException | InterruptedException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    // Blocking
    public OnionAddress bind(int hiddenServicePort) throws IOException, InterruptedException {
        return bind(hiddenServicePort, "default");
    }

    public OnionAddress bind(int hiddenServicePort, String id) throws IOException, InterruptedException {
        return bind(hiddenServicePort, NetworkUtils.findFreeSystemPort(), id);
    }

    public OnionAddress bind(int hiddenServicePort, int localPort, String id) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        File dir = new File(hsDirPath, id);
        File hostNameFile = new File(dir.getCanonicalPath(), Constants.HOSTNAME);
        File privKeyFile = new File(dir.getCanonicalPath(), Constants.PRIV_KEY);
        FileUtils.makeDirs(dir);

        TorControlConnection.CreateHiddenServiceResult result;
        if (privKeyFile.exists()) {
            String privateKey = FileUtils.readFromFile(privKeyFile);
            result = torController.createHiddenService(hiddenServicePort, localPort, privateKey);
        } else {
            result = torController.createHiddenService(hiddenServicePort, localPort);
        }

        if (!hostNameFile.exists()) {
            FileUtils.makeFile(hostNameFile);
        }
        String serviceId = result.serviceID;

        OnionAddress onionAddress = new OnionAddress(serviceId + ".onion", hiddenServicePort);
        FileUtils.writeToFile(onionAddress.getHost(), hostNameFile);
        this.onionAddress = Optional.of(onionAddress);

        if (!privKeyFile.exists()) {
            FileUtils.makeFile(privKeyFile);
        }
        FileUtils.writeToFile(result.privateKey, privKeyFile);

        log.debug("Start publishing hidden service {}", onionAddress);
        CountDownLatch latch = new CountDownLatch(1);
        torController.addHiddenServiceReadyListener(serviceId, () -> {
            try {
                super.bind(new InetSocketAddress(Constants.LOCALHOST, localPort));
                log.info(">> TorServerSocket ready. Took {} ms", System.currentTimeMillis() - ts);
                latch.countDown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        latch.await();
        torController.removeHiddenServiceReadyListener(serviceId);
        return onionAddress;
    }

    @Override
    public void close() throws IOException {
        super.close();
        log.info("Close onionAddress={}", onionAddress);
        onionAddress.ifPresent(onionAddress -> {
            torController.removeHiddenServiceReadyListener(onionAddress.getServiceId());
            try {
                torController.destroyHiddenService(onionAddress.getServiceId());
            } catch (IOException ignore) {
            }
        });
    }

    public Optional<OnionAddress> getOnionAddress() {
        return onionAddress;
    }
}
