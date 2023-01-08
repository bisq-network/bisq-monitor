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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class TorUtils {

    public static void extractBinary(String torDirectory, OsType osType) throws IOException {
        InputStream archiveInputStream = FileUtils.getResourceAsStream(osType.getArchiveName());
        try (XZCompressorInputStream compressorInputStream = new XZCompressorInputStream(archiveInputStream);
             TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(compressorInputStream)) {
            ArchiveEntry entry;
            while ((entry = tarArchiveInputStream.getNextEntry()) != null) {
                File file = new File(torDirectory + File.separator +
                        entry.getName().replace('/', File.separatorChar));

                if (entry.isDirectory()) {
                    if (!file.exists() && !file.mkdirs()) {
                        throw new IOException("Could not create directory. File= " + file);
                    }
                    continue;
                    // return;
                }

                if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                    throw new IOException("Could not create parent directory. File= " + file.getParentFile());
                }

                if (file.exists() && !file.delete()) {
                    throw new IOException("Could not delete file in preparation for overwriting it. File= " + file.getAbsolutePath());
                }

                if (!file.createNewFile()) {
                    throw new IOException("Could not create file. File= " + file);
                }

                try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
                    tarArchiveInputStream.transferTo(fileOutputStream);
                } catch (IOException ex) {
                    throw new IOException("Cannot transfer bytes to file " + file.getAbsolutePath(), ex);
                }
                // Avoid "error=26, Text file busy" system error.

                if (osType == OsType.OSX) {
                    if (!file.setExecutable(true, true)) {
                        throw new IOException("Cannot set permission at file " + file.getAbsolutePath());
                    }
                } else if (entry instanceof TarArchiveEntry) {
                    int mode = ((TarArchiveEntry) entry).getMode();
                    log.debug("mode={} for file {}", mode, file);
                    if (mode + 65 > 0) {
                        boolean ownerOnly = mode + 1 == 0;
                        log.debug("ownerOnly={} for file {}", ownerOnly, file);
                        if (!file.setExecutable(true, ownerOnly)) {
                            throw new IOException("Cannot set permission at file " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }
}
