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

import org.cloudburstmc.protocol.bedrock.data.skin.ImageData;
import org.cloudburstmc.protocol.bedrock.data.skin.SerializedSkin;
import org.cloudburstmc.protocol.bedrock.packet.PlayerSkinPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.entity.type.player.SkullPlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SkullSkinManager extends SkinManager {

    public static SerializedSkin buildSkullEntryManually(GeyserSession session, String skinId, byte[] skinData) {
        skinId = skinId + "_skull";
        return SerializedSkin.builder()
            .skinId(skinId)
            .skinResourcePatch(SkinProvider.SKULL_GEOMETRY.geometryName())
            .skinData(ImageData.of(skinData))
            .capeData(ImageData.of(SkinProvider.EMPTY_CAPE.capeData()))
            .geometryData(SkinProvider.SKULL_GEOMETRY.geometryData())
            .premium(true)
            .capeId(SkinProvider.EMPTY_CAPE.capeId())
            .fullSkinId(skinId)
            .geometryDataEngineVersion(session.getClientData().getGameVersion())
            .build();
    }

    public static void requestAndHandleSkin(SkullPlayerEntity entity, GeyserSession session,
                                            Consumer<Skin> skinConsumer) {
        BiConsumer<Skin, Throwable> applySkin = (skin, throwable) -> {
            try {
                PlayerSkinPacket packet = new PlayerSkinPacket();
                packet.setUuid(entity.getUuid());
                packet.setOldSkinName("");
                packet.setNewSkinName(skin.textureUrl());
                packet.setSkin(buildSkullEntryManually(session, skin.textureUrl(), skin.skinData()));
                packet.setTrustedSkin(true);
                session.sendUpstreamPacket(packet);
            } catch (Exception e) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.skin.fail", entity.getUuid()), e);
            }

            if (skinConsumer != null) {
                skinConsumer.accept(skin);
            }
        };

        GameProfileData data = GameProfileData.from(entity);
        if (data == null) {
            GeyserLogger.get().debug("Using fallback skin for skull at " + entity.getSkullPosition() +
                    " with texture value: " + entity.getTexturesProperty() + " and UUID: " + entity.getSkullUUID());
            // No texture available, fallback using the UUID
            SkinData fallback = SkinProvider.determineFallbackSkinData(entity.getSkullUUID());
            applySkin.accept(fallback.skin(), null);
        } else {
            SkinProvider.requestSkin(entity.getUuid(), data.skinUrl(), true)
                    .whenCompleteAsync(applySkin);
        }
    }

}
