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

package org.geysermc.connector.network;

import com.nukkitx.protocol.bedrock.BedrockPacket;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.packet.DisconnectPacket;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.packet.ModalFormResponsePacket;
import com.nukkitx.protocol.bedrock.packet.MovePlayerPacket;
import com.nukkitx.protocol.bedrock.packet.PlayStatusPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePackStackPacket;
import com.nukkitx.protocol.bedrock.packet.ResourcePacksInfoPacket;
import com.nukkitx.protocol.bedrock.packet.SetLocalPlayerAsInitializedPacket;
import com.nukkitx.protocol.bedrock.packet.SubClientLoginPacket;

import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.common.AuthType;
import org.geysermc.connector.configuration.GeyserConfiguration;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.session.auth.BedrockClientData;
import org.geysermc.connector.network.translators.PacketTranslatorRegistry;
import org.geysermc.connector.utils.LanguageUtils;
import org.geysermc.connector.utils.LoginEncryptionUtils;

public class UpstreamPacketHandler extends LoggingPacketHandler {

    public UpstreamPacketHandler(GeyserConnector connector, GeyserSession session) {
        super(connector, session);
    }

    private boolean translateAndDefault(BedrockPacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        return PacketTranslatorRegistry.BEDROCK_TRANSLATOR.translate(packet.getClass(), packet, session);
    }

    @Override
    public boolean handle(LoginPacket loginPacket) {
        final int clientId = loginPacket.getClientId();
        GeyserSession session = sessions.get(clientId);

        if (loginPacket.getProtocolVersion() > GeyserConnector.BEDROCK_PACKET_CODEC.getProtocolVersion()) {
            // Too early to determine session locale
            session.disconnect(LanguageUtils.getLocaleStringLog("geyser.network.outdated.server", GeyserConnector.BEDROCK_PACKET_CODEC.getMinecraftVersion()));
            return true;
        } else if (loginPacket.getProtocolVersion() < GeyserConnector.BEDROCK_PACKET_CODEC.getProtocolVersion()) {
            session.disconnect(LanguageUtils.getLocaleStringLog("geyser.network.outdated.client", GeyserConnector.BEDROCK_PACKET_CODEC.getMinecraftVersion()));
            return true;
        }

        LoginEncryptionUtils.handleCertChainData(connector, session, loginPacket);
        LoginEncryptionUtils.handleClientData(connector, session, loginPacket);
        LoginEncryptionUtils.encryptPlayerConnection(connector, session, loginPacket);

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(playStatus);

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        session.sendUpstreamPacket(resourcePacksInfo);
        return true;
    }

    public boolean handle(SubClientLoginPacket loginPacket) {
        final int clientId = loginPacket.getClientId();

        // TODO not very clean reaching through Upstream...
        BedrockServerSession bedrockServerSession = this.sessions.get(0).getUpstream().getSession();
        GeyserSession session = new GeyserSession(connector, bedrockServerSession, clientId);
        this.sessions.add(clientId, session);

        LoginEncryptionUtils.handleCertChainData(connector, session, loginPacket);
        LoginEncryptionUtils.handleClientData(connector, session, loginPacket);

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setSenderId(clientId);
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);

        session.sendUpstreamPacket(playStatus);

        session.connect(connector.getRemoteServer());

        return true;
    }

    @Override
    public boolean handle(ResourcePackClientResponsePacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        switch (packet.getStatus()) {
            case COMPLETED:
                session.connect(connector.getRemoteServer());
                connector.getLogger().info(LanguageUtils.getLocaleStringLog("geyser.network.connect", session.getAuthData().getName()));
                break;
            case HAVE_ALL_PACKS:
                ResourcePackStackPacket stack = new ResourcePackStackPacket();
                stack.setExperimental(false);
                stack.setForcedToAccept(false);
                stack.setGameVersion("*");
                session.sendUpstreamPacket(stack);
                break;
            default:
                session.disconnect("disconnectionScreen.resourcePack");
                break;
        }

        return true;
    }

    @Override
    public boolean handle(ModalFormResponsePacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        return LoginEncryptionUtils.authenticateFromForm(session, connector, packet.getFormId(), packet.getFormData());
    }

    private boolean couldLoginUserByName(GeyserSession session) {
        String bedrockUsername = session.getAuthData().getName();

        if (connector.getConfig().getUserAuths() != null) {
            GeyserConfiguration.IUserAuthenticationInfo info = connector.getConfig().getUserAuths().get(bedrockUsername);

            if (info != null) {
                connector.getLogger().info(LanguageUtils.getLocaleStringLog("geyser.auth.stored_credentials", session.getAuthData().getName()));
                session.authenticate(info.getEmail(), info.getPassword());

                // TODO send a message to bedrock user telling them they are connected (if nothing like a motd
                //      somes from the Java server w/in a few seconds)
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean handle(SetLocalPlayerAsInitializedPacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        LanguageUtils.loadGeyserLocale(session.getClientData().getLanguageCode());

        if (!session.isLoggedIn() && !session.isLoggingIn() && session.getConnector().getAuthType() == AuthType.ONLINE) {
            // TODO it is safer to key authentication on something that won't change (UUID, not username)
            if (!couldLoginUserByName(session)) {
                LoginEncryptionUtils.showLoginWindow(session);
            }
            // else we were able to log the user in
        }
        return translateAndDefault(packet);
    }

    @Override
    public boolean handle(MovePlayerPacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        if (session.isLoggingIn()) {
            session.sendMessage(LanguageUtils.getPlayerLocaleString("geyser.auth.login.wait", session.getClientData().getLanguageCode()));
        }

        return translateAndDefault(packet);
    }

    @Override
    boolean defaultHandler(BedrockPacket packet) {
        return translateAndDefault(packet);
    }

    @Override
    public boolean handle(DisconnectPacket packet) {
        final int clientId = packet.getClientId();
        GeyserSession session = sessions.get(clientId);

        session.disconnect(packet.getKickMessage());

        this.sessions.remove(clientId);

        return true;
    }
}
