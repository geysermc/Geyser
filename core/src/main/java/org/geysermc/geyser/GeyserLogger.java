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

import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.command.GeyserCommandSource;

import java.util.UUID;

public interface GeyserLogger extends GeyserCommandSource {

    /**
     * Logs a severe message to console
     *
     * @param message the message to log
     */
    void severe(String message);

    /**
     * Logs a severe message and an exception to console
     *
     * @param message the message to log
     * @param error the error to throw
     */
    void severe(String message, Throwable error);

    /**
     * Logs an error message to console
     *
     * @param message the message to log
     */
    void error(String message);

    /**
     * Logs an error message and an exception to console
     *
     * @param message the message to log
     * @param error the error to throw
     */
    void error(String message, Throwable error);

    /**
     * Logs a warning message to console
     *
     * @param message the message to log
     */
    void warning(String message);

    /**
     * Logs an info message to console
     *
     * @param message the message to log
     */
    void info(String message);

    /**
     * Logs an info component to console
     *
     * @param message the message to log
     */
    default void info(Component message) {
        sendMessage(message);
    }

    /**
     * Logs a debug message to console
     *
     * @param message the message to log
     */
    void debug(String message);

    /**
     * Logs an object to console if debug mode is enabled
     *
     * @param object the object to log
     */
    default void debug(@Nullable Object object) {
        if (isDebug()) {
            // Don't create String object by default
            info(String.valueOf(object));
        }
    }

    /**
     * Sets if the logger should print debug messages
     *
     * @param debug if the logger should print debug messages
     */
    void setDebug(boolean debug);

    /**
     * If debug is enabled for this logger
     */
    boolean isDebug();

    @Override
    default String name() {
        return "CONSOLE";
    }

    @Override
    default void sendMessage(@NonNull String message) {
        info(message);
    }

    @Override
    default boolean isConsole() {
        return true;
    }

    @Override
    default @Nullable UUID playerUuid() {
        return null;
    }

    @Override
    default boolean hasPermission(String permission) {
        return true;
    }
}
