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

package org.geysermc.geyser.network;

import io.netty.buffer.Unpooled;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.compat.BedrockCompat;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.ResourcePackType;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.SimpleCompressionStrategy;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.ZlibCompression;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackChunkRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackDataInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackStackPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePacksInfoPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.protocol.common.util.Zlib;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionInitializeEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.pack.PackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.api.pack.UrlPackCodec;
import org.geysermc.geyser.api.pack.option.ResourcePackOption;
import org.geysermc.geyser.event.type.SessionLoadResourcePacksEventImpl;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.ResourcePackHolder;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.loader.ResourcePackLoader;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.VersionCheckUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.OptionalInt;
import java.util.UUID;

public class UpstreamPacketHandler extends LoggingPacketHandler {

    private boolean networkSettingsRequested = false;
    private final Deque<String> packsToSend = new ArrayDeque<>();
    private final CompressionStrategy compressionStrategy;

    private SessionLoadResourcePacksEventImpl resourcePackLoadEvent;

    public UpstreamPacketHandler(GeyserImpl geyser, GeyserSession session) {
        super(geyser, session);

        ZlibCompression compression = new ZlibCompression(Zlib.RAW);
        compression.setLevel(this.geyser.getConfig().getBedrock().getCompressionLevel());
        this.compressionStrategy = new SimpleCompressionStrategy(compression);
    }

    private PacketSignal translateAndDefault(BedrockPacket packet) {
        Registries.BEDROCK_PACKET_TRANSLATORS.translate(packet.getClass(), packet, session, false);
        return PacketSignal.HANDLED; // PacketSignal.UNHANDLED will log a WARN publicly
    }

    @Override
    PacketSignal defaultHandler(BedrockPacket packet) {
        return translateAndDefault(packet);
    }

    private boolean setCorrectCodec(int protocolVersion) {
        BedrockCodec packetCodec = GameProtocol.getBedrockCodec(protocolVersion);
        if (packetCodec == null) {
            String supportedVersions = GameProtocol.getAllSupportedBedrockVersions();
            if (protocolVersion > GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                // Too early to determine session locale
                String disconnectMessage = GeyserLocale.getLocaleStringLog("geyser.network.outdated.server", supportedVersions);
                // If the latest release matches this version, then let the user know.
                OptionalInt latestRelease = VersionCheckUtils.getLatestBedrockRelease();
                if (latestRelease.isPresent() && latestRelease.getAsInt() == protocolVersion) {
                    // Random note: don't make the disconnect message too long or Bedrock will cut it off on smaller screens
                    disconnectMessage += "\n" + GeyserLocale.getLocaleStringLog("geyser.version.new.on_disconnect", Constants.GEYSER_DOWNLOAD_LOCATION);
                }
                session.disconnect(disconnectMessage);
                return false;
            } else if (protocolVersion < GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion()) {
                // A note on the following line: various older client versions have different forms of DisconnectPacket.
                // Using only the latest BedrockCompat for such clients leads to inaccurate disconnect messages: https://github.com/GeyserMC/Geyser/issues/4378
                // This updates the BedrockCompat protocol if necessary:
                session.getUpstream().getSession().setCodec(BedrockCompat.disconnectCompat(protocolVersion));

                session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", supportedVersions));
                return false;
            } else {
                throw new IllegalStateException("Default codec of protocol version " + protocolVersion + " should have been found");
            }
        }

        session.getUpstream().getSession().setCodec(packetCodec);
        return true;
    }

    @Override
    public void onDisconnect(String reason) {
        // Use our own disconnect messages for these reasons
        if (BedrockDisconnectReasons.CLOSED.equals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.closed_by_remote_peer"));
        } else if (BedrockDisconnectReasons.TIMEOUT.equals(reason)) {
            this.session.getUpstream().getSession().setDisconnectReason(GeyserLocale.getLocaleStringLog("geyser.network.disconnect.timed_out"));
        }
        this.session.disconnect(this.session.getUpstream().getSession().getDisconnectReason());
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        if (!setCorrectCodec(packet.getProtocolVersion())) {
            return PacketSignal.HANDLED;
        }

        // New since 1.19.30 - sent before login packet
        PacketCompressionAlgorithm algorithm = PacketCompressionAlgorithm.ZLIB;

        NetworkSettingsPacket responsePacket = new NetworkSettingsPacket();
        responsePacket.setCompressionAlgorithm(algorithm);
        responsePacket.setCompressionThreshold(512);
        session.sendUpstreamPacketImmediately(responsePacket);
        session.getUpstream().getSession().getPeer().setCompression(compressionStrategy);

        networkSettingsRequested = true;
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket loginPacket) {
        if (geyser.isShuttingDown() || geyser.isReloading()) {
            // Don't allow new players in if we're no longer operating
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            return PacketSignal.HANDLED;
        }

        if (!networkSettingsRequested) {
            session.disconnect(GeyserLocale.getLocaleStringLog("geyser.network.outdated.client", GameProtocol.getAllSupportedBedrockVersions()));
            return PacketSignal.HANDLED;
        }

        // Set the block translation based off of version
        session.setBlockMappings(BlockRegistries.BLOCKS.forVersion(loginPacket.getProtocolVersion()));
        session.setItemMappings(Registries.ITEMS.forVersion(loginPacket.getProtocolVersion()));

        LoginEncryptionUtils.encryptPlayerConnection(session, loginPacket);

        if (session.isClosed()) {
            // Can happen if Xbox validation fails
            return PacketSignal.HANDLED;
        }

        // Fire SessionInitializeEvent here as we now know the client data
        geyser.eventBus().fire(new SessionInitializeEvent(session));

        PlayStatusPacket playStatus = new PlayStatusPacket();
        playStatus.setStatus(PlayStatusPacket.Status.LOGIN_SUCCESS);
        session.sendUpstreamPacket(playStatus);

        geyser.getSessionManager().addPendingSession(session);

        this.resourcePackLoadEvent = new SessionLoadResourcePacksEventImpl(session);
        this.geyser.eventBus().fire(this.resourcePackLoadEvent);

        ResourcePacksInfoPacket resourcePacksInfo = new ResourcePacksInfoPacket();
        resourcePacksInfo.getResourcePackInfos().addAll(this.resourcePackLoadEvent.infoPacketEntries());

        resourcePacksInfo.setForcedToAccept(GeyserImpl.getInstance().getConfig().isForceResourcePacks());
        resourcePacksInfo.setWorldTemplateId(UUID.randomUUID());
        resourcePacksInfo.setWorldTemplateVersion("*");
        session.sendUpstreamPacket(resourcePacksInfo);

        GeyserLocale.loadGeyserLocale(session.locale());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        switch (packet.getStatus()) {
            case COMPLETED -> {
                if (geyser.getConfig().getRemote().authType() != AuthType.ONLINE) {
                    session.authenticate(session.getAuthData().name());
                } else if (!couldLoginUserByName(session.getAuthData().name())) {
                    // We must spawn the white world
                    session.connect();
                }
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.connect", session.getAuthData().name()));
            }
            case SEND_PACKS -> {
                packsToSend.addAll(packet.getPackIds());
                sendPackDataInfo(packsToSend.pop());
            }
            case HAVE_ALL_PACKS -> {
                ResourcePackStackPacket stackPacket = new ResourcePackStackPacket();
                stackPacket.setExperimentsPreviouslyToggled(false);
                stackPacket.setForcedToAccept(false); // Leaving this as false allows the player to choose to download or not
                stackPacket.setGameVersion(session.getClientData().getGameVersion());
                stackPacket.getResourcePacks().addAll(this.resourcePackLoadEvent.orderedPacks());

                session.sendUpstreamPacket(stackPacket);
            }
            default -> session.disconnect("disconnectionScreen.resourcePack");
        }

        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ModalFormResponsePacket packet) {
        session.executeInEventLoop(() -> session.getFormCache().handleResponse(packet));
        return PacketSignal.HANDLED;
    }

    private boolean couldLoginUserByName(String bedrockUsername) {
        if (geyser.getConfig().getSavedUserLogins().contains(bedrockUsername)) {
            String authChain = geyser.authChainFor(bedrockUsername);
            if (authChain != null) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.auth.stored_credentials", session.getAuthData().name()));
                session.authenticateWithAuthChain(authChain);
                return true;
            }
        }
        PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(session.getAuthData().xuid());
        if (task != null) {
            return task.getAuthentication().isDone() && session.onMicrosoftLoginComplete(task);
        }

        return false;
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        // This doesn't catch rotation, but for a niche case I don't exactly want to cache rotation...
        if (session.isLoggingIn() && !packet.getMotion().equals(Vector2f.ZERO)) {
            SetTitlePacket titlePacket = new SetTitlePacket();
            titlePacket.setType(SetTitlePacket.Type.ACTIONBAR);
            titlePacket.setText(GeyserLocale.getPlayerLocaleString("geyser.auth.login.wait", session.locale()));
            titlePacket.setFadeInTime(0);
            titlePacket.setFadeOutTime(1);
            titlePacket.setStayTime(2);
            titlePacket.setXuid("");
            titlePacket.setPlatformOnlineId("");
            session.sendUpstreamPacket(titlePacket);
        }

        return translateAndDefault(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        ResourcePackHolder holder = this.resourcePackLoadEvent.getPacks().get(packet.getPackId());

        if (holder == null) {
            GeyserImpl.getInstance().getLogger().debug("Client {0} tried to request pack id {1} not sent to it!",
                session.bedrockUsername(), packet.getPackId());
            session.disconnect("disconnectionScreen.resourcePack");
            return PacketSignal.HANDLED;
        }

        ResourcePack pack = holder.pack();
        ResourcePackChunkDataPacket data = new ResourcePackChunkDataPacket();
        PackCodec codec = pack.codec();

        // If a remote pack ends up here, that usually implies that a client was not able to download the pack
        if (codec instanceof UrlPackCodec urlPackCodec) {
            ResourcePackLoader.testRemotePack(session, urlPackCodec, packet.getPackId(), packet.getPackVersion());

            if (!resourcePackLoadEvent.value(pack.uuid(), ResourcePackOption.Type.FALLBACK, true)) {
                session.disconnect("Unable to provide downloaded resource pack. Contact an administrator!");
                return PacketSignal.HANDLED;
            }
        }

        data.setChunkIndex(packet.getChunkIndex());
        data.setProgress((long) packet.getChunkIndex() * GeyserResourcePack.CHUNK_SIZE);
        data.setPackVersion(packet.getPackVersion());
        data.setPackId(packet.getPackId());

        int offset = packet.getChunkIndex() * GeyserResourcePack.CHUNK_SIZE;
        long remainingSize = codec.size() - offset;
        byte[] packData = new byte[(int) MathUtils.constrain(remainingSize, 0, GeyserResourcePack.CHUNK_SIZE)];

        try (SeekableByteChannel channel = codec.serialize()) {
            channel.position(offset);
            channel.read(ByteBuffer.wrap(packData, 0, packData.length));
        } catch (IOException e) {
            session.disconnect("disconnectionScreen.resourcePack");
            e.printStackTrace();
        }

        data.setData(Unpooled.wrappedBuffer(packData));

        session.sendUpstreamPacket(data);

        // Check if it is the last chunk and send next pack in queue when available.
        if (remainingSize <= GeyserResourcePack.CHUNK_SIZE && !packsToSend.isEmpty()) {
            sendPackDataInfo(packsToSend.pop());
        }

        return PacketSignal.HANDLED;
    }

    private void sendPackDataInfo(String id) {
        ResourcePackDataInfoPacket data = new ResourcePackDataInfoPacket();
        String[] packID = id.split("_");

        if (packID.length < 2) {
            GeyserImpl.getInstance().getLogger().debug("Client {0} tried to request invalid pack id {1}!",
                session.bedrockUsername(), packID);
            session.disconnect("disconnectionScreen.resourcePack");
            return;
        }

        UUID packId;
        try {
            packId = UUID.fromString(packID[0]);
        } catch (IllegalArgumentException e) {
            GeyserImpl.getInstance().getLogger().debug("Client {0} tried to request pack with an invalid id {1})",
                session.bedrockUsername(), id);
            session.disconnect("disconnectionScreen.resourcePack");
            return;
        }

        ResourcePackHolder holder = this.resourcePackLoadEvent.getPacks().get(packId);
        if (holder == null) {
            GeyserImpl.getInstance().getLogger().debug("Client {0} tried to request pack id {1} not sent to it!",
                session.bedrockUsername(), id);
            session.disconnect("disconnectionScreen.resourcePack");
            return;
        }

        ResourcePack pack = holder.pack();
        PackCodec codec = pack.codec();
        ResourcePackManifest.Header header = pack.manifest().header();

        data.setPackId(header.uuid());
        int chunkCount = (int) Math.ceil(codec.size() / (double) GeyserResourcePack.CHUNK_SIZE);
        data.setChunkCount(chunkCount);
        data.setCompressedPackSize(codec.size());
        data.setMaxChunkSize(GeyserResourcePack.CHUNK_SIZE);
        data.setHash(codec.sha256());
        data.setPackVersion(packID[1]);
        data.setPremium(false);
        data.setType(ResourcePackType.RESOURCES);

        session.sendUpstreamPacket(data);
    }
}
