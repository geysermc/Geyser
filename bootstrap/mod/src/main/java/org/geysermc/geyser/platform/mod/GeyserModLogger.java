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

package org.geysermc.geyser.platform.mod;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.text.ChatColor;

public class GeyserModLogger implements GeyserLogger {
    private final Logger logger = LogManager.getLogger("geyser");

    private boolean debug;

    @Override
    public void severe(String message) {
        logger.fatal(message);
    }

    @Override
    public void severe(String message, Throwable error) {
        logger.fatal(message, error);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }

    @Override
    public void error(String message, Throwable error) {
        logger.error(message, error);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void sendMessage(Component message) {
        // As of Java Edition 1.19.2, Minecraft's console doesn't natively support legacy format
        String flattened = LegacyComponentSerializer.legacySection().serialize(message);
        // Add the reset at the end, or else format will persist... forever.
        // https://cdn.discordapp.com/attachments/573909525132738590/1033904509170225242/unknown.png
        String text = ChatColor.toANSI(flattened) + ChatColor.ANSI_RESET;
        info(text);
    }

    @Override
    public void debug(String message) {
        if (debug) {
            logger.info(message);
        }
    }

    @Override
    public void debug(String message, Object... parameters) {
        if (debug) {
            logger.info(message, parameters);
        }
    }

    @Override
    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }
}
