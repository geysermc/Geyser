/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.command;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.incendo.cloud.SenderMapper;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Converts {@link GeyserCommandSource}s to the server's command sender type (and back) in a lenient manner.
 *
 * @param senderType class of the server command sender type
 * @param playerLookup function for looking up a player command sender by UUID
 * @param consoleProvider supplier of the console command sender
 * @param commandSourceLookup supplier of the platform implementation of the {@link GeyserCommandSource}
 * @param <S> server command sender type
 */
public record CommandSourceConverter<S>(Class<S> senderType,
                                           Function<UUID, S> playerLookup,
                                           Supplier<S> consoleProvider,
                                           Function<S, GeyserCommandSource> commandSourceLookup
) implements SenderMapper<S, GeyserCommandSource> {

    /**
     * Creates a new CommandSourceConverter for a server platform
     * in which the player type is not a command sender type, and must be mapped.
     *
     * @param senderType class of the command sender type
     * @param playerLookup function for looking up a player by UUID
     * @param senderLookup function for converting a player to a command sender
     * @param consoleProvider supplier of the console command sender
     * @param commandSourceLookup supplier of the platform implementation of {@link GeyserCommandSource}
     * @return a new CommandSourceConverter
     * @param <P> server player type
     * @param <S> server command sender type
     */
    public static <P, S> CommandSourceConverter<S> layered(Class<S> senderType,
                                                           Function<UUID, P> playerLookup,
                                                           Function<P, S> senderLookup,
                                                           Supplier<S> consoleProvider,
                                                           Function<S, GeyserCommandSource> commandSourceLookup) {
        Function<UUID, S> lookup = uuid -> {
            P player = playerLookup.apply(uuid);
            if (player == null) {
                return null;
            }
            return senderLookup.apply(player);
        };
        return new CommandSourceConverter<>(senderType, lookup, consoleProvider, commandSourceLookup);
    }

    @Override
    public @NonNull GeyserCommandSource map(@NonNull S base) {
        return commandSourceLookup.apply(base);
    }

    @Override
    public @NonNull S reverse(GeyserCommandSource source) throws IllegalArgumentException {
        Object handle = source.handle();
        if (senderType.isInstance(handle)) {
            return senderType.cast(handle); // one of the server platform implementations
        }

        if (source.isConsole()) {
            return consoleProvider.get(); // one of the loggers
        }

        if (!(source instanceof GeyserSession)) {
            GeyserLogger logger = GeyserLogger.get();
            if (logger.isDebug()) {
                logger.debug("Falling back to UUID for command sender lookup for a command source that is not a GeyserSession: " + source);
                Thread.dumpStack();
            }
        }

        // Ideally lookup should only be necessary for GeyserSession
        UUID uuid = source.playerUuid();
        if (uuid != null) {
            return playerLookup.apply(uuid);
        }

        throw new IllegalArgumentException("failed to find sender for name=%s, uuid=%s".formatted(source.name(), source.playerUuid()));
    }
}
