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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.pack.ResourcePackManifest;

import java.io.IOException;
import java.util.Collection;
import java.util.UUID;

public record GeyserResourcePackManifest(
    @JsonProperty("format_version") int formatVersion,
    Header header,
    Collection<Module> modules,
    Collection<Dependency> dependencies,
    Collection<Subpack> subpacks,
    Collection<Setting> settings
) implements ResourcePackManifest {

    public record Header(UUID uuid, Version version, String name, String description, @JsonProperty("min_engine_version") Version minimumSupportedMinecraftVersion) implements ResourcePackManifest.Header { }

    public record Module(UUID uuid, Version version, String type, String description) implements ResourcePackManifest.Module { }

    public record Dependency(UUID uuid, Version version) implements ResourcePackManifest.Dependency { }

    public record Subpack(@JsonProperty("folder_name") String folderName, String name, @JsonProperty("memory_tier") Float memoryTier) implements ResourcePackManifest.Subpack { }

    public record Setting(String type, String text) implements ResourcePackManifest.Setting { }

    @JsonDeserialize(using = Version.VersionDeserializer.class)
    public record Version(int major, int minor, int patch) implements ResourcePackManifest.Version {

        @Override
        public @NonNull String toString() {
            return major + "." + minor + "." + patch;
        }

        public static class VersionDeserializer extends JsonDeserializer<Version> {
            @Override
            public Version deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                int[] version = ctxt.readValue(p, int[].class);
                return new Version(version[0], version[1], version[2]);
            }
        }
    }
}

