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

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.dump.BootstrapDumpInfo;
import org.geysermc.geyser.platform.mod.GeyserModBootstrap;
import org.geysermc.geyser.platform.mod.platform.GeyserModPlatform;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class GeyserNeoForgePlatform implements GeyserModPlatform {

    private final ModContainer container;

    public GeyserNeoForgePlatform(ModContainer container) {
        this.container = container;
    }

    @Override
    public @NonNull PlatformType platformType() {
        return PlatformType.NEOFORGE;
    }

    @Override
    public @NonNull String configPath() {
        return "Geyser-NeoForge";
    }

    @Override
    public @NonNull Path dataFolder(@NonNull String modId) {
        return FMLPaths.CONFIGDIR.get().resolve(modId);
    }

    @Override
    public @NonNull BootstrapDumpInfo dumpInfo(@NonNull MinecraftServer server) {
        return new GeyserNeoForgeDumpInfo(server);
    }

    @Override
    public boolean testFloodgatePluginPresent(@NonNull GeyserModBootstrap bootstrap) {
        if (ModList.get().isLoaded("floodgate")) {
            Path floodgateDataFolder = FMLPaths.CONFIGDIR.get().resolve("floodgate");
            bootstrap.loadFloodgate(floodgateDataFolder);
            return true;
        }
        return false;
    }

    @Override
    public @Nullable InputStream resolveResource(@NonNull String resource) {
        try {
            Path path = container.getModInfo().getOwningFile().getFile().findResource(resource);
            return Files.newInputStream(path);
        } catch (IOException e) {
            return null;
        }
    }
}
