/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.network.translators.bedrock.entity.player;

import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.world.block.BlockFace;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerActionPacket;
import com.nukkitx.protocol.bedrock.packet.EmotePacket;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.configuration.EmoteOffhandWorkaroundOption;
import org.geysermc.connector.entity.Entity;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.PacketTranslator;
import org.geysermc.connector.network.translators.Translator;
import org.geysermc.connector.utils.BlockUtils;
import org.geysermc.connector.utils.PluginMessageUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Translator(packet = EmotePacket.class)
public class BedrockEmoteTranslator extends PacketTranslator<EmotePacket> {

    @Override
    public void translate(GeyserSession session, EmotePacket packet) {
        if (session.getConnector().getConfig().getEmoteOffhandWorkaround() != EmoteOffhandWorkaroundOption.DISABLED) {
            // Activate the workaround - we should trigger the offhand now
            ClientPlayerActionPacket swapHandsPacket = new ClientPlayerActionPacket(PlayerAction.SWAP_HANDS, BlockUtils.POSITION_ZERO,
                    BlockFace.DOWN);
            session.sendDownstreamPacket(swapHandsPacket);

            if (session.getConnector().getConfig().getEmoteOffhandWorkaround() == EmoteOffhandWorkaroundOption.NO_EMOTES) {
                return;
            }
        }

        if (session.canSendDownstream(PluginMessageUtils.EMOTE_CHANNEL)) {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] emoteId = packet.getEmoteId().getBytes(StandardCharsets.UTF_8);
                out.write(emoteId.length);
                out.write(emoteId);

                PluginMessageUtils.sendMessage(session, PluginMessageUtils.EMOTE_CHANNEL, out.toByteArray());
            } catch (Exception e) {
                GeyserConnector.getInstance().getLogger().error("Failed to send emote data downstream!", e);
            }
        } else {
            long javaId = session.getPlayerEntity().getEntityId();
            for (GeyserSession otherSession : session.getConnector().getPlayers()) {
                if (otherSession != session) {
                    if (otherSession.isClosed()) continue;
                    if (otherSession.getEventLoop().inEventLoop()) {
                        playEmote(otherSession, javaId, packet.getEmoteId());
                    } else {
                        session.executeInEventLoop(() -> playEmote(otherSession, javaId, packet.getEmoteId()));
                    }
                }
            }
        }
    }

    private void playEmote(GeyserSession otherSession, long javaId, String emoteId) {
        Entity otherEntity = otherSession.getEntityCache().getEntityByJavaId(javaId); // Must be ran on same thread
        if (otherEntity == null) return;
        EmotePacket otherEmotePacket = new EmotePacket();
        otherEmotePacket.setEmoteId(emoteId);
        otherEmotePacket.setRuntimeEntityId(otherEntity.getGeyserId());
        otherSession.sendUpstreamPacket(otherEmotePacket);
    }
}
