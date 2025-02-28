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

import com.google.gson.annotations.JsonAdapter;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.text.AsteriskSerializer;

import java.util.ArrayList;
import java.util.List;

@Getter
public class GeyserVelocityDumpInfo extends BootstrapDumpInfo {

    private final String platformName;
    private final String platformVersion;
    private final String platformVendor;
    private final boolean onlineMode;

    @JsonAdapter(value = AsteriskSerializer.class)
    private final String serverIP;
    private final int serverPort;
    private final List<PluginInfo> plugins;

    GeyserVelocityDumpInfo(ProxyServer proxy) {
        super();
        this.platformName = proxy.getVersion().getName();
        this.platformVersion = proxy.getVersion().getVersion();
        this.platformVendor = proxy.getVersion().getVendor();
        this.onlineMode = proxy.getConfiguration().isOnlineMode();
        this.serverIP = proxy.getBoundAddress().getHostString();
        this.serverPort = proxy.getBoundAddress().getPort();
        this.plugins = new ArrayList<>();

        for (PluginContainer plugin : proxy.getPluginManager().getPlugins()) {
            String pluginClass = plugin.getInstance().map((pl) -> pl.getClass().getName()).orElse("unknown");
            this.plugins.add(new PluginInfo(true, plugin.getDescription().getName().orElse(null), plugin.getDescription().getVersion().orElse(null), pluginClass, plugin.getDescription().getAuthors()));
        }
    }
}
