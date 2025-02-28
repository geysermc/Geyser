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

package org.geysermc.geyser.platform.neoforge;

import com.google.gson.annotations.JsonAdapter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.language.IModInfo;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.text.AsteriskSerializer;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Getter
public class GeyserNeoForgeDumpInfo extends BootstrapDumpInfo {

    private final String platformName;
    private final String platformVersion;
    private final String minecraftVersion;
    private final Dist dist;

    @JsonAdapter(value = AsteriskSerializer.class)
    private final String serverIP;
    private final int serverPort;
    private final boolean onlineMode;
    private final List<ModInfo> mods;

    public GeyserNeoForgeDumpInfo(MinecraftServer server) {
        this.platformName = FMLLoader.launcherHandlerName();
        this.platformVersion = FMLLoader.versionInfo().neoForgeVersion();
        this.minecraftVersion = FMLLoader.versionInfo().mcVersion();
        this.dist = FMLLoader.getDist();
        this.serverIP = server.getLocalIp() == null ? "unknown" : server.getLocalIp();
        this.serverPort = server.getPort();
        this.onlineMode = server.usesAuthentication();
        this.mods = new ArrayList<>();

        for (IModInfo mod : ModList.get().getMods()) {
            this.mods.add(new ModInfo(
                    ModList.get().isLoaded(mod.getModId()),
                    mod.getModId(),
                    mod.getVersion().toString(),
                    mod.getModURL().map(URL::toString).orElse("")
            ));
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ModInfo {
        public boolean enabled;
        public String name;
        public String version;
        public String url;
    }
}
