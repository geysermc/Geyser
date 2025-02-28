/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.configuration;

import com.google.common.annotations.VisibleForTesting;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.returnsreceiver.qual.This;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserBootstrap;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.text.GeyserLocale;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.interfaces.InterfaceDefaultOptions;
import org.spongepowered.configurate.objectmapping.meta.Processor;
import org.spongepowered.configurate.transformation.ConfigurationTransformation;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.spongepowered.configurate.NodePath.path;
import static org.spongepowered.configurate.transformation.TransformAction.remove;
import static org.spongepowered.configurate.transformation.TransformAction.rename;

public final class ConfigLoader {
    private static final String HEADER = """
            --------------------------------
            Geyser Configuration File

            A bridge between Minecraft: Bedrock Edition and Minecraft: Java Edition.

            GitHub: https://github.com/GeyserMC/Geyser
            Discord: https://discord.gg/geysermc
            Wiki: https://geysermc.org/wiki

            NOTICE: See https://geysermc.org/wiki/geyser/setup/ for the setup guide. Many video tutorials are outdated.
            In most cases, especially with server hosting providers, further hosting-specific configuration is required.
            --------------------------------""";

    private static final String ADVANCED_HEADER = """
            --------------------------------
            Geyser ADVANCED Configuration File
            
            In most cases, you do *not* need to mess with this file to get Geyser running.
            Tread with caution.
            --------------------------------
            """;

    /**
     * Only nullable for testing.
     */
    private final @Nullable GeyserBootstrap bootstrap;
    private @Nullable Consumer<CommentedConfigurationNode> transformer;
    private File configFile;

    public ConfigLoader(GeyserBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        configFile = new File(bootstrap.getConfigFolder().toFile(), "config.yml");
    }

    @VisibleForTesting
    ConfigLoader(File file) {
        this.bootstrap = null;
        configFile = file;
    }

    /**
     * Creates the directory as indicated by {@link GeyserBootstrap#getConfigFolder()}
     */
    @This
    public ConfigLoader createFolder() {
        Path dataFolder = this.bootstrap.getConfigFolder();
        if (!dataFolder.toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            dataFolder.toFile().mkdir();
        }
        return this;
    }

    @This
    public ConfigLoader transformer(Consumer<CommentedConfigurationNode> transformer) {
        this.transformer = transformer;
        return this;
    }

    @This
    public ConfigLoader configFile(File configFile) {
        this.configFile = configFile;
        return this;
    }

    /**
     * @return null if the config failed to load.
     */
    @Nullable
    public <T extends GeyserConfig> T load(Class<T> configClass) {
        try {
            return load0(configClass);
        } catch (IOException ex) {
            bootstrap.getGeyserLogger().error(GeyserLocale.getLocaleStringLog("geyser.config.failed"), ex);
            return null;
        }
    }

    private <T extends GeyserConfig> T load0(Class<T> configClass) throws IOException {
        var loader = createLoader(configFile, HEADER);

        CommentedConfigurationNode node = loader.load();
        boolean originallyEmpty = !configFile.exists() || node.isNull();

        var migrations = ConfigurationTransformation.versionedBuilder()
            .versionKey("config-version")
                // Pre-Configurate
                .addVersion(5, ConfigurationTransformation.builder()
                    .addAction(path("legacy-ping-passthrough"), configClass == GeyserRemoteConfig.class ? remove() : (path, value) -> {
                        // Invert value
                        value.set(!value.getBoolean());
                        return new Object[]{"integrated-ping-passthrough"};
                    })
                    .addAction(path("remote"), rename("java"))
                    .addAction(path("floodgate-key-file"), (path, value) -> {
                        // Elimate any legacy config values
                        if ("public-key.pem".equals(value.getString())) {
                            value.set("key.pem");
                        }
                        return null;
                    })
                    .addAction(path("default-locale"), (path, value) -> {
                        if (value.getString() == null) {
                            value.set("system");
                        }
                        return null;
                    })
                    .addAction(path("show-cooldown"), (path, value) -> {
                        String s = value.getString();
                        if (s != null) {
                            switch (s) {
                                case "true" -> value.set("title");
                                case "false" -> value.set("disabled");
                            }
                        }
                        return null;
                    })
                    .addAction(path("metrics", "uuid"), (path, value) -> {
                        if ("generateduuid".equals(value.getString())) {
                            // Manually copied config without Metrics UUID creation?
                            value.set(UUID.randomUUID());
                        }
                        return null;
                    })
                    .addAction(path("remote", "address"), (path, value) -> {
                        if ("auto".equals(value.getString())) {
                            // Auto-convert back to localhost
                            value.set("127.0.0.1");
                        }
                        return null;
                    })
                    .addAction(path("metrics", "enabled"), (path, value) -> {
                        // Move to the root, not in the Metrics class.
                        return new Object[]{"enable-metrics"};
                    })
                    .addAction(path("bedrock", "motd1"), rename("primary-motd"))
                    .addAction(path("bedrock", "motd2"), rename("secondary-motd"))
                    .build())
                .build();

        int currentVersion = migrations.version(node);
        migrations.apply(node);
        int newVersion = migrations.version(node);

        T config = node.get(configClass);

        // Serialize the instance to ensure strict field ordering. Additionally, if we serialized back
        // to the old node, existing nodes would only have their value changed, keeping their position
        // at the top of the ordered map, forcing all new nodes to the bottom (regardless of field order).
        // For that reason, we must also create a new node.
        CommentedConfigurationNode newRoot = CommentedConfigurationNode.root(loader.defaultOptions());
        newRoot.set(config);

        // Create the path in a way that Standalone changing the config name will be fine.
        int extensionIndex = configFile.getName().lastIndexOf(".");
        File advancedConfigPath = new File(configFile.getParent(), configFile.getName().substring(0, extensionIndex) + "-advanced" + configFile.getName().substring(extensionIndex));
        AdvancedConfig advancedConfig = null;

        if (originallyEmpty || currentVersion != newVersion) {

            if (!originallyEmpty && currentVersion > 4) {
                // Only copy comments over if the file already existed, and we are going to replace it

                // Second case: Version 4 is pre-configurate where there were commented out nodes.
                // These get treated as comments on lower nodes, which produces very undesirable results.

                ConfigurationCommentMover.moveComments(node, newRoot);
            } else if (currentVersion <= 4) {
                advancedConfig = migrateToAdvancedConfig(advancedConfigPath, node);
            }

            loader.save(newRoot);
        }
        if (advancedConfig == null) {
            advancedConfig = loadAdvancedConfig(advancedConfigPath);
        }

        if (transformer != null) {
            // We transform AFTER saving so that these specific transformations aren't applied to file.
            transformer.accept(newRoot);
            config = newRoot.get(configClass);
        }

        config.advanced(advancedConfig);

        if (this.bootstrap != null) { // Null for testing only.
            this.bootstrap.getGeyserLogger().setDebug(config.debugMode());
        }

        return config;
    }

    private AdvancedConfig migrateToAdvancedConfig(File file, ConfigurationNode configRoot) throws IOException {
        Stream<NodePath> copyFromOldConfig = Stream.of("max-visible-custom-skulls", "custom-skull-render-distance", "scoreboard-packet-threshold", "mtu",
                "floodgate-key-file", "use-direct-connection", "disable-compression")
            .map(NodePath::path);

        var loader = createLoader(file, ADVANCED_HEADER);

        CommentedConfigurationNode advancedNode = CommentedConfigurationNode.root(loader.defaultOptions());
        copyFromOldConfig.forEach(path -> {
            ConfigurationNode node = configRoot.node(path);
            if (!node.virtual()) {
                advancedNode.node(path).mergeFrom(node);
                configRoot.removeChild(path);
            }
        });

        ConfigurationNode metricsUuid = configRoot.node("metrics", "uuid");
        if (!metricsUuid.virtual()) {
            advancedNode.node("metrics-uuid").set(metricsUuid.get(UUID.class));
        }

        advancedNode.node("version").set(Constants.ADVANCED_CONFIG_VERSION);

        AdvancedConfig advancedConfig = advancedNode.get(AdvancedConfig.class);
        // Ensure all fields get populated
        CommentedConfigurationNode newNode = CommentedConfigurationNode.root(loader.defaultOptions());
        newNode.set(advancedConfig);
        loader.save(newNode);
        return advancedConfig;
    }

    private AdvancedConfig loadAdvancedConfig(File file) throws IOException {
        var loader = createLoader(file, ADVANCED_HEADER);
        if (file.exists()) {
            ConfigurationNode node = loader.load();
            return node.get(AdvancedConfig.class);
        } else {
            ConfigurationNode node = CommentedConfigurationNode.root(loader.defaultOptions());
            node.node("version").set(Constants.ADVANCED_CONFIG_VERSION);
            AdvancedConfig advancedConfig = node.get(AdvancedConfig.class);
            node.set(advancedConfig);
            loader.save(node);
            return advancedConfig;
        }
    }

    private YamlConfigurationLoader createLoader(File file, String header) {
        return YamlConfigurationLoader.builder()
            .file(file)
            .indent(2)
            .nodeStyle(NodeStyle.BLOCK)
            .defaultOptions(options -> InterfaceDefaultOptions.addTo(options, builder -> {
                    if (this.bootstrap != null) { // Testing only.
                        builder.addProcessor(ExcludePlatform.class, excludePlatform(bootstrap.platformType().platformName()))
                            .addProcessor(PluginSpecific.class, integrationSpecific(bootstrap.platformType() != PlatformType.STANDALONE));
                    }
                })
                .shouldCopyDefaults(false) // If we use ConfigurationNode#get(type, default), do not write the default back to the node.
                .header(header)
                .serializers(builder -> builder.register(new LowercaseEnumSerializer())))
            .build();
    }
    
    private static Processor.Factory<ExcludePlatform, Object> excludePlatform(String thisPlatform) {
        return (data, fieldType) -> (value, destination) -> {
            for (String platform : data.platforms()) {
                if (thisPlatform.equals(platform)) {
                    //noinspection DataFlowIssue
                    destination.parent().removeChild(destination.key());
                    break;
                }
            }
        };
    }

    private static Processor.Factory<PluginSpecific, Object> integrationSpecific(boolean thisConfigPlugin) {
        return (data, fieldType) -> (value, destination) -> {
            if (data.forPlugin() != thisConfigPlugin) {
                //noinspection DataFlowIssue
                destination.parent().removeChild(destination.key());
            }
        };
    }
}
