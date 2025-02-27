/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.skin;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.skin.SkinManager.GameProfileData;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.auth.GameProfile.Texture;
import org.geysermc.mcprotocollib.auth.GameProfile.TextureModel;
import org.geysermc.mcprotocollib.auth.GameProfile.TextureType;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for modifying a player's skin when wearing a player head
 */
public class FakeHeadProvider {
    private static final LoadingCache<FakeHeadEntry, SkinData> MERGED_SKINS_LOADING_CACHE = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .maximumSize(10000)
            .build(new CacheLoader<>() {
                @Override
                public SkinData load(@NonNull FakeHeadEntry fakeHeadEntry) throws Exception {
                    SkinData skinData = SkinProvider.getOrDefault(SkinProvider.requestSkinData(fakeHeadEntry.getEntity(), fakeHeadEntry.getSession()), null, 5);

                    if (skinData == null) {
                        throw new Exception("Couldn't load player's original skin");
                    }

                    Skin skin = skinData.skin();
                    Cape cape = skinData.cape();
                    SkinGeometry geometry = skinData.geometry().geometryName().equals("{\"geometry\" :{\"default\" :\"geometry.humanoid.customSlim\"}}")
                            ? SkinProvider.WEARING_CUSTOM_SKULL_SLIM : SkinProvider.WEARING_CUSTOM_SKULL;

                    Skin headSkin = SkinProvider.getOrDefault(
                            SkinProvider.requestSkin(fakeHeadEntry.getEntity().getUuid(), fakeHeadEntry.getFakeHeadSkinUrl(), false), SkinProvider.EMPTY_SKIN, 5);
                    BufferedImage originalSkinImage = SkinProvider.imageDataToBufferedImage(skin.skinData(), 64, skin.skinData().length / 4 / 64);
                    BufferedImage headSkinImage = SkinProvider.imageDataToBufferedImage(headSkin.skinData(), 64, headSkin.skinData().length / 4 / 64);

                    Graphics2D graphics2D = originalSkinImage.createGraphics();
                    graphics2D.setComposite(AlphaComposite.Clear);
                    graphics2D.fillRect(0, 0, 64, 16);
                    graphics2D.setComposite(AlphaComposite.SrcOver);
                    graphics2D.drawImage(headSkinImage, 0, 0, 64, 16, 0, 0, 64, 16, null);
                    graphics2D.dispose();

                    // Make the skin key a combination of the current skin data and the new skin data
                    // Don't tie it to a player - that player *can* change skins in-game
                    String skinKey = "customPlayerHead_" + fakeHeadEntry.getFakeHeadSkinUrl() + "_" + skin.textureUrl();
                    byte[] targetSkinData = SkinProvider.bufferedImageToImageData(originalSkinImage);
                    Skin mergedSkin = new Skin(skinKey, targetSkinData);

                    // Avoiding memory leak
                    fakeHeadEntry.setEntity(null);
                    fakeHeadEntry.setSession(null);

                    return new SkinData(mergedSkin, cape, geometry);
                }
            });

    public static void setHead(GeyserSession session, PlayerEntity entity, @Nullable GameProfile profile) {
        if (profile == null) {
            return;
        }

        GameProfile current = session.getPlayerWithCustomHeads().get(entity.getUuid());
        if (profile.equals(current)) {
            // We already did this, no need to re-compute
            return;
        }

        Map<TextureType, Texture> textures;
        try {
            textures = profile.getTextures(false);
        } catch (IllegalStateException e) {
            GeyserLogger.get().debug("Could not decode player head from profile %s, got: %s".formatted(profile, e.getMessage()));
            textures = null;
        }

        if (textures == null || textures.isEmpty()) {
            loadHeadFromProfile(session, entity, profile);
            return;
        }

        Texture skinTexture = textures.get(TextureType.SKIN);

        if (skinTexture == null) {
            return;
        }

        Texture capeTexture = textures.get(TextureType.CAPE);
        String capeUrl = capeTexture != null ? capeTexture.getURL() : null;

        boolean isAlex = skinTexture.getModel() == TextureModel.SLIM;

        loadHeadFromProfile(session, entity, new GameProfileData(skinTexture.getURL(), capeUrl, isAlex), profile);
    }

    public static void loadHeadFromProfile(GeyserSession session, PlayerEntity entity, GameProfile profile) {
        CompletableFuture<String> texturesFuture;
        if (profile.getId() != null) {
            texturesFuture = SkinProvider.requestTexturesFromUUID(profile.getId().toString());
        } else {
            texturesFuture = SkinProvider.requestTexturesFromUsername(profile.getName());
        }

        texturesFuture.whenCompleteAsync((encodedJson, throwable) -> {
            if (throwable != null) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.skin.fail", entity.getUuid()), throwable);
                return;
            }
            try {
                SkinManager.GameProfileData gameProfileData = SkinManager.GameProfileData.loadFromJson(encodedJson);
                if (gameProfileData == null) {
                    return;
                }
                loadHeadFromProfile(session, entity, gameProfileData, profile);
            } catch (IOException e) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.skin.fail", entity.getUuid(), e.getMessage()));
            }
        });
    }

    public static void loadHeadFromProfile(GeyserSession session, PlayerEntity entity, SkinManager.GameProfileData gameProfileData, GameProfile profile) {
        String fakeHeadSkinUrl = gameProfileData.skinUrl();

        session.getPlayerWithCustomHeads().put(entity.getUuid(), profile);
        String texturesProperty = entity.getTexturesProperty();
        SkinProvider.getExecutorService().execute(() -> {
            try {
                SkinData mergedSkinData = MERGED_SKINS_LOADING_CACHE.get(new FakeHeadEntry(texturesProperty, fakeHeadSkinUrl, entity, session));
                SkinManager.sendSkinPacket(session, entity, mergedSkinData);
            } catch (ExecutionException e) {
                GeyserLogger.get().error("Couldn't merge skin of " + entity.getUsername() + " with head skin url " + fakeHeadSkinUrl, e);
            }
        });
    }

    public static void restoreOriginalSkin(GeyserSession session, LivingEntity livingEntity) {
        if (!(livingEntity instanceof PlayerEntity entity)) {
            return;
        }

        if (session.getPlayerWithCustomHeads().remove(entity.getUuid()) == null) {
            return;
        }

        SkinProvider.requestSkinData(entity, session).whenCompleteAsync((skinData, throwable) -> {
            if (throwable != null) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.skin.fail", entity.getUuid()), throwable);
                return;
            }

            SkinManager.sendSkinPacket(session, entity, skinData);
        });
    }

    @AllArgsConstructor
    @Getter
    @Setter
    private static class FakeHeadEntry {
        private final String texturesProperty;
        private final String fakeHeadSkinUrl;
        private PlayerEntity entity;
        private GeyserSession session;

        @Override
        public boolean equals(Object o) {
            // We don't care about the equality of the entity as that is not used for caching purposes
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FakeHeadEntry that = (FakeHeadEntry) o;
            return Objects.equals(texturesProperty, that.texturesProperty) && Objects.equals(fakeHeadSkinUrl, that.fakeHeadSkinUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(texturesProperty, fakeHeadSkinUrl);
        }
    }

}
