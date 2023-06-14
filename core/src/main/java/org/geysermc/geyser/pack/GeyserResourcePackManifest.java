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

package org.geysermc.geyser.pack;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

public record GeyserResourcePackManifest(@JsonProperty("format_version") int formatVersion, Header header, Collection<Module> modules, Collection<Dependency> dependencies) implements ResourcePackManifest {

    public record Header(UUID uuid, int[] version, String name, String description, @JsonProperty("min_engine_version") int[] minimumSupportedMinecraftVersion) implements ResourcePackManifest.Header {
        @Override
        public @NotNull String versionString() {
            return new Version(version).versionString();
        }
    }

    public record Module(UUID uuid, int[] version, String type, String description) implements ResourcePackManifest.Module { }

    public record Dependency(UUID uuid, int[] version) implements ResourcePackManifest.Dependency { }

    public record Version(int[] version) implements ResourcePackManifest.Version {

        public String versionString() {
            return major() + "." + minor() + "." + patch();
        }

        @Override
        public int major() {
            return version[0];
        }

        @Override
        public int minor() {
            return version[1];
        }

        @Override
        public int patch() {
            return version[2];
        }
    }
}

