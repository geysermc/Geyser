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

package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionJoinEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundPlayerLoadedPacket;

@Translator(packet = SetLocalPlayerAsInitializedPacket.class)
public class BedrockSetLocalPlayerAsInitializedTranslator extends PacketTranslator<SetLocalPlayerAsInitializedPacket> {
    @Override
    public void translate(GeyserSession session, SetLocalPlayerAsInitializedPacket packet) {
        if (session.getPlayerEntity().getGeyserId() == packet.getRuntimeEntityId()) {
            if (!session.getUpstream().isInitialized()) {
                session.getUpstream().setInitialized(true);

                if (session.remoteServer().authType() == AuthType.ONLINE) {
                    if (!session.isLoggedIn()) {
                        if (session.getGeyser().config().savedUserLogins().contains(session.bedrockUsername())) {
                            if (session.getGeyser().authChainFor(session.bedrockUsername()) == null) {
                                LoginEncryptionUtils.buildAndShowConsentWindow(session);
                            } else {
                                // If the auth chain is not null and we're here, then it expired
                                // and the expiration form has been cached
                                session.getFormCache().resendAllForms();
                            }
                        } else {
                            LoginEncryptionUtils.buildAndShowLoginWindow(session);
                        }
                    }
                    // else we were able to log the user in
                }
                if (session.isLoggedIn()) {
                    // Sigh - as of Bedrock 1.18
                    session.getEntityCache().updateBossBars();

                    // Double sigh - https://github.com/GeyserMC/Geyser/issues/2677 - as of Bedrock 1.18
                    if (session.getOpenInventory() != null && session.getOpenInventory().isPending()) {
                        InventoryUtils.openInventory(session, session.getOpenInventory());
                    }

                    // What am I to expect - as of Bedrock 1.18
                    session.getFormCache().resendAllForms();

                    GeyserImpl.getInstance().eventBus().fire(new SessionJoinEvent(session));
                    session.sendDownstreamGamePacket(ServerboundPlayerLoadedPacket.INSTANCE);
                }
            }
        }
    }
}
