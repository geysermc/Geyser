/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.session;

import org.geysermc.floodgate.crypto.FloodgateCipher;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.skin.FloodgateSkinUploader;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.ConnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketErrorEvent;
import org.geysermc.mcprotocollib.network.event.session.PacketSendingEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.UnexpectedEncryptionException;
import org.geysermc.mcprotocollib.protocol.packet.handshake.serverbound.ClientIntentionPacket;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class GeyserSessionAdapter extends SessionAdapter {

    private final GeyserImpl geyser;
    private final GeyserSession geyserSession;
    private final boolean floodgate;
    private final String locale;

    public GeyserSessionAdapter(GeyserSession session) {
        this.geyserSession = session;
        this.floodgate = session.remoteServer().authType() == AuthType.FLOODGATE;
        this.geyser = GeyserImpl.getInstance();
        this.locale = session.locale();
    }

    @Override
    public void packetSending(PacketSendingEvent event) {
        if (event.getPacket() instanceof ClientIntentionPacket intentionPacket) {
            BedrockClientData clientData = geyserSession.getClientData();

            String addressSuffix;
            if (floodgate) {
                byte[] encryptedData;

                try {
                    FloodgateSkinUploader skinUploader = geyser.getSkinUploader();
                    FloodgateCipher cipher = geyser.getCipher();

                    String bedrockAddress = geyserSession.getUpstream().getAddress().getAddress().getHostAddress();
                    // both BungeeCord and Velocity remove the IPv6 scope (if there is one) for Spigot
                    int ipv6ScopeIndex = bedrockAddress.indexOf('%');
                    if (ipv6ScopeIndex != -1) {
                        bedrockAddress = bedrockAddress.substring(0, ipv6ScopeIndex);
                    }

                    encryptedData = cipher.encryptFromString(BedrockData.of(
                        clientData.getGameVersion(),
                        geyserSession.bedrockUsername(),
                        geyserSession.xuid(),
                        clientData.getDeviceOs().ordinal(),
                        clientData.getLanguageCode(),
                        clientData.getUiProfile().ordinal(),
                        clientData.getCurrentInputMode().ordinal(),
                        bedrockAddress,
                        skinUploader.getId(),
                        skinUploader.getVerifyCode()
                    ).toString());
                } catch (Exception e) {
                    geyser.getLogger().error(GeyserLocale.getLocaleStringLog("geyser.auth.floodgate.encrypt_fail"), e);
                    geyserSession.disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.floodgate.encrypt_fail", locale));
                    return;
                }

                addressSuffix = '\0' + new String(encryptedData, StandardCharsets.UTF_8);
            } else {
                addressSuffix = "";
            }

            String address;
            if (geyser.getConfig().getRemote().isForwardHost()) {
                address = clientData.getServerAddress().split(":")[0];
            } else {
                address = intentionPacket.getHostname();
            }

            event.setPacket(intentionPacket.withHostname(address + addressSuffix));
        }
    }

    @Override
    public void connected(ConnectedEvent event) {
        geyserSession.loggingIn = false;
        geyserSession.loggedIn = true;

        if (geyserSession.getDownstream().getSession() instanceof LocalSession) {
            // Connected directly to the server
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect_internal",
                geyserSession.bedrockUsername(), geyserSession.getProtocol().getProfile().getName()));
        } else {
            // Connected to an IP address
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect",
                geyserSession.bedrockUsername(), geyserSession.getProtocol().getProfile().getName(), geyserSession.remoteServer().address()));
        }

        UUID uuid = geyserSession.getProtocol().getProfile().getId();
        if (uuid == null) {
            // Set what our UUID *probably* is going to be
            if (geyserSession.remoteServer().authType() == AuthType.FLOODGATE) {
                uuid = new UUID(0, Long.parseLong(geyserSession.xuid()));
            } else {
                uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + geyserSession.getProtocol().getProfile().getName()).getBytes(StandardCharsets.UTF_8));
            }
        }
        geyserSession.getPlayerEntity().setUuid(uuid);
        geyserSession.getPlayerEntity().setUsername(geyserSession.getProtocol().getProfile().getName());

        String locale = geyserSession.getClientData().getLanguageCode();

        // Let the user know there locale may take some time to download
        // as it has to be extracted from a JAR
        if (locale.equalsIgnoreCase("en_us") && !MinecraftLocale.LOCALE_MAPPINGS.containsKey("en_us")) {
            // This should probably be left hardcoded as it will only show for en_us clients
            geyserSession.sendMessage("Loading your locale (en_us); if this isn't already downloaded, this may take some time");
        }

        // Download and load the language for the player
        MinecraftLocale.downloadAndLoadLocale(locale);
    }

    @Override
    public void disconnected(DisconnectedEvent event) {
        geyserSession.loggingIn = false;

        String disconnectMessage, customDisconnectMessage = null;
        Throwable cause = event.getCause();
        if (cause instanceof UnexpectedEncryptionException) {
            if (geyserSession.remoteServer().authType() != AuthType.FLOODGATE) {
                // Server expects online mode
                customDisconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.authentication_type_mismatch", locale);
                // Explain that they may be looking for Floodgate.
                geyser.getLogger().warning(GeyserLocale.getLocaleStringLog(
                    geyser.getPlatformType() == PlatformType.STANDALONE ?
                        "geyser.network.remote.floodgate_explanation_standalone"
                        : "geyser.network.remote.floodgate_explanation_plugin",
                    Constants.FLOODGATE_DOWNLOAD_LOCATION
                ));
            } else {
                // Likely that Floodgate is not configured correctly.
                customDisconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.floodgate_login_error", locale);
                if (geyser.getPlatformType() == PlatformType.STANDALONE) {
                    geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.remote.floodgate_login_error_standalone"));
                }
            }
        } else if (cause instanceof ConnectException) {
            // Server is offline, probably
            customDisconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.server_offline", locale);
        }

        // Use our helpful disconnect message whenever possible
        disconnectMessage = customDisconnectMessage != null ? customDisconnectMessage : MessageTranslator.convertMessage(event.getReason());;

        if (geyserSession.getDownstream().getSession() instanceof LocalSession) {
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect_internal", geyserSession.bedrockUsername(), disconnectMessage));
        } else {
            geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect", geyserSession.bedrockUsername(), geyserSession.remoteServer().address(), disconnectMessage));
        }
        if (cause != null) {
            if (cause.getMessage() != null) {
                GeyserImpl.getInstance().getLogger().error(cause.getMessage());
            } else {
                GeyserImpl.getInstance().getLogger().error("An exception occurred: ", cause);
            }
            if (geyser.getConfig().isDebugMode()) {
                cause.printStackTrace();
            }
        }
        if ((!geyserSession.isClosed() && geyserSession.loggedIn) || cause != null) {
            // GeyserSession is disconnected via session.disconnect() called indirectly be the server
            // This needs to be "initiated" here when there is an exception, but also when the Netty connection
            // is closed without a disconnect packet - in this case, closed will still be false, but loggedIn
            // will also be true as GeyserSession#disconnect will not have been called.
            if (customDisconnectMessage != null) {
                geyserSession.disconnect(customDisconnectMessage);
            } else {
                geyserSession.disconnect(event.getReason());
            }
        }

        geyserSession.loggedIn = false;
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        Registries.JAVA_PACKET_TRANSLATORS.translate(packet.getClass(), packet, geyserSession, true);
    }

    @Override
    public void packetError(PacketErrorEvent event) {
        geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.downstream_error",
            (event.getPacketClass() != null ? "(" + event.getPacketClass().getSimpleName() + ") " : "") +
                event.getCause().getMessage())
        );
        if (geyser.getConfig().isDebugMode())
            event.getCause().printStackTrace();
        event.setSuppress(true);
    }
}
