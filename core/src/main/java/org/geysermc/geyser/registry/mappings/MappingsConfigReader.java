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

package org.geysermc.geyser.registry.mappings;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;
import org.geysermc.geyser.registry.mappings.versions.MappingsReader;
import org.geysermc.geyser.registry.mappings.versions.MappingsReader_v1;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;

public class MappingsConfigReader {
    private final Int2ObjectMap<MappingsReader> mappingReaders = new Int2ObjectOpenHashMap<>();
    private final Path customMappingsDirectory = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("custom_mappings");

    public MappingsConfigReader() {
        this.mappingReaders.put(1, new MappingsReader_v1());
    }

    public Path[] getCustomMappingsFiles() {
        try {
            return Files.walk(this.customMappingsDirectory)
                    .filter(child -> child.toString().endsWith(".json"))
                    .toArray(Path[]::new);
        } catch (IOException e) {
            return new Path[0];
        }
    }


    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean ensureMappingsDirectory(Path mappingsDirectory) {
        if (!Files.exists(mappingsDirectory)) {
            try {
                Files.createDirectories(mappingsDirectory);
                return true;
            } catch (IOException e) {
                GeyserImpl.getInstance().getLogger().error("Failed to create mappings directory", e);
                return false;
            }
        }
        return true;
    }

    public void loadItemMappingsFromJson(BiConsumer<String, CustomItemData> consumer) {
        if (!ensureMappingsDirectory(this.customMappingsDirectory)) {
            return;
        }

        Path[] mappingsFiles = this.getCustomMappingsFiles();
        for (Path mappingsFile : mappingsFiles) {
            this.readItemMappingsFromJson(mappingsFile, consumer);
        }
    }

    public void loadBlockMappingsFromJson(BiConsumer<String, CustomBlockMapping> consumer) {
        if (!ensureMappingsDirectory(this.customMappingsDirectory)) {
            return;
        }

        Path[] mappingsFiles = this.getCustomMappingsFiles();
        for (Path mappingsFile : mappingsFiles) {
            this.readBlockMappingsFromJson(mappingsFile, consumer);
        }
    }

    public @Nullable JsonObject getMappingsRoot(Path file) {
        JsonObject mappingsRoot;
        try (FileReader reader = new FileReader(file.toFile())) {
            mappingsRoot = (JsonObject) new JsonParser().parse(reader);
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().error("Failed to read custom mapping file: " + file, e);
            return null;
        }

        if (!mappingsRoot.has("format_version")) {
            GeyserImpl.getInstance().getLogger().error("Mappings file " + file + " is missing the format version field!");
            return null;
        }

        return mappingsRoot;
    }

    public int getFormatVersion(JsonObject mappingsRoot, Path file) {
        int formatVersion =  mappingsRoot.get("format_version").getAsInt();
        if (!this.mappingReaders.containsKey(formatVersion)) {
            GeyserImpl.getInstance().getLogger().error("Mappings file " + file + " has an unknown format version: " + formatVersion);
            return -1;
        }
        return formatVersion;
    }

    public void readItemMappingsFromJson(Path file, BiConsumer<String, CustomItemData> consumer) {
        JsonObject mappingsRoot = getMappingsRoot(file);

        if (mappingsRoot == null) {
            return;
        }

        int formatVersion = getFormatVersion(mappingsRoot, file);

        if (formatVersion < 0) {
            return;
        }

        this.mappingReaders.get(formatVersion).readItemMappings(file, mappingsRoot, consumer);
    }

    public void readBlockMappingsFromJson(Path file, BiConsumer<String, CustomBlockMapping> consumer) {
        JsonObject mappingsRoot = getMappingsRoot(file);

        if (mappingsRoot == null) {
            return;
        }

        int formatVersion = getFormatVersion(mappingsRoot, file);

        if (formatVersion < 0) {
            return;
        }

        this.mappingReaders.get(formatVersion).readBlockMappings(file, mappingsRoot, consumer);
    }
}
