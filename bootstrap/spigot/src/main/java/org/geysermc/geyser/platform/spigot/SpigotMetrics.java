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

package org.geysermc.geyser.platform.spigot;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.util.metrics.MetricsPlatform;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class SpigotMetrics implements MetricsPlatform {
    private final YamlConfiguration config;

    public SpigotMetrics(Plugin plugin) {
        // https://github.com/Bastian/bstats-metrics/blob/master/bukkit/src/main/java/org/bstats/bukkit/Metrics.java
        // Get the config file
        File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
        File configFile = new File(bStatsFolder, "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);

        if (!config.isSet("serverUuid")) {
            config.addDefault("enabled", true);
            config.addDefault("serverUuid", UUID.randomUUID().toString());
            config.addDefault("logFailedRequests", false);
            config.addDefault("logSentData", false);
            config.addDefault("logResponseStatusText", false);

            // Inform the server owners about bStats
            config.options().header(
                "bStats (https://bStats.org) collects some basic information for plugin authors, like how\n" +
                    "many people use their plugin and their total player count. It's recommended to keep bStats\n" +
                    "enabled, but if you're not comfortable with this, you can turn this setting off. There is no\n" +
                    "performance penalty associated with having metrics enabled, and data sent to bStats is fully\n" +
                    "anonymous."
            ).copyDefaults(true);
            try {
                config.save(configFile);
            } catch (IOException ignored) { }
        }
    }

    @Override
    public boolean enabled() {
        return config.getBoolean("enabled", true);
    }

    @Override
    public String serverUuid() {
        return config.getString("serverUuid");
    }

    @Override
    public boolean logFailedRequests() {
        return config.getBoolean("logFailedRequests", false);
    }

    @Override
    public boolean logSentData() {
        return config.getBoolean("logSentData", false);
    }

    @Override
    public boolean logResponseStatusText() {
        return config.getBoolean("logResponseStatusText", false);
    }
}
