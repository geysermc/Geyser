/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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
package org.geysermc.geyser.platform.viaproxy;

import io.netty.channel.AbstractChannel;
import net.lenni0451.lambdaevents.EventHandler;
import net.lenni0451.reflect.stream.RStream;
import net.raphimc.vialegacy.api.LegacyProtocolVersion;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.PluginManager;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.Client2ProxyChannelInitializeEvent;
import net.raphimc.viaproxy.plugins.events.ConsoleCommandEvent;
import net.raphimc.viaproxy.plugins.events.ProxyStartEvent;
import net.raphimc.viaproxy.plugins.events.ProxyStopEvent;
import net.raphimc.viaproxy.plugins.events.ShouldVerifyOnlineModeEvent;
import net.raphimc.viaproxy.plugins.events.types.ITyped;
import net.raphimc.viaproxy.protocoltranslator.viaproxy.ViaProxyConfig;
import org.apache.logging.log4j.LogManager;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.command.standalone.StandaloneCloudCommandManager;
import org.geysermc.geyser.configuration.ConfigLoader;
import org.geysermc.geyser.configuration.GeyserPluginConfig;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.ping.GeyserLegacyPingPassthrough;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;
import org.geysermc.geyser.platform.viaproxy.listener.GeyserServerTransferListener;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoopbackUtil;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class GeyserViaProxyPlugin extends ViaProxyPlugin implements GeyserBootstrap, EventRegistrar {

    private static final File ROOT_FOLDER = new File(PluginManager.PLUGINS_DIR, "Geyser");

    private final GeyserViaProxyLogger logger = new GeyserViaProxyLogger(LogManager.getLogger("Geyser"));
    private GeyserPluginConfig config;
    private GeyserImpl geyser;
    private StandaloneCloudCommandManager cloud;
    private CommandRegistry commandRegistry;
    private IGeyserPingPassthrough pingPassthrough;

    @Override
    public void onEnable() {
        ROOT_FOLDER.mkdirs();

        GeyserLocale.init(this);
        this.onGeyserInitialize();

        ViaProxy.EVENT_MANAGER.register(this);
    }

    @Override
    public void onDisable() {
        this.onGeyserShutdown();
    }

    @EventHandler
    private void onConsoleCommand(final ConsoleCommandEvent event) {
        final String command = event.getCommand().startsWith("/") ? event.getCommand().substring(1) : event.getCommand();
        CommandRegistry registry = this.getCommandRegistry();
        if (registry.rootCommands().contains(command)) {
            registry.runCommand(this.getGeyserLogger(), command + " " + String.join(" ", event.getArgs()));
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onShouldVerifyOnlineModeEvent(final ShouldVerifyOnlineModeEvent event) {
        final UUID uuid = event.getProxyConnection().getGameProfile().getId();
        if (uuid == null) return;

        final GeyserSession connection = GeyserImpl.getInstance().onlineConnections().stream().filter(s -> s.javaUuid().equals(uuid)).findAny().orElse(null);
        if (connection == null) return;

        if (connection.javaUsername().equals(event.getProxyConnection().getGameProfile().getName())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onClient2ProxyChannelInitialize(Client2ProxyChannelInitializeEvent event) {
        if (event.getType() != ITyped.Type.POST || event.isLegacyPassthrough()) {
            return;
        }
        if (System.getProperty("geyser.viaproxy.disableIpPassthrough") != null) { // Temporary until Configurate branch is merged
            return;
        }

        final GeyserSession session = GeyserImpl.getInstance().onlineConnections().stream()
            .filter(c -> c.getDownstream() != null)
            .filter(c -> c.getDownstream().getSession().getLocalAddress().equals(event.getChannel().remoteAddress()))
            .findAny().orElse(null);
        if (session != null) {
            final SocketAddress realAddress = session.getSocketAddress();
            if (event.getChannel() instanceof AbstractChannel) {
                RStream.of(AbstractChannel.class, event.getChannel()).fields().by("remoteAddress").set(realAddress);
            }
        }
    }

    @EventHandler
    private void onProxyStart(final ProxyStartEvent event) {
        this.onGeyserEnable();
    }

    @EventHandler
    private void onProxyStop(final ProxyStopEvent event) {
        this.onGeyserDisable();
    }

    @Override
    public void onGeyserInitialize() {
        if (!this.loadConfig()) {
            return;
        }

        this.geyser = GeyserImpl.load(this);
        this.geyser.eventBus().register(this, new GeyserServerTransferListener());
        LoopbackUtil.checkAndApplyLoopback(this.logger);
    }

    @Override
    public void onGeyserEnable() {
        // If e.g. the config failed to load, GeyserImpl was not loaded and we cannot start
        if (geyser == null) {
            return;
        }
        boolean reloading = geyser.isReloading();
        if (reloading) {
            if (!this.loadConfig()) {
                return;
            }
        } else {
            // Only initialized once - documented in the Geyser-Standalone bootstrap
            this.cloud = new StandaloneCloudCommandManager(geyser);
            this.commandRegistry = new CommandRegistry(geyser, cloud);
        }

        GeyserImpl.start();

        if (!reloading) {
            // Event must be fired after CommandRegistry has subscribed its listener.
            // Also, the subscription for the Permissions class is created when Geyser is initialized (by GeyserImpl#start)
            this.cloud.fireRegisterPermissionsEvent();
        }

        if (ViaProxy.getConfig().getTargetVersion() != null && ViaProxy.getConfig().getTargetVersion().newerThanOrEqualTo(LegacyProtocolVersion.b1_8tob1_8_1)) {
            // Only initialize the ping passthrough if the protocol version is above beta 1.7.3, as that's when the status protocol was added
            this.pingPassthrough = GeyserLegacyPingPassthrough.init(this.geyser);
        }
        if (this.config.java().authType() == AuthType.FLOODGATE) {
            ViaProxy.getConfig().setPassthroughBungeecordPlayerInfo(true);
        }
    }

    @Override
    public void onGeyserDisable() {
        this.geyser.disable();
    }

    @Override
    public void onGeyserShutdown() {
        this.geyser.shutdown();
    }

    @Override
    public PlatformType platformType() {
        return PlatformType.VIAPROXY;
    }

    @Override
    public GeyserPluginConfig config() {
        return this.config;
    }

    @Override
    public GeyserLogger getGeyserLogger() {
        return this.logger;
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return this.commandRegistry;
    }

    @Override
    public IGeyserPingPassthrough getGeyserPingPassthrough() {
        return this.pingPassthrough;
    }

    @Override
    public Path getConfigFolder() {
        return ROOT_FOLDER.toPath();
    }

    @Override
    public BootstrapDumpInfo getDumpInfo() {
        return new GeyserViaProxyDumpInfo();
    }

    @NonNull
    @Override
    public String getServerBindAddress() {
        if (ViaProxy.getConfig().getBindAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getHostString();
        } else {
            throw new IllegalStateException("Unsupported bind address type: " + ViaProxy.getConfig().getBindAddress().getClass().getName());
        }
    }

    @Override
    public int getServerPort() {
        if (ViaProxy.getConfig().getBindAddress() instanceof InetSocketAddress socketAddress) {
            return socketAddress.getPort();
        } else {
            throw new IllegalStateException("Unsupported bind address type: " + ViaProxy.getConfig().getBindAddress().getClass().getName());
        }
    }

    @Override
    public boolean testFloodgatePluginPresent() {
        return false;
    }

    @Override
    public Path getFloodgateKeyPath() {
        return new File(ROOT_FOLDER, config.advanced().floodgateKeyFile()).toPath();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean loadConfig() {
        this.config = new ConfigLoader(this)
            .transformer(node -> {
                try {
                    if (!ViaProxy.getConfig().getWildcardDomainHandling().equals(ViaProxyConfig.WildcardDomainHandling.NONE)) {
                        node.node("java", "forward-host").set(true);
                    }

                    var pingPassthroughInterval = node.node("ping-passthrough-interval");
                    int interval = pingPassthroughInterval.getInt();
                    if (interval < 15 && ViaProxy.getConfig().getTargetVersion() != null && ViaProxy.getConfig().getTargetVersion().olderThanOrEqualTo(LegacyProtocolVersion.r1_6_4)) {
                        // <= 1.6.4 servers sometimes block incoming connections from an IP address if too many connections are made
                        pingPassthroughInterval.set(15);
                    }
                } catch (SerializationException e) {
                    throw new RuntimeException(e);
                }
            })
            .configFile(new File(ROOT_FOLDER, "config.yml"))
            .load(GeyserPluginConfig.class);
        if (this.config == null) {
            return false;
        }
        this.config.java().authType(Files.isRegularFile(getFloodgateKeyPath()) ? AuthType.FLOODGATE : AuthType.OFFLINE);
        return true;
    }

}
