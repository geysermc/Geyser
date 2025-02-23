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

package org.geysermc.geyser.platform.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.Locale;
import java.util.UUID;

public class VelocityCommandSource implements GeyserCommandSource {

    private final CommandSource handle;

    public VelocityCommandSource(CommandSource handle) {
        this.handle = handle;
        // Ensure even Java players' languages are loaded
        GeyserLocale.loadGeyserLocale(this.locale());
    }

    @Override
    public String name() {
        if (handle instanceof Player) {
            return ((Player) handle).getUsername();
        } else if (handle instanceof ConsoleCommandSource) {
            return "CONSOLE";
        }
        return "";
    }

    @Override
    public void sendMessage(@NonNull String message) {
        handle.sendMessage(LegacyComponentSerializer.legacySection().deserialize(message));
    }

    @Override
    public void sendMessage(Component message) {
        // Be careful that we don't shade in Adventure!!
        handle.sendMessage(message);
    }

    @Override
    public boolean isConsole() {
        return handle instanceof ConsoleCommandSource;
    }

    @Override
    public @Nullable UUID playerUuid() {
        if (handle instanceof Player player) {
            return player.getUniqueId();
        }
        return null;
    }

    @Override
    public String locale() {
        if (handle instanceof Player) {
            Locale locale = ((Player) handle).getPlayerSettings().getLocale();
            return GeyserLocale.formatLocale(locale.getLanguage() + "_" + locale.getCountry());
        }
        return GeyserLocale.getDefaultLocale();
    }

    @Override
    public boolean hasPermission(String permission) {
        // Handle blank permissions ourselves, as velocity only handles empty ones
        return permission.isBlank() || handle.hasPermission(permission);
    }

    @Override
    public Object handle() {
        return handle;
    }
}
