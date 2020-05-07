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

package org.geysermc.connector;

import com.nukkitx.protocol.bedrock.BedrockPacketCodec;
import com.nukkitx.protocol.bedrock.BedrockServer;
import com.nukkitx.protocol.bedrock.v390.Bedrock_v390;
import lombok.Getter;
import org.geysermc.common.AuthType;
import org.geysermc.common.PlatformType;
import org.geysermc.connector.bootstrap.GeyserBootstrap;
import org.geysermc.connector.command.CommandManager;
import org.geysermc.connector.metrics.Metrics;
import org.geysermc.connector.network.ConnectorServerEventHandler;
import org.geysermc.connector.network.remote.RemoteServer;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.Translators;
import org.geysermc.connector.network.translators.world.WorldManager;
import org.geysermc.connector.thread.PingPassthroughThread;
import org.geysermc.connector.utils.Toolbox;
import org.geysermc.connector.utils.TranslationUtils;

import java.net.InetSocketAddress;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
public class GeyserConnector {

    public static final BedrockPacketCodec BEDROCK_PACKET_CODEC = Bedrock_v390.V390_CODEC;

    public static final String NAME = "Geyser";
    public static final String VERSION = "DEV"; // A fallback for running in IDEs

    private final Map<InetSocketAddress, GeyserSession> players = new HashMap<>();

    private static GeyserConnector instance;

    private RemoteServer remoteServer;
    private AuthType authType;

    private boolean shuttingDown = false;

    private final ScheduledExecutorService generalThreadPool;
    private PingPassthroughThread passthroughThread;

    private BedrockServer bedrockServer;
    private PlatformType platformType;
    private GeyserBootstrap bootstrap;

    private Metrics metrics;

    private GeyserConnector(PlatformType platformType, GeyserBootstrap bootstrap) {
        long startupTime = System.currentTimeMillis();

        instance = this;

        this.bootstrap = bootstrap;

        GeyserLogger logger = bootstrap.getGeyserLogger();
        GeyserConfiguration config = bootstrap.getGeyserConfig();

        this.platformType = platformType;

        logger.info("******************************************");
        logger.info("");
        logger.info(TranslationUtils.getLocaleStringLog("geyser.core.load", NAME, VERSION));
        logger.info("");
        logger.info("******************************************");

        this.generalThreadPool = Executors.newScheduledThreadPool(config.getGeneralThreadPool());

        logger.setDebug(config.isDebugMode());

        Toolbox.init();
        Translators.start();

        remoteServer = new RemoteServer(config.getRemote().getAddress(), config.getRemote().getPort());
        authType = AuthType.getByName(config.getRemote().getAuthType());

        passthroughThread = new PingPassthroughThread(this);
        if (config.isPingPassthrough())
            generalThreadPool.scheduleAtFixedRate(passthroughThread, 1, 1, TimeUnit.SECONDS);

        bedrockServer = new BedrockServer(new InetSocketAddress(config.getBedrock().getAddress(), config.getBedrock().getPort()));
        bedrockServer.setHandler(new ConnectorServerEventHandler(this));
        bedrockServer.bind().whenComplete((avoid, throwable) -> {
            if (throwable == null) {
                logger.info(TranslationUtils.getLocaleStringLog("geyser.core.start", config.getBedrock().getAddress(), config.getBedrock().getPort()));
            } else {
                logger.severe(TranslationUtils.getLocaleStringLog("geyser.core.fail", config.getBedrock().getAddress(), config.getBedrock().getPort()));
                throwable.printStackTrace();
            }
        }).join();

        if (config.getMetrics().isEnabled()) {
            metrics = new Metrics(this, "GeyserMC", config.getMetrics().getUniqueId(), false, java.util.logging.Logger.getLogger(""));
            metrics.addCustomChart(new Metrics.SingleLineChart("servers", () -> 1));
            metrics.addCustomChart(new Metrics.SingleLineChart("players", players::size));
            metrics.addCustomChart(new Metrics.SimplePie("authMode", authType.name()::toLowerCase));
            metrics.addCustomChart(new Metrics.SimplePie("platform", platformType::getPlatformName));
        }

        double completeTime = (System.currentTimeMillis() - startupTime) / 1000D;
        logger.info(TranslationUtils.getLocaleStringLog("geyser.core.done", new DecimalFormat("#.###").format(completeTime)));
    }

    public void shutdown() {
        bootstrap.getGeyserLogger().info(TranslationUtils.getLocaleStringLog("geyser.core.shutdown"));
        shuttingDown = true;

        if (players.size() >= 1) {
            bootstrap.getGeyserLogger().info(TranslationUtils.getLocaleStringLog("geyser.core.shutdown.kick.log", players.size()));

            for (GeyserSession playerSession : players.values()) {
                playerSession.disconnect(TranslationUtils.getLocaleStringLog("geyser.core.shutdown.kick.message"));
            }

            CompletableFuture<Void> future = CompletableFuture.runAsync(new Runnable() {
                @Override
                public void run() {
                    // Simulate a long-running Job
                    try {
                        while (true) {
                            if (players.size() == 0) {
                                return;
                            }

                            TimeUnit.MILLISECONDS.sleep(100);
                        }
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                }
            });

            // Block and wait for the future to complete
            try {
                future.get();
                bootstrap.getGeyserLogger().info(TranslationUtils.getLocaleStringLog("geyser.core.shutdown.kick.done"));
            } catch (Exception e) {
                // Quietly fail
            }
        }

        generalThreadPool.shutdown();
        bedrockServer.close();
        players.clear();
        remoteServer = null;
        authType = null;
        this.getCommandManager().getCommands().clear();

        bootstrap.getGeyserLogger().info(TranslationUtils.getLocaleStringLog("geyser.core.shutdown.done"));
    }

    public void addPlayer(GeyserSession player) {
        players.put(player.getSocketAddress(), player);
    }

    public void removePlayer(GeyserSession player) {
        players.remove(player.getSocketAddress());
    }

    public static GeyserConnector start(PlatformType platformType, GeyserBootstrap bootstrap) {
        return new GeyserConnector(platformType, bootstrap);
    }

    public void reload() {
        shutdown();
        bootstrap.onEnable();
    }

    public GeyserLogger getLogger() {
        return bootstrap.getGeyserLogger();
    }

    public GeyserConfiguration getConfig() {
        return bootstrap.getGeyserConfig();
    }

    public CommandManager getCommandManager() {
        return bootstrap.getGeyserCommandManager();
    }

    public WorldManager getWorldManager() {
        return bootstrap.getWorldManager();
    }

    public static GeyserConnector getInstance() {
        return instance;
    }
}
