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

package org.geysermc.geyser.platform.standalone;

import io.netty.util.ResourceLeakDetector;
import lombok.Getter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.command.standalone.StandaloneCloudCommandManager;
import org.geysermc.geyser.configuration.ConfigLoader;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.configuration.GeyserRemoteConfig;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.ping.GeyserLegacyPingPassthrough;
import org.geysermc.geyser.ping.IGeyserPingPassthrough;
import org.geysermc.geyser.platform.standalone.gui.GeyserStandaloneGUI;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.LoopbackUtil;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

public class GeyserStandaloneBootstrap implements GeyserBootstrap {

    private StandaloneCloudCommandManager cloud;
    private CommandRegistry commandRegistry;
    private GeyserConfig geyserConfig;
    private final GeyserStandaloneLogger geyserLogger = new GeyserStandaloneLogger();
    private IGeyserPingPassthrough geyserPingPassthrough;
    private GeyserStandaloneGUI gui;
    @Getter
    private boolean useGui = System.console() == null && !isHeadless();
    private Logger log4jLogger;
    private String configFilename = "config.yml";

    private GeyserImpl geyser;

    private static final Map<NodePath, String> argsConfigKeys = new HashMap<>();

    public static void main(String[] args) {
        if (System.getProperty("io.netty.leakDetection.level") == null) {
            ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED); // Can eat performance
        }

        System.setProperty("java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");
        GeyserStandaloneLogger.setupStreams();

        GeyserStandaloneBootstrap bootstrap = new GeyserStandaloneBootstrap();
        // Set defaults
        boolean useGuiOpts = bootstrap.useGui;
        String configFilenameOpt = bootstrap.configFilename;

        GeyserLocale.init(bootstrap);

        for (int i = 0; i < args.length; i++) {
            // By default, standalone Geyser will check if it should open the GUI based on if the GUI is null
            // Optionally, you can force the use of a GUI or no GUI by specifying args
            // Allows gui and nogui without options, for backwards compatibility
            String arg = args[i];
            switch (arg) {
                case "--gui", "gui" -> useGuiOpts = true;
                case "--nogui", "nogui" -> useGuiOpts = false;
                case "--config", "-c" -> {
                    if (i >= args.length - 1) {
                        System.err.println(MessageFormat.format(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.config_not_specified"), "-c"));
                        return;
                    }
                    configFilenameOpt = args[i + 1];
                    i++;
                    System.out.println(MessageFormat.format(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.config_specified"), configFilenameOpt));
                }
                case "--help", "-h" -> {
                    System.out.println(MessageFormat.format(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.usage"), "[java -jar] Geyser.jar [opts]"));
                    System.out.println("  " + GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.options"));
                    System.out.println("    -c, --config [file]    " + GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.config"));
                    System.out.println("    -h, --help             " + GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.help"));
                    System.out.println("    --gui, --nogui         " + GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.gui"));
                    return;
                }
                default -> {
                    // We have likely added a config option argument
                    if (arg.startsWith("--")) {
                        // Split the argument by an =
                        String[] argParts = arg.substring(2).split("=");
                        if (argParts.length == 2) {
                            argsConfigKeys.put(NodePath.of(argParts[0].split("\\.")), argParts[1]);
                            break;
                        }
                    }
                    System.err.println(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.unrecognised", arg));
                    return;
                }
            }
        }
        bootstrap.useGui = useGuiOpts;
        bootstrap.configFilename = configFilenameOpt;
        bootstrap.onGeyserInitialize();
    }

    @Override
    public void onGeyserInitialize() {
        log4jLogger = (Logger) LogManager.getRootLogger();

        if (useGui && gui == null) {
            gui = new GeyserStandaloneGUI(geyserLogger);
            gui.addGuiAppender();
            gui.startUpdateThread();
        }

        LoopbackUtil.checkAndApplyLoopback(geyserLogger);

        this.onGeyserEnable();
    }

    @Override
    public void onGeyserEnable() {
        this.geyserConfig = new ConfigLoader(this)
            .transformer(this::handleArgsConfigOptions)
            .load(GeyserRemoteConfig.class);
        if (this.geyserConfig == null) {
            if (gui == null) {
                System.exit(1);
            } else {
                // Leave the process running so the GUI is still visible
                return;
            }
        }

        // Allow libraries like Protocol to have their debug information passthrough
        log4jLogger.get().setLevel(geyserConfig.debugMode() ? Level.DEBUG : Level.INFO);

        geyser = GeyserImpl.load(this);

        boolean reloading = geyser.isReloading();
        if (!reloading) {
            // Currently there would be no significant benefit of re-initializing commands. Also, we would have to unsubscribe CommandRegistry.
            // Fire GeyserDefineCommandsEvent after PreInitEvent, before PostInitEvent, for consistency with other bootstraps.
            cloud = new StandaloneCloudCommandManager(geyser);
            commandRegistry = new CommandRegistry(geyser, cloud);
        }

        GeyserImpl.start();

        if (!reloading) {
            // Event must be fired after CommandRegistry has subscribed its listener.
            // Also, the subscription for the Permissions class is created when Geyser is initialized.
            cloud.fireRegisterPermissionsEvent();
        }

        if (gui != null) {
            gui.enableCommands(geyser.getScheduledThread(), commandRegistry);
        }

        geyserPingPassthrough = GeyserLegacyPingPassthrough.init(geyser);

        geyserLogger.start();
    }

    /**
     * Check using {@link java.awt.GraphicsEnvironment} that we are a headless client
     *
     * @return If the current environment is headless
     */
    private boolean isHeadless() {
        try {
            Class<?> graphicsEnv = Class.forName("java.awt.GraphicsEnvironment");
            Method isHeadless = graphicsEnv.getDeclaredMethod("isHeadless");
            return (boolean) isHeadless.invoke(null);
        } catch (Exception ignore) {
        }

        return true;
    }

    @Override
    public void onGeyserDisable() {
        geyser.disable();
    }

    @Override
    public void onGeyserShutdown() {
        geyser.shutdown();
        System.exit(0);
    }

    @Override
    public PlatformType platformType() {
        return PlatformType.STANDALONE;
    }

    @Override
    public GeyserConfig config() {
        return this.geyserConfig;
    }

    @Override
    public GeyserStandaloneLogger getGeyserLogger() {
        return geyserLogger;
    }

    @Override
    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    @Override
    public IGeyserPingPassthrough getGeyserPingPassthrough() {
        return geyserPingPassthrough;
    }

    @Override
    public Path getConfigFolder() {
        // Return the current working directory
        return Paths.get(System.getProperty("user.dir"));
    }

    @Override
    public Path getSavedUserLoginsFolder() {
        // Return the location of the config
        return new File(configFilename).getAbsoluteFile().getParentFile().toPath();
    }

    @Override
    public BootstrapDumpInfo getDumpInfo() {
        return new GeyserStandaloneDumpInfo(this);
    }

    @NonNull
    @Override
    public String getServerBindAddress() {
        throw new IllegalStateException();
    }

    @Override
    public int getServerPort() {
        throw new IllegalStateException();
    }

    @Override
    public boolean testFloodgatePluginPresent() {
        return false;
    }

    @Override
    public Path getFloodgateKeyPath() {
        return Path.of(geyserConfig.advanced().floodgateKeyFile());
    }

    /**
     * Set a POJO property value on an object
     *
     * @param value The new value of the property
     */
    private static void setConfigOption(CommentedConfigurationNode node, Object value) throws SerializationException {
        Object parsedValue = value;

        // Change the values type if needed
        Class<?> clazz = node.raw().getClass();
        if (Integer.class == clazz) {
            parsedValue = Integer.valueOf((String) parsedValue);
        } else if (Boolean.class == clazz) {
            parsedValue = Boolean.valueOf((String) parsedValue);
        }

        node.set(parsedValue);
    }

    /**
     * Update the loaded config with any values passed in the command line arguments
     */
    private void handleArgsConfigOptions(CommentedConfigurationNode node) {
        for (Map.Entry<NodePath, String> configKey : argsConfigKeys.entrySet()) {
            NodePath path = configKey.getKey();
            CommentedConfigurationNode subNode = node.node(path);
            if (subNode.virtual()) {
                geyserLogger.error(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.unrecognised", path));
                continue;
            }

            try {
                setConfigOption(subNode, configKey.getValue());
                geyserLogger.info(GeyserLocale.getLocaleStringLog("geyser.bootstrap.args.set_config_option", configKey.getKey(), configKey.getValue()));
            } catch (SerializationException e) {
                geyserLogger.error("Failed to set config option: " + path);
            }
        }
    }
}
