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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.packet.ChangeDimensionPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.MobEffectPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.StopSoundPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;

import java.util.Set;

public class DimensionUtils {

    public static final String BEDROCK_FOG_HELL = "minecraft:fog_hell";

    public static void switchDimension(GeyserSession session, JavaDimension javaDimension) {
        switchDimension(session, javaDimension, javaDimension.bedrockId());
    }

    public static void switchDimension(GeyserSession session, JavaDimension javaDimension, int bedrockDimension) {
        @Nullable JavaDimension previousDimension = session.getDimensionType(); // previous java dimension; can be null if an online player with no saved auth token logs in.

        Entity player = session.getPlayerEntity();

        session.getChunkCache().clear();
        session.getEntityCache().removeAllEntities();
        session.getItemFrameCache().clear();
        session.getLodestoneCache().clear();
        session.getPistonCache().clear();
        session.getSkullCache().clear();

        changeDimension(session, bedrockDimension);

        session.setDimensionType(javaDimension);

        Set<Effect> entityEffects = session.getEffectCache().getEntityEffects();
        for (Effect effect : entityEffects) {
            MobEffectPacket mobEffectPacket = new MobEffectPacket();
            mobEffectPacket.setEvent(MobEffectPacket.Event.REMOVE);
            mobEffectPacket.setRuntimeEntityId(player.getGeyserId());
            mobEffectPacket.setEffectId(EntityUtils.toBedrockEffectId(effect));
            session.sendUpstreamPacket(mobEffectPacket);
        }
        // Effects are re-sent from server
        entityEffects.clear();

        // Always reset weather, as it sometimes suddenly starts raining. See https://github.com/GeyserMC/Geyser/issues/3679
        session.updateRain(0);
        session.updateThunder(0);

        finalizeDimensionSwitch(session, player);

        // If the bedrock nether height workaround is enabled, meaning the client is told it's in the end dimension,
        // we check if the player is entering the nether and apply the nether fog to fake the fact that the client
        // thinks they are in the end dimension.
        if (BedrockDimension.isCustomBedrockNetherId()) {
            if (javaDimension.isNetherLike()) {
                session.camera().sendFog(BEDROCK_FOG_HELL);
            } else if (previousDimension != null && previousDimension.isNetherLike()) {
                session.camera().removeFog(BEDROCK_FOG_HELL);
            }
        }
    }

    /**
     * Switch dimensions without clearing internal logic.
     */
    public static void fastSwitchDimension(GeyserSession session, int bedrockDimension) {
        changeDimension(session, bedrockDimension);
        finalizeDimensionSwitch(session, session.getPlayerEntity());
    }

    private static void changeDimension(GeyserSession session, int bedrockDimension) {
        if (session.getServerRenderDistance() > 32 && !session.isEmulatePost1_13Logic()) {
            // The server-sided view distance wasn't a thing until Minecraft Java 1.14
            // So ViaVersion compensates by sending a "view distance" of 64
            // That's fine, except when the actual view distance sent from the server is five chunks
            // The client locks up when switching dimensions, expecting more chunks than it's getting
            // To solve this, we cap at 32 unless we know that the render distance actually exceeds 32
            // Also, as of 1.19: PS4 crashes with a ChunkRadiusUpdatedPacket too large
            GeyserLogger.get().debug("Applying dimension switching workaround for Bedrock render distance of "
                + session.getServerRenderDistance());
            ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
            chunkRadiusUpdatedPacket.setRadius(32);
            session.sendUpstreamPacket(chunkRadiusUpdatedPacket);
            // Will be re-adjusted on spawn
        }

        Vector3f pos = Vector3f.from(0, Short.MAX_VALUE, 0);

        ChangeDimensionPacket changeDimensionPacket = new ChangeDimensionPacket();
        changeDimensionPacket.setDimension(bedrockDimension);
        changeDimensionPacket.setRespawn(true);
        changeDimensionPacket.setPosition(pos);
        session.sendUpstreamPacket(changeDimensionPacket);

        setBedrockDimension(session, bedrockDimension);

        session.getPlayerEntity().setPosition(pos);
        session.setSpawned(false);
        session.setLastChunkPosition(null);
    }

    private static void finalizeDimensionSwitch(GeyserSession session, Entity player) {
        //let java server handle portal travel sound
        StopSoundPacket stopSoundPacket = new StopSoundPacket();
        stopSoundPacket.setStoppingAllSound(true);
        stopSoundPacket.setSoundName("");
        session.sendUpstreamPacket(stopSoundPacket);

        // Kind of silly but Bedrock 1.19.50 and later requires an acknowledgement after the
        // initial chunks are sent, prior to the client acknowledgement
        // Note: send this before chunks are sent. Fixed https://github.com/GeyserMC/Geyser/issues/3421
        PlayerActionPacket ackPacket = new PlayerActionPacket();
        ackPacket.setRuntimeEntityId(player.getGeyserId());
        ackPacket.setAction(PlayerActionType.DIMENSION_CHANGE_SUCCESS);
        ackPacket.setBlockPosition(Vector3i.ZERO);
        ackPacket.setResultPosition(Vector3i.ZERO);
        ackPacket.setFace(0);
        session.sendUpstreamPacket(ackPacket);

        // TODO - fix this hack of a fix by sending the final dimension switching logic after sections have been sent.
        // The client wants sections sent to it before it can successfully respawn.
        ChunkUtils.sendEmptyChunks(session, player.getPosition().toInt(), 3, true);
    }

    public static void setBedrockDimension(GeyserSession session, int bedrockDimension) {
        session.setBedrockDimension(switch (bedrockDimension) {
            case BedrockDimension.END_ID -> BedrockDimension.THE_END;
            case BedrockDimension.DEFAULT_NETHER_ID -> BedrockDimension.THE_NETHER; // JavaDimension *should* be set to BEDROCK_END_ID if the Nether workaround is enabled.
            default -> session.getBedrockOverworldDimension();
        });
    }

    /**
     * Map the Java edition dimension IDs to Bedrock edition
     *
     * @param javaDimension Dimension ID to convert
     * @return Converted Bedrock edition dimension ID
     */
    public static int javaToBedrock(String javaDimension) {
        return switch (javaDimension) {
            case BedrockDimension.NETHER_IDENTIFIER -> BedrockDimension.BEDROCK_NETHER_ID;
            case "minecraft:the_end" -> BedrockDimension.END_ID;
            default -> BedrockDimension.OVERWORLD_ID;
        };
    }

    /**
     * Gets the Bedrock dimension ID, with a safety check if a packet is created before the player is logged/spawned in.
     */
    public static int javaToBedrock(GeyserSession session) {
        JavaDimension dimension = session.getDimensionType();
        if (dimension == null) {
            return BedrockDimension.OVERWORLD_ID;
        }
        return dimension.bedrockId();
    }

    /**
     * Gets the fake, temporary dimension we send clients to so we aren't switching to the same dimension without an additional
     * dimension switch.
     *
     * @param currentBedrockDimension the current dimension of the player
     * @param newBedrockDimension the new dimension that the player will be transferred to
     * @return the Bedrock fake dimension to transfer to
     */
    public static int getTemporaryDimension(int currentBedrockDimension, int newBedrockDimension) {
        if (BedrockDimension.isCustomBedrockNetherId()) {
            // Prevents rare instances of Bedrock locking up
            return newBedrockDimension == BedrockDimension.END_ID ? BedrockDimension.OVERWORLD_ID : BedrockDimension.END_ID;
        }
        // Check current Bedrock dimension and not just the Java dimension.
        // Fixes rare instances like https://github.com/GeyserMC/Geyser/issues/3161
        return currentBedrockDimension == BedrockDimension.OVERWORLD_ID ? BedrockDimension.DEFAULT_NETHER_ID : BedrockDimension.OVERWORLD_ID;
    }

}
