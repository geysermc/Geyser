/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser;

import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.text.GeyserLocale;

import java.nio.file.Files;
import java.nio.file.Path;

public class FloodgateKeyLoader {
    public static Path getKeyPath(GeyserConfig config, Path floodgateDataFolder, Path geyserDataFolder, GeyserLogger logger) {
        // Always prioritize Floodgate's key, if it is installed.
        // This mostly prevents people from trying to copy the key and corrupting it in the process
        if (floodgateDataFolder != null) {
            Path autoKey = floodgateDataFolder.resolve("key.pem");
            if (Files.exists(autoKey)) {
                logger.debug(GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.auto_loaded"));
                return autoKey;
            } else {
                logger.error(GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.missing_key"));
            }
        }

        Path floodgateKey = geyserDataFolder.resolve(config.advanced().floodgateKeyFile());

        if (!Files.exists(floodgateKey)) {
            logger.error(GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.not_installed"));
        }

        return floodgateKey;
    }
}
