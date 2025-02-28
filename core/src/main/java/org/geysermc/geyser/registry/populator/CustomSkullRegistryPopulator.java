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

package org.geysermc.geyser.registry.populator;

import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomSkullsEvent;
import org.geysermc.geyser.configuration.GeyserCustomSkullConfiguration;
import org.geysermc.geyser.pack.SkullResourcePackManager;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.CustomSkull;
import org.geysermc.geyser.skin.SkinManager;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

public class CustomSkullRegistryPopulator {

    public static void populate() {
        SkullResourcePackManager.SKULL_SKINS.clear(); // Remove skins after reloading
        BlockRegistries.CUSTOM_SKULLS.set(Object2ObjectMaps.emptyMap());

        if (!GeyserImpl.getInstance().config().addNonBedrockItems()) {
            return;
        }

        GeyserCustomSkullConfiguration skullConfig;
        try {
            GeyserBootstrap bootstrap = GeyserImpl.getInstance().getBootstrap();
            Path skullConfigPath = bootstrap.getConfigFolder().resolve("custom-skulls.yml");
            File skullConfigFile = FileUtils.fileOrCopiedFromResource(skullConfigPath.toFile(), "custom-skulls.yml", Function.identity(), bootstrap);
            skullConfig = FileUtils.loadConfig(skullConfigFile, GeyserCustomSkullConfiguration.class);
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.config.failed"), e);
            return;
        }

        BlockRegistries.CUSTOM_SKULLS.set(new Object2ObjectOpenHashMap<>());

        List<String> profiles = new ArrayList<>(skullConfig.getPlayerProfiles());
        List<String> usernames = new ArrayList<>(skullConfig.getPlayerUsernames());
        List<String> uuids = new ArrayList<>(skullConfig.getPlayerUUIDs());
        List<String> skinHashes = new ArrayList<>(skullConfig.getPlayerSkinHashes());

        GeyserImpl.getInstance().getEventBus().fire(new GeyserDefineCustomSkullsEvent() {
            @Override
            public void register(@NonNull String texture, @NonNull SkullTextureType type) {
                switch (type) {
                    case USERNAME -> usernames.add(texture);
                    case UUID -> uuids.add(texture);
                    case PROFILE -> profiles.add(texture);
                    case SKIN_HASH -> skinHashes.add(texture);
                }
            }
        });

        usernames.forEach((username) -> {
            String profile = getProfileFromUsername(username);
            if (profile != null) {
                String skinHash = getSkinHash(profile);
                if (skinHash != null) {
                    skinHashes.add(skinHash);
                }
            }
        });

        uuids.forEach((uuid) -> {
            String profile = getProfileFromUuid(uuid);
            if (profile != null) {
                String skinHash = getSkinHash(profile);
                if (skinHash != null) {
                    skinHashes.add(skinHash);
                }
            }
        });

        profiles.forEach((profile) -> {
            String skinHash = getSkinHash(profile);
            if (skinHash != null) {
                skinHashes.add(skinHash);
            }
        });

        skinHashes.forEach((skinHash) -> {
            if (!skinHash.matches("^[a-fA-F0-9]+$")) {
                GeyserImpl.getInstance().getLogger().error("Skin hash " + skinHash + " does not match required format ^[a-fA-F0-9]{64}$ and will not be added as a custom block.");
                return;
            }

            try {
                SkullResourcePackManager.cacheSkullSkin(skinHash);
                BlockRegistries.CUSTOM_SKULLS.register(skinHash, new CustomSkull(skinHash));
            } catch (IOException e) {
                GeyserImpl.getInstance().getLogger().error("Failed to cache skin for skull texture " + skinHash + " This skull will not be added as a custom block.", e);
            }
        });

        if (!BlockRegistries.CUSTOM_SKULLS.get().isEmpty()) {
            GeyserImpl.getInstance().getLogger().info("Registered " + BlockRegistries.CUSTOM_SKULLS.get().size() + " custom skulls as custom blocks.");
        }
    }

    /**
     * Gets the skin hash from a base64 encoded profile
     * @param profile the base64 encoded profile
     * @return the skin hash or null if the profile is invalid
     */
    private static @Nullable String getSkinHash(String profile) {
        try {
            SkinManager.GameProfileData profileData = SkinManager.GameProfileData.loadFromJson(profile);
            if (profileData == null) {
                GeyserImpl.getInstance().getLogger().warning("Skull texture " + profile + " contained no skins and will not be added as a custom block.");
                return null;
            }
            String skinUrl = profileData.skinUrl();
            return skinUrl.substring(skinUrl.lastIndexOf("/") + 1);
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().error("Skull texture " + profile + " is invalid and will not be added as a custom block.", e);
            return null;
        }
    }

    /**
     * Gets the base64 encoded profile from a player's username
     * @param username the player username
     * @return the base64 encoded profile or null if the request failed
     */
    private static @Nullable String getProfileFromUsername(String username) {
        try {
            return SkinProvider.requestTexturesFromUsername(username).get();
        } catch (InterruptedException | ExecutionException e) {
            GeyserImpl.getInstance().getLogger().error("Unable to request skull textures for " + username + " This skull will not be added as a custom block.", e);
            return null;
        }
    }

    /**
     * Gets the base64 encoded profile from a player's UUID
     * @param uuid the player UUID
     * @return the base64 encoded profile or null if the request failed
     */
    private static @Nullable String getProfileFromUuid(String uuid) {
        try {
            String uuidDigits = uuid.replace("-", "");
            if (uuidDigits.length() != 32) {
                GeyserImpl.getInstance().getLogger().error("Invalid skull uuid " + uuid + " This skull will not be added as a custom block.");
                return null;
            }
            return SkinProvider.requestTexturesFromUUID(uuid).get();
        } catch (InterruptedException | ExecutionException e) {
            GeyserImpl.getInstance().getLogger().error("Unable to request skull textures for " + uuid + " This skull will not be added as a custom block.", e);
            return null;
        }
    }
}
