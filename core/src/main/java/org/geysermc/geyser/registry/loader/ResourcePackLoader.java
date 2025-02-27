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

package org.geysermc.geyser.registry.loader;

import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.GeyserResourcePackManifest;
import org.geysermc.geyser.pack.SkullResourcePackManager;
import org.geysermc.geyser.pack.path.GeyserPathPackCodec;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads {@link ResourcePack}s within a {@link Path} directory, firing the {@link GeyserLoadResourcePacksEvent}.
 */
public class ResourcePackLoader implements RegistryLoader<Path, Map<UUID, ResourcePack>> {

    static final PathMatcher PACK_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{zip,mcpack}");

    private static final boolean SHOW_RESOURCE_PACK_LENGTH_WARNING = Boolean.parseBoolean(System.getProperty("Geyser.ShowResourcePackLengthWarning", "true"));

    /**
     * Loop through the packs directory and locate valid resource pack files
     */
    @Override
    public Map<UUID, ResourcePack> load(Path directory) {
        Map<UUID, ResourcePack> packMap = new HashMap<>();

        if (!Files.exists(directory)) {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                GeyserLogger.get().error("Could not create packs directory", e);
            }
        }

        List<Path> resourcePacks;
        try (Stream<Path> stream = Files.walk(directory)) {
            resourcePacks = stream.filter(PACK_MATCHER::matches)
                    .collect(Collectors.toCollection(ArrayList::new)); // toList() does not guarantee mutability
        } catch (Exception e) {
            GeyserLogger.get().error("Could not list packs directory", e);

            // Ensure the event is fired even if there was an issue reading
            // from our own resource pack directory. External projects may have
            // resource packs located at different locations.
            resourcePacks = new ArrayList<>();
        }

        // Add custom skull pack
        Path skullResourcePack = SkullResourcePackManager.createResourcePack();
        if (skullResourcePack != null) {
            resourcePacks.add(skullResourcePack);
        }

        GeyserLoadResourcePacksEvent event = new GeyserLoadResourcePacksEvent(resourcePacks);
        GeyserImpl.getInstance().eventBus().fire(event);

        for (Path path : event.resourcePacks()) {
            try {
                GeyserResourcePack pack = readPack(path);
                packMap.put(pack.manifest().header().uuid(), pack);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return packMap;
    }

    /**
     * Reads a resource pack at the given file. Also searches for a file in the same directory, with the same name
     * but suffixed by ".key", containing the content key. If such file does not exist, no content key is stored.
     *
     * @param path the file to read from, in ZIP format
     * @return a {@link ResourcePack} representation
     * @throws IllegalArgumentException if the pack manifest was invalid or there was any processing exception
     */
    public static GeyserResourcePack readPack(Path path) throws IllegalArgumentException {
        if (!path.getFileName().toString().endsWith(".mcpack") && !path.getFileName().toString().endsWith(".zip")) {
            throw new IllegalArgumentException("Resource pack " + path.getFileName() + " must be a .zip or .mcpack file!");
        }

        AtomicReference<GeyserResourcePackManifest> manifestReference = new AtomicReference<>();

        try (ZipFile zip = new ZipFile(path.toFile());
             Stream<? extends ZipEntry> stream = zip.stream()) {
            stream.forEach(x -> {
                String name = x.getName();
                if (SHOW_RESOURCE_PACK_LENGTH_WARNING && name.length() >= 80) {
                    GeyserLogger.get().warning("The resource pack " + path.getFileName()
                            + " has a file in it that meets or exceeds 80 characters in its path (" + name
                            + ", " + name.length() + " characters long). This will cause problems on some Bedrock platforms." +
                            " Please rename it to be shorter, or reduce the amount of folders needed to get to the file.");
                }
                if (name.contains("manifest.json")) {
                    try {
                        GeyserResourcePackManifest manifest = FileUtils.loadJson(zip.getInputStream(x), GeyserResourcePackManifest.class);
                        if (manifest.header().uuid() != null) {
                            manifestReference.set(manifest);
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });

            GeyserResourcePackManifest manifest = manifestReference.get();
            if (manifest == null) {
                throw new IllegalArgumentException(path.getFileName() + " does not contain a valid pack_manifest.json or manifest.json");
            }

            // Check if a file exists with the same name as the resource pack suffixed by .key,
            // and set this as content key. (e.g. test.zip, key file would be test.zip.key)
            Path keyFile = path.resolveSibling(path.getFileName().toString() + ".key");
            String contentKey = Files.exists(keyFile) ? Files.readString(keyFile, StandardCharsets.UTF_8) : "";

            return new GeyserResourcePack(new GeyserPathPackCodec(path), manifest, contentKey);
        } catch (Exception e) {
            throw new IllegalArgumentException(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", path.getFileName()), e);
        }
    }
}
