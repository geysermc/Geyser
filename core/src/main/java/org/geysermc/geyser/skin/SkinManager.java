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

import com.fasterxml.jackson.databind.JsonNode;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerListPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.entity.type.player.SkullPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.text.GeyserLocale;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class SkinManager {

    /**
     * Builds a Bedrock player list entry from our existing, cached Bedrock skin information
     */
    public static PlayerListPacket.Entry buildCachedEntry(GeyserSession session, PlayerEntity playerEntity) {
        // First: see if we have the cached skin texture ID.
        GameProfileData data = GameProfileData.from(playerEntity);
        Skin skin = null;
        Cape cape = null;
        SkinGeometry geometry = SkinGeometry.WIDE;
        if (data != null) {
            // GameProfileData is not null = server provided us with textures data to work with.
            skin = SkinProvider.getCachedSkin(data.skinUrl());
            cape = SkinProvider.getCachedCape(data.capeUrl());
            geometry = data.isAlex() ? SkinGeometry.SLIM : SkinGeometry.WIDE;
        }

        if (skin == null || cape == null) {
            // The server either didn't have a texture to send, or we didn't have the texture ID cached.
            // Let's see if this player is a Bedrock player, and if so, let's pull their skin.
            // Otherwise, grab the default player skin
            SkinData fallbackSkinData = SkinProvider.determineFallbackSkinData(playerEntity.getUuid());
            if (skin == null) {
                skin = fallbackSkinData.skin();
                geometry = fallbackSkinData.geometry();
            }
            if (cape == null) {
                cape = fallbackSkinData.cape();
            }
        }

        return buildEntryManually(
                session,
                playerEntity.getUuid(),
                playerEntity.getUsername(),
                playerEntity.getGeyserId(),
                skin,
                cape,
                geometry
        );
    }

    /**
     * With all the information needed, build a Bedrock player entry with translated skin information.
     */
    public static PlayerListPacket.Entry buildEntryManually(GeyserSession session, UUID uuid, String username, long geyserId,
                                                            Skin skin,
                                                            Cape cape,
                                                            SkinGeometry geometry) {
        SerializedSkin serializedSkin = getSkin(session, skin.textureUrl(), skin, cape, geometry);

        // This attempts to find the XUID of the player so profile images show up for Xbox accounts
        String xuid = "";
        GeyserSession playerSession = GeyserImpl.getInstance().connectionByUuid(uuid);

        if (playerSession != null) {
            xuid = playerSession.getAuthData().xuid();
        }

        PlayerListPacket.Entry entry;

        // If we are building a PlayerListEntry for our own session we use our AuthData UUID instead of the Java UUID
        // as Bedrock expects to get back its own provided UUID
        if (session.getPlayerEntity().getUuid().equals(uuid)) {
            entry = new PlayerListPacket.Entry(session.getAuthData().uuid());
        } else {
            entry = new PlayerListPacket.Entry(uuid);
        }

        entry.setName(username);
        entry.setEntityId(geyserId);
        entry.setSkin(serializedSkin);
        entry.setXuid(xuid);
        entry.setPlatformChatId("");
        entry.setTeacher(false);
        entry.setTrustedSkin(true);
        return entry;
    }

    public static void sendSkinPacket(GeyserSession session, PlayerEntity entity, SkinData skinData) {
        Skin skin = skinData.skin();
        Cape cape = skinData.cape();
        SkinGeometry geometry = skinData.geometry();

        if (entity.getUuid().equals(session.getPlayerEntity().getUuid())) {
            PlayerListPacket.Entry updatedEntry = buildEntryManually(
                    session,
                    entity.getUuid(),
                    entity.getUsername(),
                    entity.getGeyserId(),
                    skin,
                    cape,
                    geometry
            );

            PlayerListPacket playerAddPacket = new PlayerListPacket();
            playerAddPacket.setAction(PlayerListPacket.Action.ADD);
            playerAddPacket.getEntries().add(updatedEntry);
            session.sendUpstreamPacket(playerAddPacket);
        } else {
            PlayerSkinPacket packet = new PlayerSkinPacket();
            packet.setUuid(entity.getUuid());
            packet.setOldSkinName("");
            packet.setNewSkinName(skin.textureUrl());
            packet.setSkin(getSkin(session, skin.textureUrl(), skin, cape, geometry));
            packet.setTrustedSkin(true);
            session.sendUpstreamPacket(packet);
        }
    }

    private static SerializedSkin getSkin(GeyserSession session, String skinId, Skin skin, Cape cape, SkinGeometry geometry) {
        return SerializedSkin.builder()
            .skinId(skinId)
            .skinResourcePatch(geometry.geometryName())
            .skinData(ImageData.of(skin.skinData()))
            .capeData(ImageData.of(cape.capeData()))
            .geometryData(geometry.geometryData())
            .premium(true)
            .capeId(cape.capeId())
            .fullSkinId(skinId)
            .geometryDataEngineVersion(session.getClientData().getGameVersion())
            .build();
    }

    public static void requestAndHandleSkinAndCape(PlayerEntity entity, GeyserSession session,
                                                   Consumer<SkinProvider.SkinAndCape> skinAndCapeConsumer) {
        SkinProvider.requestSkinData(entity, session).whenCompleteAsync((skinData, throwable) -> {
            if (skinData == null) {
                if (skinAndCapeConsumer != null) {
                    skinAndCapeConsumer.accept(null);
                }

                return;
            }

            if (skinData.geometry() != null) {
                sendSkinPacket(session, entity, skinData);
            }

            if (skinAndCapeConsumer != null) {
                skinAndCapeConsumer.accept(new SkinProvider.SkinAndCape(skinData.skin(), skinData.cape()));
            }
        });
    }

    public static void handleBedrockSkin(PlayerEntity playerEntity, BedrockClientData clientData) {
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser.getConfig().isDebugMode()) {
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.skin.bedrock.register", playerEntity.getUsername(), playerEntity.getUuid()));
        }

        try {
            byte[] skinBytes = Base64.getDecoder().decode(clientData.getSkinData().getBytes(StandardCharsets.UTF_8));
            byte[] capeBytes = clientData.getCapeData();

            byte[] geometryNameBytes = Base64.getDecoder().decode(clientData.getGeometryName().getBytes(StandardCharsets.UTF_8));
            byte[] geometryBytes = Base64.getDecoder().decode(clientData.getGeometryData().getBytes(StandardCharsets.UTF_8));

            if (skinBytes.length <= (128 * 128 * 4) && !clientData.isPersonaSkin()) {
                SkinProvider.storeBedrockSkin(playerEntity.getUuid(), clientData.getSkinId(), skinBytes);
                SkinProvider.storeBedrockGeometry(playerEntity.getUuid(), geometryNameBytes, geometryBytes);
            } else if (geyser.getConfig().isDebugMode()) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.skin.bedrock.fail", playerEntity.getUsername()));
                geyser.getLogger().debug("The size of '" + playerEntity.getUsername() + "' skin is: " + clientData.getSkinImageWidth() + "x" + clientData.getSkinImageHeight());
            }

            if (!clientData.getCapeId().equals("")) {
                SkinProvider.storeBedrockCape(clientData.getCapeId(), capeBytes);
            }
        } catch (Exception e) {
            throw new AssertionError("Failed to cache skin for bedrock user (" + playerEntity.getUsername() + "): ", e);
        }
    }

    public record GameProfileData(String skinUrl, String capeUrl, boolean isAlex) {
        /**
         * Generate the GameProfileData from the given CompoundTag representing a GameProfile
         *
         * @param tag tag to build the GameProfileData from
         * @return The built GameProfileData, or null if this wasn't a valid tag
         */
        public static @Nullable GameProfileData from(NbtMap tag) {
            NbtMap properties = tag.getCompound("Properties", null);
            if (properties == null) {
                return null;
            }
            List<NbtMap> textures = properties.getList("textures", NbtType.COMPOUND);
            if (textures.isEmpty()) {
                return null;
            }
            String skinDataValue = textures.get(0).getString("Value", null);
            if (skinDataValue == null) {
                return null;
            }

            try {
                return loadFromJson(skinDataValue);
            } catch (IOException e) {
                GeyserLogger.get().debug("Something went wrong while processing skin for tag " + tag);
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    e.printStackTrace();
                }
                return null;
            }
        }

        /**
         * Generate the GameProfileData from the given player entity
         *
         * @param entity entity to build the GameProfileData from
         * @return The built GameProfileData
         */
        public static @Nullable GameProfileData from(PlayerEntity entity) {
            String texturesProperty = entity.getTexturesProperty();
            if (texturesProperty == null) {
                // Likely offline mode
                return null;
            }

            try {
                return loadFromJson(texturesProperty);
            } catch (Exception exception) {
                if (entity instanceof SkullPlayerEntity skullEntity) {
                    GeyserLogger.get().debug("Something went wrong while processing skin for skull at " + skullEntity.getSkullPosition() + " with Value: " + texturesProperty);
                } else {
                    GeyserLogger.get().debug("Something went wrong while processing skin for " + entity.getUsername() + " with Value: " + texturesProperty);
                }
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    exception.printStackTrace();
                }
            }
            return null;
        }

        public static @Nullable GameProfileData loadFromJson(String encodedJson) throws IOException, IllegalArgumentException {
            JsonNode skinObject;
            try {
                skinObject = GeyserImpl.JSON_MAPPER.readTree(new String(Base64.getDecoder().decode(encodedJson), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                GeyserLogger.get().debug("Invalid base64 encoded skin entry: " + encodedJson);
                return null;
            }

            JsonNode textures = skinObject.get("textures");

            if (textures == null) {
                return null;
            }

            JsonNode skinTexture = textures.get("SKIN");
            if (skinTexture == null) {
                return null;
            }

            String skinUrl;
            JsonNode skinUrlNode = skinTexture.get("url");
            if (skinUrlNode != null && skinUrlNode.isTextual()) {
                skinUrl = skinUrlNode.asText().replace("http://", "https://");
            } else {
                return null;
            }

            if (DEFAULT_FLOODGATE_STEVE.equals(skinUrl)) {
                // https://github.com/GeyserMC/Floodgate/commit/00b8b1b6364116ff4bc9b00e2015ce35bae8abb1 ensures that
                // Bedrock players on online-mode servers will always have a textures property. However, this skin is
                // also sent our way, and isn't overwritten. It's very likely that this skin is *only* a placeholder,
                // and no one should ever be using it outside of Floodgate, and therefore no one wants to see this
                // specific Steve skin.
                return null;
            }

            boolean isAlex = skinTexture.has("metadata");

            String capeUrl = null;
            JsonNode capeTexture = textures.get("CAPE");
            if (capeTexture != null) {
                JsonNode capeUrlNode = capeTexture.get("url");
                if (capeUrlNode != null && capeUrlNode.isTextual()) {
                    capeUrl = capeUrlNode.asText().replace("http://", "https://");
                }
            }

            return new GameProfileData(skinUrl, capeUrl, isAlex);
        }

        private static final String DEFAULT_FLOODGATE_STEVE = "https://textures.minecraft.net/texture/31f477eb1a7beee631c2ca64d06f8f68fa93a3386d04452ab27f43acdf1b60cb";
    }
}
