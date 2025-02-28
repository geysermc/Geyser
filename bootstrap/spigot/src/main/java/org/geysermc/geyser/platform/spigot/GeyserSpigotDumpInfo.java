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

package org.geysermc.geyser.platform.spigot;

import com.google.gson.annotations.JsonAdapter;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.text.AsteriskSerializer;

import java.util.ArrayList;
import java.util.List;

@Getter
public class GeyserSpigotDumpInfo extends BootstrapDumpInfo {

    private final String platformName;
    private final String platformVersion;
    private final String platformAPIVersion;
    private final boolean onlineMode;

    @JsonAdapter(value = AsteriskSerializer.class)
    private final String serverIP;
    private final int serverPort;
    private final List<PluginInfo> plugins;

    @SuppressWarnings("deprecation")
    GeyserSpigotDumpInfo() {
        super();
        this.platformName = Bukkit.getName();
        this.platformVersion = Bukkit.getVersion();
        this.platformAPIVersion = Bukkit.getBukkitVersion();
        this.onlineMode = Bukkit.getOnlineMode();
        this.serverIP = Bukkit.getIp();
        this.serverPort = Bukkit.getPort();
        this.plugins = new ArrayList<>();

        for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
            this.plugins.add(new PluginInfo(plugin.isEnabled(), plugin.getName(), plugin.getDescription().getVersion(), plugin.getDescription().getMain(), plugin.getDescription().getAuthors()));
        }
    }
}
