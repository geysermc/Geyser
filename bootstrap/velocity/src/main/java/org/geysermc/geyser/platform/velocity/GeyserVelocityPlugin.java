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

package org.geysermc.geyser.platform.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ListenerBoundEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.network.ListenerType;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.FloodgateKeyLoader;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.command.CommandSourceConverter;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.ConfigLoader;
import org.geysermc.geyser.configuration.GeyserPluginConfig;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.ping.GeyserLegacyPingPassthrough;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;
import org.geysermc.geyser.platform.velocity.command.VelocityCommandSource;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.metrics.MetricsPlatform;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.velocity.VelocityCommandManager;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Plugin(id = "geyser", name = GeyserImpl.NAME + "-Velocity", version = GeyserImpl.VERSION, url = "https://geysermc.org", authors = "GeyserMC")
public class GeyserVelocityPlugin implements GeyserBootstrap {

    private final ProxyServer proxyServer;
    private final PluginContainer container;
    private final GeyserVelocityLogger geyserLogger;
    private GeyserPluginConfig geyserConfig;
    private GeyserVelocityInjector geyserInjector;
    private IGeyserPingPassthrough geyserPingPassthrough;
    private CommandRegistry commandRegistry;
    private GeyserImpl geyser;

    @Getter
    private final Path configFolder = Paths.get("plugins/" + GeyserImpl.NAME + "-Velocity/");

    @Inject
    public GeyserVelocityPlugin(ProxyServer server, PluginContainer container, Logger logger) {
        this.proxyServer = server;
        this.container = container;
        this.geyserLogger = new GeyserVelocityLogger(logger);
    }

    @Override
    public void onGeyserInitialize() {
        GeyserLocale.init(this);

        if (!ProtocolVersion.isSupported(GameProtocol.getJavaProtocolVersion())) {
            geyserLogger.error("      / \\");
            geyserLogger.error("     /   \\");
            geyserLogger.error("    /  |  \\");
            geyserLogger.error("   /   |   \\    " + GeyserLocale.getLocaleStringLog("geyser.bootstrap.unsupported_proxy", proxyServer.getVersion().getName()));
            geyserLogger.error("  /         \\   " + GeyserLocale.getLocaleStringLog("geyser.may_not_work_as_intended_all_caps"));
            geyserLogger.error(" /     o     \\");
            geyserLogger.error("/_____________\\");
        }

        if (!loadConfig()) {
            return;
        }

        this.geyser = GeyserImpl.load(this);
        this.geyserInjector = new GeyserVelocityInjector(proxyServer);

        // We need to register commands here, rather than in onGeyserEnable which is invoked during the appropriate ListenerBoundEvent.
        // Reason: players can connect after a listener is bound, and a player join locks registration to the cloud CommandManager.
        var sourceConverter = new CommandSourceConverter<>(
            CommandSource.class,
            id -> proxyServer.getPlayer(id).orElse(null),
            proxyServer::getConsoleCommandSource,
            VelocityCommandSource::new
        );
        CommandManager<GeyserCommandSource> cloud = new VelocityCommandManager<>(
            container,
            proxyServer,
            ExecutionCoordinator.simpleCoordinator(),
            sourceConverter
        );
        this.commandRegistry = new CommandRegistry(geyser, cloud, false); // applying root permission would be a breaking change because we can't register permission defaults
    }

    @Override
    public void onGeyserEnable() {
        // If e.g. the config failed to load, GeyserImpl was not loaded and we cannot start
        if (geyser == null) {
            return;
        }
        if (GeyserImpl.getInstance().isReloading()) {
            if (!loadConfig()) {
                return;
            }
        }

        GeyserImpl.start();

        if (!geyserConfig.integratedPingPassthrough()) {
            this.geyserPingPassthrough = GeyserLegacyPingPassthrough.init(geyser);
        } else {
            this.geyserPingPassthrough = new GeyserVelocityPingPassthrough(proxyServer);
        }

        // No need to re-register events
        if (!GeyserImpl.getInstance().isReloading()) {
            proxyServer.getEventManager().register(this, new GeyserVelocityUpdateListener());
        }
    }

    @Override
    public void onGeyserDisable() {
        if (geyser != null) {
            geyser.disable();
        }
    }

    @Override
    public void onGeyserShutdown() {
        if (geyser != null) {
            geyser.shutdown();
        }
        if (geyserInjector != null) {
            geyserInjector.shutdown();
        }
    }

    @Override
    public PlatformType platformType() {
        return PlatformType.VELOCITY;
    }

    @Override
    public GeyserPluginConfig config() {
        return geyserConfig;
    }

    @Override
    public GeyserVelocityLogger getGeyserLogger() {
        return geyserLogger;
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return this.commandRegistry;
    }

    @Override
    public IGeyserPingPassthrough getGeyserPingPassthrough() {
        return geyserPingPassthrough;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) {
        this.onGeyserInitialize();
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        this.onGeyserShutdown();
    }

    @Subscribe
    public void onProxyBound(ListenerBoundEvent event) {
        if (event.getListenerType() == ListenerType.MINECRAFT) {
            // Once listener is bound, do our startup process
            this.onGeyserEnable();

            if (geyserInjector != null) {
                // After this bound, we know that the channel initializer cannot change without it being ineffective for Velocity, too
                geyserInjector.initializeLocalChannel(this);
            }
        }
    }

    @Override
    public BootstrapDumpInfo getDumpInfo() {
        return new GeyserVelocityDumpInfo(proxyServer);
    }

    @Nullable
    @Override
    public SocketAddress getSocketAddress() {
        return this.geyserInjector.getServerSocketAddress();
    }

    @NonNull
    @Override
    public String getServerBindAddress() {
        return proxyServer.getBoundAddress().getHostString();
    }

    @Override
    public int getServerPort() {
        return proxyServer.getBoundAddress().getPort();
    }

    @Override
    public boolean testFloodgatePluginPresent() {
        var floodgate = proxyServer.getPluginManager().getPlugin("floodgate");
        return floodgate.isPresent();
    }

    @Override
    public Path getFloodgateKeyPath() {
        Optional<PluginContainer> floodgate = proxyServer.getPluginManager().getPlugin("floodgate");
        Path floodgateDataPath = floodgate.isPresent() ? Paths.get("plugins/floodgate/") : null;
        return FloodgateKeyLoader.getKeyPath(geyserConfig, floodgateDataPath, configFolder, geyserLogger);
    }

    @Override
    public MetricsPlatform createMetricsPlatform() {
        try {
            return new VelocityMetrics(this.configFolder);
        } catch (IOException e) {
            this.geyserLogger.debug("Integrated bStats support failed to load.");
            if (this.config().debugMode()) {
                e.printStackTrace();
            }
            return null;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean loadConfig() {
        this.geyserConfig = new ConfigLoader(this).createFolder().load(GeyserPluginConfig.class);
        return this.geyserConfig != null;
    }
}
