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

package org.geysermc.geyser.util;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VersionCheckUtils {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static @NonNull OptionalInt LATEST_BEDROCK_RELEASE = OptionalInt.empty();
    private static final int SUPPORTED_JAVA_VERSION = 17;

    public static void checkForOutdatedFloodgate(GeyserLogger logger) {
        try {
            // This class was removed in Floodgate 2.1.0-SNAPSHOT - if it still exists, Floodgate will not work
            // with this version of Geyser
            Class.forName("org.geysermc.floodgate.util.TimeSyncerHolder");
            logger.warning(GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.outdated", Constants.FLOODGATE_DOWNLOAD_LOCATION));
        } catch (ClassNotFoundException ignored) {
            // Nothing to worry about; we want this exception
        }
    }

    public static void checkForOutdatedJava(GeyserLogger logger) {
        // Taken from Paper
        String javaVersion = System.getProperty("java.version");
        Matcher matcher = Pattern.compile("(?:1\\.)?(\\d+)").matcher(javaVersion);
        if (!matcher.find()) {
            logger.debug("Could not parse Java version string " + javaVersion);
            return;
        }

        String version = matcher.group(1);
        int majorVersion;
        try {
            majorVersion = Integer.parseInt(version);
        } catch (NumberFormatException e) {
            logger.debug("Could not format as an int: " + version);
            return;
        }

        if (majorVersion < SUPPORTED_JAVA_VERSION) {
            logger.warning("*********************************************");
            logger.warning("");
            logger.warning(GeyserLocale.getLocaleStringLog("geyser.bootstrap.unsupported_java.header"));
            logger.warning(GeyserLocale.getLocaleStringLog("geyser.bootstrap.unsupported_java.message", SUPPORTED_JAVA_VERSION, javaVersion));
            logger.warning("");
            logger.warning("*********************************************");
        }
    }

    public static void checkForGeyserUpdate(Supplier<GeyserCommandSource> recipient) {
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject json = WebUtils.getJson("https://api.geysermc.org/v2/versions/geyser");
                JsonObject bedrock = json.getAsJsonObject("bedrock").getAsJsonObject("protocol");
                int protocolVersion = bedrock.get("id").getAsInt();
                if (GameProtocol.getBedrockCodec(protocolVersion) != null) {
                    LATEST_BEDROCK_RELEASE = OptionalInt.empty();
                    // We support the latest version! No need to print a message.
                    return;
                }

                LATEST_BEDROCK_RELEASE = OptionalInt.of(protocolVersion);
                final String newBedrockVersion = bedrock.get("name").getAsString();

                // Delayed for two reasons: save unnecessary processing, and wait to load locale if this is on join.
                GeyserCommandSource sender = recipient.get();

                // Overarching component is green - geyser.version.new component cannot be green or else the link blue is overshadowed
                Component message = Component.text().color(NamedTextColor.GREEN)
                        .append(Component.text(GeyserLocale.getPlayerLocaleString("geyser.version.new", sender.locale(), newBedrockVersion))
                                .replaceText(TextReplacementConfig.builder()
                                        .match("\\{1\\}") // Replace "Download here: {1}" so we can use fancy text component yesyes
                                        .replacement(Component.text()
                                                .content(Constants.GEYSER_DOWNLOAD_LOCATION)
                                                .color(NamedTextColor.BLUE)
                                                .decoration(TextDecoration.UNDERLINED, TextDecoration.State.TRUE)
                                                .clickEvent(ClickEvent.openUrl(Constants.GEYSER_DOWNLOAD_LOCATION)))
                                        .build()))
                        .build();
                sender.sendMessage(message);
            } catch (Exception e) {
                GeyserImpl.getInstance().getLogger().error("Error whilst checking for Geyser update!", e);
            }
        });
    }

    public static @NonNull OptionalInt getLatestBedrockRelease() {
        return LATEST_BEDROCK_RELEASE;
    }

    private VersionCheckUtils() {
    }
}
