/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.utils;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.google.gson.JsonObject;
import com.nukkitx.protocol.bedrock.data.ImageData;
import com.nukkitx.protocol.bedrock.data.SerializedSkin;
import com.nukkitx.protocol.bedrock.packet.PlayerListPacket;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.geysermc.common.AuthType;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.entity.PlayerEntity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.session.auth.BedrockClientData;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.function.Consumer;

public class SkinUtils {

    public static PlayerListPacket.Entry buildCachedEntry(GameProfile profile, long geyserId) {
        GameProfileData data = GameProfileData.from(profile);
        SkinProvider.Cape cape = SkinProvider.getCachedCape(data.getCapeUrl());

        SkinProvider.SkinGeometry geometry = SkinProvider.SkinGeometry.getLegacy("geometry.humanoid.custom" + (data.isAlex() ? "Slim" : ""));

        return buildEntryManually(
                profile.getId(),
                profile.getName(),
                geyserId,
                profile.getIdAsString(),
                SkinProvider.getCachedSkin(profile.getId()).getSkinData(),
                cape.getCapeId(),
                cape.getCapeData(),
                geometry.getGeometryName(),
                geometry.getGeometryData()
        );
    }

    public static PlayerListPacket.Entry buildDefaultEntry(GameProfile profile, long geyserId) {
        return buildEntryManually(
                profile.getId(),
                profile.getName(),
                geyserId,
                profile.getIdAsString(),
                SkinProvider.STEVE_SKIN,
                SkinProvider.EMPTY_CAPE.getCapeId(),
                SkinProvider.EMPTY_CAPE.getCapeData(),
                SkinProvider.EMPTY_GEOMETRY.getGeometryName(),
                SkinProvider.EMPTY_GEOMETRY.getGeometryData()
        );
    }

    public static PlayerListPacket.Entry buildEntryManually(UUID uuid, String username, long geyserId,
                                                            String skinId, byte[] skinData,
                                                            String capeId, byte[] capeData,
                                                            String geometryName, String geometryData) {
        SerializedSkin serializedSkin = SerializedSkin.of(
                skinId, geometryName, ImageData.of(skinData), Collections.emptyList(),
                ImageData.of(capeData), geometryData, "", true, false, false, capeId, uuid.toString()
        );

        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(uuid);
        entry.setName(username);
        entry.setEntityId(geyserId);
        entry.setSkin(serializedSkin);
        entry.setXuid("");
        entry.setPlatformChatId("");
        entry.setTeacher(false);
        return entry;
    }

    @AllArgsConstructor
    @Getter
    public static class GameProfileData {
        private String skinUrl;
        private String capeUrl;
        private boolean alex;

        public static GameProfileData from(GameProfile profile) {
            try {
                GameProfile.Property skinProperty = profile.getProperty("textures");

                JsonObject skinObject = SkinProvider.GSON.fromJson(new String(Base64.getDecoder().decode(skinProperty.getValue()), StandardCharsets.UTF_8), JsonObject.class);
                JsonObject textures = skinObject.getAsJsonObject("textures");

                JsonObject skinTexture = textures.getAsJsonObject("SKIN");
                String skinUrl = skinTexture.get("url").getAsString();

                boolean isAlex = skinTexture.has("metadata");

                String capeUrl = null;
                if (textures.has("CAPE")) {
                    JsonObject capeTexture = textures.getAsJsonObject("CAPE");
                    capeUrl = capeTexture.get("url").getAsString();
                }

                return new GameProfileData(skinUrl, capeUrl, isAlex);
            } catch (Exception exception) {
                if (GeyserConnector.getInstance().getAuthType() != AuthType.OFFLINE) {
                    GeyserConnector.getInstance().getLogger().debug("Got invalid texture data for " + profile.getName() + " " + exception.getMessage());
                }
                // return default skin with default cape when texture data is invalid
                return new GameProfileData(SkinProvider.EMPTY_SKIN.getTextureUrl(), SkinProvider.EMPTY_CAPE.getTextureUrl(), false);
            }
        }
    }

    public static void requestAndHandleSkinAndCape(PlayerEntity entity, GeyserSession session,
                                                   Consumer<SkinProvider.SkinAndCape> skinAndCapeConsumer) {
        GeyserConnector.getInstance().getGeneralThreadPool().execute(() -> {
            GameProfileData data = GameProfileData.from(entity.getProfile());

            // Check if the entity is a bedrock player
            if (entity.getEntityId() == -1) {
                // Handle offline and floodgate mode bedrock skins
                SkinUtils.handleBedrockSkin(entity, session.getClientData());
            }

            SkinProvider.requestSkinAndCape(entity.getUuid(), data.getSkinUrl(), data.getCapeUrl())
                    .whenCompleteAsync((skinAndCape, throwable) -> {
                        try {
                            SkinProvider.Skin skin = skinAndCape.getSkin();
                            SkinProvider.Cape cape = skinAndCape.getCape();

                            if (cape.isFailed() && SkinProvider.ALLOW_THIRD_PARTY_CAPES) {
                                cape = SkinProvider.getOrDefault(SkinProvider.requestUnofficialCape(
                                        cape, entity.getUuid(),
                                        entity.getUsername(), false
                                ), SkinProvider.EMPTY_CAPE, SkinProvider.UnofficalCape.VALUES.length * 3);
                            }

                            if (cape.isFailed()) {
                                cape = SkinProvider.getOrDefault(SkinProvider.requestBedrockCape(
                                        entity.getUuid(), false
                                ), SkinProvider.EMPTY_CAPE, 3);
                            }

                            SkinProvider.SkinGeometry geometry = SkinProvider.SkinGeometry.getLegacy("geometry.humanoid.custom" + (data.isAlex() ? "Slim" : ""));
                            geometry = SkinProvider.getOrDefault(SkinProvider.requestBedrockGeometry(
                                    geometry, entity.getUuid(), false
                            ), geometry, 3);

                            if (entity.getLastSkinUpdate() < skin.getRequestedOn()) {
                                entity.setLastSkinUpdate(skin.getRequestedOn());

                                if (session.getUpstream().isInitialized()) {
                                    PlayerListPacket.Entry updatedEntry = buildEntryManually(
                                            entity.getUuid(),
                                            entity.getUsername(),
                                            entity.getGeyserId(),
                                            entity.getUuid().toString(),
                                            skin.getSkinData(),
                                            cape.getCapeId(),
                                            cape.getCapeData(),
                                            geometry.getGeometryName(),
                                            geometry.getGeometryData()
                                    );

                                    PlayerListPacket playerRemovePacket = new PlayerListPacket();
                                    playerRemovePacket.setAction(PlayerListPacket.Action.REMOVE);
                                    playerRemovePacket.getEntries().add(updatedEntry);
                                    session.getUpstream().sendPacket(playerRemovePacket);

                                    PlayerListPacket playerAddPacket = new PlayerListPacket();
                                    playerAddPacket.setAction(PlayerListPacket.Action.ADD);
                                    playerAddPacket.getEntries().add(updatedEntry);
                                    session.getUpstream().sendPacket(playerAddPacket);
                                }
                            }
                        } catch (Exception e) {
                            GeyserConnector.getInstance().getLogger().error("Failed getting skin for " + entity.getUuid(), e);
                        }

                        if (skinAndCapeConsumer != null) skinAndCapeConsumer.accept(skinAndCape);
                    });

        });
    }

    public static void handleBedrockSkin(PlayerEntity playerEntity, BedrockClientData clientData) {
        GameProfileData data = GameProfileData.from(playerEntity.getProfile());

        GeyserConnector.getInstance().getLogger().info("Registering my skin: " + playerEntity.getUsername());

        try {
            byte[] skinBytes = com.github.steveice10.mc.auth.util.Base64.decode(clientData.getSkinData().getBytes("UTF-8"));
            byte[] capeBytes = clientData.getCapeData();

            byte[] geometryNameBytes = com.github.steveice10.mc.auth.util.Base64.decode(clientData.getGeometryName().getBytes("UTF-8"));
            byte[] geometryBytes = com.github.steveice10.mc.auth.util.Base64.decode(clientData.getGeometryData().getBytes("UTF-8"));

            if (skinBytes.length <= (128 * 128 * 4)) {
                SkinProvider.storeBedrockSkin(playerEntity.getUuid(), data.getSkinUrl(), skinBytes);
            }else{
                GeyserConnector.getInstance().getLogger().info("Unable to load skin for '" + playerEntity.getUsername() + "' as they are using a customised skin");
            }
            SkinProvider.storeBedrockGeometry(playerEntity.getUuid(), geometryNameBytes, geometryBytes);
            if (!clientData.getCapeId().equals("")) {
                SkinProvider.storeBedrockCape(playerEntity.getUuid(), capeBytes);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to cache skin for bedrock user (" + playerEntity.getUsername() + "): ", e);
        }
    }
}
