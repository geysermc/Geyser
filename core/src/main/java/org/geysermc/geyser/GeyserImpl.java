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

package org.geysermc.geyser;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.netty.channel.epoll.Epoll;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import net.raphimc.minecraftauth.step.msa.StepMsaToken;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.api.Geyser;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.erosion.packet.Packets;
import org.geysermc.floodgate.crypto.AesCipher;
import org.geysermc.floodgate.crypto.AesKeyProducer;
import org.geysermc.floodgate.crypto.Base64Topping;
import org.geysermc.floodgate.crypto.FloodgateCipher;
import org.geysermc.floodgate.news.NewsItemAction;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.event.EventBus;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreReloadEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserRegisterPermissionsEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserShutdownEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.BedrockListener;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.api.util.MinecraftVersion;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.erosion.UnixSocketClientListener;
import org.geysermc.geyser.event.GeyserEventBus;
import org.geysermc.geyser.extension.GeyserExtensionManager;
import org.geysermc.geyser.impl.MinecraftVersionImpl;
import org.geysermc.geyser.level.WorldManager;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.GeyserServer;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.provider.ProviderSupplier;
import org.geysermc.geyser.scoreboard.ScoreboardUpdater;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.PendingMicrosoftAuthentication;
import org.geysermc.geyser.session.SessionManager;
import org.geysermc.geyser.session.cache.RegistryCache;
import org.geysermc.geyser.skin.FloodgateSkinUploader;
import org.geysermc.geyser.skin.ProvidedSkins;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.AssetUtils;
import org.geysermc.geyser.util.CooldownUtils;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.geyser.util.Metrics;
import org.geysermc.geyser.util.MinecraftAuthLogger;
import org.geysermc.geyser.util.NewsHandler;
import org.geysermc.geyser.util.VersionCheckUtils;
import org.geysermc.geyser.util.WebUtils;
import org.geysermc.mcprotocollib.network.tcp.TcpSession;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.security.Key;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class GeyserImpl implements GeyserApi, EventRegistrar {
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.IGNORE_UNDEFINED)
            .enable(JsonParser.Feature.ALLOW_COMMENTS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES)
            .enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);

    public static final String NAME = "Geyser";
    public static final String GIT_VERSION = BuildData.GIT_VERSION;
    public static final String VERSION = BuildData.VERSION;

    public static final String BUILD_NUMBER = BuildData.BUILD_NUMBER;
    public static final String BRANCH = BuildData.BRANCH;
    public static final String COMMIT = BuildData.COMMIT;
    public static final String REPOSITORY = BuildData.REPOSITORY;
    public static final boolean IS_DEV = BuildData.isDevBuild();

    /**
     * Oauth client ID for Microsoft authentication
     */
    public static final String OAUTH_CLIENT_ID = "204cefd1-4818-4de1-b98d-513fae875d88";

    private static final String IP_REGEX = "\\b\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\b";

    private final SessionManager sessionManager = new SessionManager();

    private FloodgateCipher cipher;
    private FloodgateSkinUploader skinUploader;
    private NewsHandler newsHandler;

    private UnixSocketClientListener erosionUnixListener;

    @Setter
    private volatile boolean shuttingDown = false;

    private ScheduledExecutorService scheduledThread;

    private GeyserServer geyserServer;
    private final PlatformType platformType;
    private final GeyserBootstrap bootstrap;

    private final EventBus<EventRegistrar> eventBus;
    private final GeyserExtensionManager extensionManager;

    private Metrics metrics;

    private PendingMicrosoftAuthentication pendingMicrosoftAuthentication;
    @Getter(AccessLevel.NONE)
    private Map<String, String> savedAuthChains;

    @Getter
    private static GeyserImpl instance;

    /**
     * Determines if we're currently reloading. Replaces per-bootstrap reload checks
     */
    private volatile boolean isReloading;

    /**
     * Determines if Geyser is currently enabled. This is used to determine if {@link #disable()} should be called during {@link #shutdown()}.
     */
    @Setter
    private boolean isEnabled;

    private GeyserImpl(PlatformType platformType, GeyserBootstrap bootstrap) {
        instance = this;

        Geyser.set(this);

        this.platformType = platformType;
        this.bootstrap = bootstrap;

        /* Initialize event bus */
        this.eventBus = new GeyserEventBus();

        /* Create Extension Manager */
        this.extensionManager = new GeyserExtensionManager();

        /* Finalize locale loading now that we know the default locale from the config */
        GeyserLocale.finalizeDefaultLocale(this);

        /* Load Extensions */
        this.extensionManager.init();
        this.eventBus.fire(new GeyserPreInitializeEvent(this.extensionManager, this.eventBus));
    }

    public void initialize() {
        long startupTime = System.currentTimeMillis();

        GeyserLogger logger = bootstrap.getGeyserLogger();

        logger.info("******************************************");
        logger.info("");
        logger.info(GeyserLocale.getLocaleStringLog("geyser.core.load", NAME, VERSION));
        logger.info("");
        if (IS_DEV) {
            logger.info(GeyserLocale.getLocaleStringLog("geyser.core.dev_build", "https://discord.gg/geysermc"));
            logger.info("");
        }
        logger.info("******************************************");

        /* Initialize registries */
        Registries.init();
        BlockRegistries.init();

        RegistryCache.init();

        /* Initialize translators */
        EntityDefinitions.init();
        MessageTranslator.init();

        // Download the latest asset list and cache it
        AssetUtils.generateAssetCache().whenComplete((aVoid, ex) -> {
            if (ex != null) {
                return;
            }

            MinecraftLocale.ensureEN_US();
            String locale = GeyserLocale.getDefaultLocale();
            if (!"en_us".equals(locale)) {
                // English will be loaded after assets are downloaded, if necessary
                MinecraftLocale.downloadAndLoadLocale(locale);
            }

            ProvidedSkins.init();

            CompletableFuture.runAsync(AssetUtils::downloadAndRunClientJarTasks);
        });

        // Register our general permissions when possible
        eventBus.subscribe(this, GeyserRegisterPermissionsEvent.class, Permissions::register);

        startInstance();

        GeyserConfiguration config = bootstrap.getGeyserConfig();

        double completeTime = (System.currentTimeMillis() - startupTime) / 1000D;
        String message = GeyserLocale.getLocaleStringLog("geyser.core.finish.done", new DecimalFormat("#.###").format(completeTime));
        message += " " + GeyserLocale.getLocaleStringLog("geyser.core.finish.console");
        logger.info(message);

        if (platformType == PlatformType.STANDALONE) {
            if (config.getRemote().authType() != AuthType.FLOODGATE) {
                // If the auth-type is Floodgate, then this Geyser instance is probably owned by the Java server
                logger.warning(GeyserLocale.getLocaleStringLog("geyser.core.movement_warn"));
            }
        } else if (config.getRemote().authType() == AuthType.FLOODGATE) {
            VersionCheckUtils.checkForOutdatedFloodgate(logger);
        }

        VersionCheckUtils.checkForOutdatedJava(logger);
    }

    private void startInstance() {
        this.scheduledThread = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("Geyser Scheduled Thread"));

        if (isReloading) {
            // If we're reloading, the default locale in the config might have changed.
            GeyserLocale.finalizeDefaultLocale(this);
        }
        GeyserLogger logger = bootstrap.getGeyserLogger();
        GeyserConfiguration config = bootstrap.getGeyserConfig();

        ScoreboardUpdater.init();

        SkinProvider.registerCacheImageTask(this);

        Registries.RESOURCE_PACKS.load();

        String geyserUdpPort = System.getProperty("geyserUdpPort", "");
        String pluginUdpPort = geyserUdpPort.isEmpty() ? System.getProperty("pluginUdpPort", "") : geyserUdpPort;
        if ("-1".equals(pluginUdpPort)) {
            throw new UnsupportedOperationException("This hosting/service provider does not support applications running on the UDP port");
        }
        boolean portPropertyApplied = false;
        String pluginUdpAddress = System.getProperty("geyserUdpAddress", System.getProperty("pluginUdpAddress", ""));

        if (platformType != PlatformType.STANDALONE) {
            int javaPort = bootstrap.getServerPort();
            if (config.getRemote().address().equals("auto")) {
                config.setAutoconfiguredRemote(true);
                String serverAddress = bootstrap.getServerBindAddress();
                if (!serverAddress.isEmpty() && !"0.0.0.0".equals(serverAddress)) {
                    config.getRemote().setAddress(serverAddress);
                } else {
                    // Set the remote address to localhost since that is where we are always connecting
                    try {
                        config.getRemote().setAddress(InetAddress.getLocalHost().getHostAddress());
                    } catch (UnknownHostException ex) {
                        logger.debug("Unknown host when trying to find localhost.");
                        if (config.isDebugMode()) {
                            ex.printStackTrace();
                        }
                        config.getRemote().setAddress(InetAddress.getLoopbackAddress().getHostAddress());
                    }
                }
                if (javaPort != -1) {
                    config.getRemote().setPort(javaPort);
                }
            }

            boolean forceMatchServerPort = "server".equals(pluginUdpPort);
            if ((config.getBedrock().isCloneRemotePort() || forceMatchServerPort) && javaPort != -1) {
                config.getBedrock().setPort(javaPort);
                if (forceMatchServerPort) {
                    if (geyserUdpPort.isEmpty()) {
                        logger.info("Port set from system generic property to match Java server.");
                    } else {
                        logger.info("Port set from system property to match Java server.");
                    }
                    portPropertyApplied = true;
                }
            }

            if ("server".equals(pluginUdpAddress)) {
                String address = bootstrap.getServerBindAddress();
                if (!address.isEmpty()) {
                    config.getBedrock().setAddress(address);
                }
            } else if (!pluginUdpAddress.isEmpty()) {
                config.getBedrock().setAddress(pluginUdpAddress);
            }

            if (!portPropertyApplied && !pluginUdpPort.isEmpty()) {
                int port = Integer.parseInt(pluginUdpPort);
                config.getBedrock().setPort(port);
                if (geyserUdpPort.isEmpty()) {
                    logger.info("Port set from generic system property: " + port);
                } else {
                    logger.info("Port set from system property: " + port);
                }
            }

            String broadcastPort = System.getProperty("geyserBroadcastPort", "");
            if (!broadcastPort.isEmpty()) {
                int parsedPort;
                try {
                    parsedPort = Integer.parseInt(broadcastPort);
                    if (parsedPort < 1 || parsedPort > 65535) {
                        throw new NumberFormatException("The broadcast port must be between 1 and 65535 inclusive!");
                    }
                } catch (NumberFormatException e) {
                    logger.error(String.format("Invalid broadcast port: %s! Defaulting to configured port.", broadcastPort + " (" + e.getMessage() + ")"));
                    parsedPort = config.getBedrock().port();
                }
                config.getBedrock().setBroadcastPort(parsedPort);
                logger.info("Broadcast port set from system property: " + parsedPort);
            }

            if (platformType != PlatformType.VIAPROXY) {
                boolean floodgatePresent = bootstrap.testFloodgatePluginPresent();
                if (config.getRemote().authType() == AuthType.FLOODGATE && !floodgatePresent) {
                    logger.severe(GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.not_installed") + " "
                            + GeyserLocale.getLocaleStringLog("geyser.bootstrap.floodgate.disabling"));
                    return;
                } else if (config.isAutoconfiguredRemote() && floodgatePresent) {
                    // Floodgate installed means that the user wants Floodgate authentication
                    logger.debug("Auto-setting to Floodgate authentication.");
                    config.getRemote().setAuthType(AuthType.FLOODGATE);
                }
            }
        }

        String remoteAddress = config.getRemote().address();
        // Filters whether it is not an IP address or localhost, because otherwise it is not possible to find out an SRV entry.
        if (!remoteAddress.matches(IP_REGEX) && !remoteAddress.equalsIgnoreCase("localhost")) {
            String[] record = WebUtils.findSrvRecord(this, remoteAddress);
            if (record != null) {
                int remotePort = Integer.parseInt(record[2]);
                config.getRemote().setAddress(remoteAddress = record[3]);
                config.getRemote().setPort(remotePort);
                logger.debug("Found SRV record \"" + remoteAddress + ":" + remotePort + "\"");
            }
        }

        // Ensure that PacketLib does not create an event loop for handling packets; we'll do that ourselves
        TcpSession.USE_EVENT_LOOP_FOR_PACKETS = false;

        pendingMicrosoftAuthentication = new PendingMicrosoftAuthentication(config.getPendingAuthenticationTimeout());

        this.newsHandler = new NewsHandler(BRANCH, this.buildNumber());

        Packets.initGeyser();

        if (Epoll.isAvailable()) {
            this.erosionUnixListener = new UnixSocketClientListener();
        } else {
            logger.debug("Epoll is not available; Erosion's Unix socket handling will not work.");
        }

        CooldownUtils.setDefaultShowCooldown(config.getShowCooldown());
        DimensionUtils.changeBedrockNetherId(config.isAboveBedrockNetherBuilding()); // Apply End dimension ID workaround to Nether

        Integer bedrockThreadCount = Integer.getInteger("Geyser.BedrockNetworkThreads");
        if (bedrockThreadCount == null) {
            // Copy the code from Netty's default thread count fallback
            bedrockThreadCount = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        }

        this.geyserServer = new GeyserServer(this, bedrockThreadCount);
        this.geyserServer.bind(new InetSocketAddress(config.getBedrock().address(), config.getBedrock().port()))
            .whenComplete((avoid, throwable) -> {
                String address = config.getBedrock().address();
                String port = String.valueOf(config.getBedrock().port()); // otherwise we get commas

                if (throwable == null) {
                    if ("0.0.0.0".equals(address)) {
                        // basically just hide it in the log because some people get confused and try to change it
                        logger.info(GeyserLocale.getLocaleStringLog("geyser.core.start.ip_suppressed", port));
                    } else {
                        logger.info(GeyserLocale.getLocaleStringLog("geyser.core.start", address, port));
                    }
                } else {
                    logger.severe(GeyserLocale.getLocaleStringLog("geyser.core.fail", address, port));
                    if (!"0.0.0.0".equals(address)) {
                        logger.info(Component.text("Suggestion: try setting `address` under `bedrock` in the Geyser config back to 0.0.0.0", NamedTextColor.GREEN));
                        logger.info(Component.text("Then, restart this server.", NamedTextColor.GREEN));
                    }
                }
            }).join();

        if (config.getRemote().authType() == AuthType.FLOODGATE) {
            try {
                Key key = new AesKeyProducer().produceFrom(config.getFloodgateKeyPath());
                cipher = new AesCipher(new Base64Topping());
                cipher.init(key);
                logger.debug("Loaded Floodgate key!");
                // Note: this is positioned after the bind so the skin uploader doesn't try to run if Geyser fails
                // to load successfully. Spigot complains about class loader if the plugin is disabled.
                skinUploader = new FloodgateSkinUploader(this).start();
            } catch (Exception exception) {
                logger.severe(GeyserLocale.getLocaleStringLog("geyser.auth.floodgate.bad_key"), exception);
            }
        }

        if (config.getMetrics().isEnabled()) {
            metrics = new Metrics(this, "GeyserMC", config.getMetrics().getUniqueId(), false, java.util.logging.Logger.getLogger(""));
            metrics.addCustomChart(new Metrics.SingleLineChart("players", sessionManager::size));
            // Prevent unwanted words best we can
            metrics.addCustomChart(new Metrics.SimplePie("authMode", () -> config.getRemote().authType().toString().toLowerCase(Locale.ROOT)));
            metrics.addCustomChart(new Metrics.SimplePie("platform", platformType::platformName));
            metrics.addCustomChart(new Metrics.SimplePie("defaultLocale", GeyserLocale::getDefaultLocale));
            metrics.addCustomChart(new Metrics.SimplePie("version", () -> GeyserImpl.VERSION));
            metrics.addCustomChart(new Metrics.AdvancedPie("playerPlatform", () -> {
                Map<String, Integer> valueMap = new HashMap<>();
                for (GeyserSession session : sessionManager.getAllSessions()) {
                    if (session == null) continue;
                    if (session.getClientData() == null) continue;
                    String os = session.getClientData().getDeviceOs().toString();
                    if (!valueMap.containsKey(os)) {
                        valueMap.put(os, 1);
                    } else {
                        valueMap.put(os, valueMap.get(os) + 1);
                    }
                }
                return valueMap;
            }));
            metrics.addCustomChart(new Metrics.AdvancedPie("playerVersion", () -> {
                Map<String, Integer> valueMap = new HashMap<>();
                for (GeyserSession session : sessionManager.getAllSessions()) {
                    if (session == null) continue;
                    if (session.getClientData() == null) continue;
                    String version = session.getClientData().getGameVersion();
                    if (!valueMap.containsKey(version)) {
                        valueMap.put(version, 1);
                    } else {
                        valueMap.put(version, valueMap.get(version) + 1);
                    }
                }
                return valueMap;
            }));

            String minecraftVersion = bootstrap.getMinecraftServerVersion();
            if (minecraftVersion != null) {
                Map<String, Map<String, Integer>> versionMap = new HashMap<>();
                Map<String, Integer> platformMap = new HashMap<>();
                platformMap.put(platformType.platformName(), 1);
                versionMap.put(minecraftVersion, platformMap);

                metrics.addCustomChart(new Metrics.DrilldownPie("minecraftServerVersion", () -> {
                    // By the end, we should return, for example:
                    // 1.16.5 => (Spigot, 1)
                    return versionMap;
                }));
            }

            // The following code can be attributed to the PaperMC project
            // https://github.com/PaperMC/Paper/blob/master/Spigot-Server-Patches/0005-Paper-Metrics.patch#L614
            metrics.addCustomChart(new Metrics.DrilldownPie("javaVersion", () -> {
                Map<String, Map<String, Integer>> map = new HashMap<>();
                String javaVersion = System.getProperty("java.version");
                Map<String, Integer> entry = new HashMap<>();
                entry.put(javaVersion, 1);

                // http://openjdk.java.net/jeps/223
                // Java decided to change their versioning scheme and in doing so modified the
                // java.version system property to return $major[.$minor][.$security][-ea], as opposed to
                // 1.$major.0_$identifier we can handle pre-9 by checking if the "major" is equal to "1",
                // otherwise, 9+
                String majorVersion = javaVersion.split("\\.")[0];
                String release;

                int indexOf = javaVersion.lastIndexOf('.');

                if (majorVersion.equals("1")) {
                    release = "Java " + javaVersion.substring(0, indexOf);
                } else {
                    // of course, it really wouldn't be all that simple if they didn't add a quirk, now
                    // would it valid strings for the major may potentially include values such as -ea to
                    // denote a pre release
                    Matcher versionMatcher = Pattern.compile("\\d+").matcher(majorVersion);
                    if (versionMatcher.find()) {
                        majorVersion = versionMatcher.group(0);
                    }
                    release = "Java " + majorVersion;
                }
                map.put(release, entry);
                return map;
            }));
        } else {
            metrics = null;
        }

        if (config.getRemote().authType() == AuthType.ONLINE) {
            // May be written/read to on multiple threads from each GeyserSession as well as writing the config
            savedAuthChains = new ConcurrentHashMap<>();

            // TODO Remove after a while - just a migration help
            //noinspection deprecation
            File refreshTokensFile = bootstrap.getSavedUserLoginsFolder().resolve(Constants.SAVED_REFRESH_TOKEN_FILE).toFile();
            if (refreshTokensFile.exists()) {
                logger.info("Migrating refresh tokens to auth chains...");
                TypeReference<Map<String, String>> type = new TypeReference<>() { };
                Map<String, String> refreshTokens = null;
                try {
                    refreshTokens = JSON_MAPPER.readValue(refreshTokensFile, type);
                } catch (IOException e) {
                    // ignored - we'll just delete this file :))
                }

                if (refreshTokens != null) {
                    List<String> validUsers = config.getSavedUserLogins();
                    final Gson gson = new Gson();
                    for (Map.Entry<String, String> entry : refreshTokens.entrySet()) {
                        String user = entry.getKey();
                        if (!validUsers.contains(user)) {
                            continue;
                        }

                        // Migrate refresh tokens to auth chains
                        try {
                            StepFullJavaSession javaSession = PendingMicrosoftAuthentication.AUTH_FLOW.apply(false, 10);
                            StepFullJavaSession.FullJavaSession fullJavaSession = javaSession.getFromInput(
                                MinecraftAuthLogger.INSTANCE,
                                PendingMicrosoftAuthentication.AUTH_CLIENT,
                                new StepMsaToken.RefreshToken(entry.getValue())
                            );

                            String authChain = gson.toJson(javaSession.toJson(fullJavaSession));
                            savedAuthChains.put(user, authChain);
                        } catch (Exception e) {
                            GeyserImpl.getInstance().getLogger().warning("Could not migrate " + entry.getKey() + " to an auth chain! " +
                                "They will need to sign in the next time they join Geyser.");
                        }

                        // Ensure the new additions are written to the file
                        scheduleAuthChainsWrite();
                    }
                }

                // Finally: Delete it. Goodbye!
                refreshTokensFile.delete();
            }

            File authChainsFile = bootstrap.getSavedUserLoginsFolder().resolve(Constants.SAVED_AUTH_CHAINS_FILE).toFile();
            if (authChainsFile.exists()) {
                TypeReference<Map<String, String>> type = new TypeReference<>() { };

                Map<String, String> authChainFile = null;
                try {
                    authChainFile = JSON_MAPPER.readValue(authChainsFile, type);
                } catch (IOException e) {
                    logger.error("Cannot load saved user tokens!", e);
                }
                if (authChainFile != null) {
                    List<String> validUsers = config.getSavedUserLogins();
                    boolean doWrite = false;
                    for (Map.Entry<String, String> entry : authChainFile.entrySet()) {
                        String user = entry.getKey();
                        if (!validUsers.contains(user)) {
                            // Perform a write to this file to purge the now-unused name
                            doWrite = true;
                            continue;
                        }
                        savedAuthChains.put(user, entry.getValue());
                    }
                    if (doWrite) {
                        scheduleAuthChainsWrite();
                    }
                }
            }
        } else {
            savedAuthChains = null;
        }

        newsHandler.handleNews(null, NewsItemAction.ON_SERVER_STARTED);

        if (isReloading) {
            this.eventBus.fire(new GeyserPostReloadEvent(this.extensionManager, this.eventBus));
        } else {
            this.eventBus.fire(new GeyserPostInitializeEvent(this.extensionManager, this.eventBus));
        }

        if (config.isNotifyOnNewBedrockUpdate()) {
            VersionCheckUtils.checkForGeyserUpdate(this::getLogger);
        }
    }

    @Override
    public @NonNull List<GeyserSession> onlineConnections() {
        return sessionManager.getAllSessions();
    }

    @Override
    public int onlineConnectionsCount() {
        return sessionManager.size();
    }

    @Override
    public @MonotonicNonNull String usernamePrefix() {
        return null;
    }

    @Override
    public @Nullable GeyserSession connectionByUuid(@NonNull UUID uuid) {
        return this.sessionManager.getSessions().get(uuid);
    }

    @Override
    public @Nullable GeyserSession connectionByXuid(@NonNull String xuid) {
        return sessionManager.sessionByXuid(xuid);
    }

    @Override
    public boolean isBedrockPlayer(@NonNull UUID uuid) {
        return connectionByUuid(uuid) != null;
    }

    @Override
    public boolean sendForm(@NonNull UUID uuid, @NonNull Form form) {
        Objects.requireNonNull(uuid);
        Objects.requireNonNull(form);
        GeyserSession session = connectionByUuid(uuid);
        if (session == null) {
            return false;
        }
        return session.sendForm(form);
    }

    @Override
    public boolean sendForm(@NonNull UUID uuid, @NonNull FormBuilder<?, ?, ?> formBuilder) {
        return sendForm(uuid, formBuilder.build());
    }

    @Override
    public boolean transfer(@NonNull UUID uuid, @NonNull String address, int port) {
        Objects.requireNonNull(uuid);
        GeyserSession session = connectionByUuid(uuid);
        if (session == null) {
            return false;
        }
        return session.transfer(address, port);
    }

    public void disable() {
        bootstrap.getGeyserLogger().info(GeyserLocale.getLocaleStringLog("geyser.core.shutdown"));

        if (sessionManager.size() >= 1) {
            bootstrap.getGeyserLogger().info(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.log", sessionManager.size()));
            sessionManager.disconnectAll("geyser.core.shutdown.kick.message");
            bootstrap.getGeyserLogger().info(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.kick.done"));
        }

        runIfNonNull(scheduledThread, ScheduledExecutorService::shutdown);
        runIfNonNull(geyserServer, GeyserServer::shutdown);
        runIfNonNull(skinUploader, FloodgateSkinUploader::close);
        runIfNonNull(newsHandler, NewsHandler::shutdown);
        runIfNonNull(erosionUnixListener, UnixSocketClientListener::close);

        Registries.RESOURCE_PACKS.get().clear();

        this.setEnabled(false);
    }

    public void shutdown() {
        shuttingDown = true;
        if (isEnabled) {
            this.disable();
        }

        // Disable extensions, fire the shutdown event
        this.eventBus.fire(new GeyserShutdownEvent(this.extensionManager, this.eventBus));
        this.extensionManager.disableExtensions();

        bootstrap.getGeyserLogger().info(GeyserLocale.getLocaleStringLog("geyser.core.shutdown.done"));
    }

    public void reloadGeyser() {
        isReloading = true;
        this.eventBus.fire(new GeyserPreReloadEvent(this.extensionManager, this.eventBus));

        bootstrap.onGeyserDisable();
        bootstrap.onGeyserEnable();

        isReloading = false;
    }

    /**
     * Returns false if this Geyser instance is running in an IDE. This only needs to be used in cases where files
     * expected to be in a jarfile are not present.
     *
     * @return true if the version number is not 'DEV'.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isProductionEnvironment() {
        // First is if Blossom runs, second is if Blossom doesn't run
        //noinspection ConstantConditions - changes in production
        return !("git-local/dev-0000000".equals(GeyserImpl.GIT_VERSION) || "${gitVersion}".equals(GeyserImpl.GIT_VERSION));
    }

    @Override
    @NonNull
    public GeyserExtensionManager extensionManager() {
        return this.extensionManager;
    }

    /**
     * @return the current CommandRegistry in use. The instance may change over the lifecycle of the Geyser runtime.
     */
    @NonNull
    public CommandRegistry commandRegistry() {
        return this.bootstrap.getCommandRegistry();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R extends T, T> @NonNull R provider(@NonNull Class<T> apiClass, @Nullable Object... args) {
        ProviderSupplier provider = Registries.PROVIDERS.get(apiClass);
        if (provider == null) {
            throw new IllegalArgumentException("No provider found for " + apiClass);
        }
        return (R) provider.create(args);
    }

    @Override
    @NonNull
    public EventBus<EventRegistrar> eventBus() {
        return this.eventBus;
    }

    @NonNull
    public RemoteServer defaultRemoteServer() {
        return getConfig().getRemote();
    }

    @Override
    @NonNull
    public BedrockListener bedrockListener() {
        return getConfig().getBedrock();
    }

    @Override
    @NonNull
    public Path configDirectory() {
        return bootstrap.getConfigFolder();
    }

    @Override
    @NonNull
    public Path packDirectory() {
        return bootstrap.getConfigFolder().resolve("packs");
    }

    @Override
    @NonNull
    public PlatformType platformType() {
        return platformType;
    }

    @Override
    public @NonNull MinecraftVersion supportedJavaVersion() {
        return new MinecraftVersionImpl(GameProtocol.getJavaMinecraftVersion(), GameProtocol.getJavaProtocolVersion());
    }

    @Override
    public @NonNull List<MinecraftVersion> supportedBedrockVersions() {
        return Collections.unmodifiableList(GameProtocol.SUPPORTED_BEDROCK_VERSIONS);
    }

    @Override
    public @NonNull CommandSource consoleCommandSource() {
        return getLogger();
    }

    public int buildNumber() {
        if (!this.isProductionEnvironment()) {
            return 0;
        }

        //noinspection DataFlowIssue
        return Integer.parseInt(BUILD_NUMBER);
    }

    public static GeyserImpl load(PlatformType platformType, GeyserBootstrap bootstrap) {
        if (instance == null) {
            return new GeyserImpl(platformType, bootstrap);
        }

        return instance;
    }

    public static void start() {
        if (instance == null) {
            throw new RuntimeException("Geyser has not been loaded yet!");
        }

        if (getInstance().isReloading()) {
            instance.startInstance();
        } else {
            instance.initialize();
        }
        instance.setEnabled(true);
    }

    public GeyserLogger getLogger() {
        return bootstrap.getGeyserLogger();
    }

    public GeyserConfiguration getConfig() {
        return bootstrap.getGeyserConfig();
    }

    public WorldManager getWorldManager() {
        return bootstrap.getWorldManager();
    }

    @Nullable
    public String authChainFor(@NonNull String bedrockName) {
        return savedAuthChains.get(bedrockName);
    }

    public void saveAuthChain(@NonNull String bedrockName, @NonNull String authChain) {
        if (!getConfig().getSavedUserLogins().contains(bedrockName)) {
            // Do not save this login
            return;
        }

        // We can safely overwrite old instances because MsaAuthenticationService#getLoginResponseFromRefreshToken
        // refreshes the token for us
        if (!Objects.equals(authChain, savedAuthChains.put(bedrockName, authChain))) {
            scheduleAuthChainsWrite();
        }
    }

    private <T> void runIfNonNull(T nullable, Consumer<T> consumer) {
        if (nullable != null) {
            consumer.accept(nullable);
        }
    }

    private void scheduleAuthChainsWrite() {
        scheduledThread.execute(() -> {
            // Ensure all writes are handled on the same thread
            File savedAuthChains = getBootstrap().getSavedUserLoginsFolder().resolve(Constants.SAVED_AUTH_CHAINS_FILE).toFile();
            TypeReference<Map<String, String>> type = new TypeReference<>() { };
            try (FileWriter writer = new FileWriter(savedAuthChains)) {
                JSON_MAPPER.writerFor(type)
                        .withDefaultPrettyPrinter()
                        .writeValue(writer, this.savedAuthChains);
            } catch (IOException e) {
                getLogger().error("Unable to write saved refresh tokens!", e);
            }
        });
    }
}
