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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.lifecycle.GeyserLoadResourcePacksEvent;
import org.geysermc.geyser.api.pack.PathPackCodec;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.api.pack.UrlPackCodec;
import org.geysermc.geyser.event.type.GeyserDefineResourcePacksEventImpl;
import org.geysermc.geyser.pack.GeyserResourcePack;
import org.geysermc.geyser.pack.GeyserResourcePackManifest;
import org.geysermc.geyser.pack.SkullResourcePackManager;
import org.geysermc.geyser.pack.path.GeyserPathPackCodec;
import org.geysermc.geyser.pack.url.GeyserUrlPackCodec;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;
import org.geysermc.geyser.util.WebUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads {@link ResourcePack}s within a {@link Path} directory, firing the {@link GeyserDefineResourcePacksEventImpl}.
 */
public class ResourcePackLoader implements RegistryLoader<Path, Map<String, ResourcePack>> {

    /**
     * Used to keep track of remote resource packs that the client rejected.
     * If a client rejects such a pack, it falls back to the old method, and Geyser serves a cached variant.
     */
    private static final Cache<String, UrlPackCodec> CACHED_FAILED_PACKS = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();

    static final PathMatcher PACK_MATCHER = FileSystems.getDefault().getPathMatcher("glob:**.{zip,mcpack}");

    private static final boolean SHOW_RESOURCE_PACK_LENGTH_WARNING = Boolean.parseBoolean(System.getProperty("Geyser.ShowResourcePackLengthWarning", "true"));

    /**
     * Loop through the packs directory and locate valid resource pack files
     */
    @Override
    public Map<String, ResourcePack> load(Path directory) {
        Map<String, ResourcePack> packMap = new Object2ObjectOpenHashMap<>();

        if (!Files.exists(directory)) {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                GeyserImpl.getInstance().getLogger().error("Could not create packs directory", e);
            }
        }

        List<Path> resourcePacks;
        try (Stream<Path> stream = Files.walk(directory)) {
            resourcePacks = stream.filter(PACK_MATCHER::matches)
                    .collect(Collectors.toCollection(ArrayList::new)); // toList() does not guarantee mutability
        } catch (Exception e) {
            GeyserImpl.getInstance().getLogger().error("Could not list packs directory", e);

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

        @SuppressWarnings("deprecation")
        GeyserLoadResourcePacksEvent event = new GeyserLoadResourcePacksEvent(resourcePacks);
        GeyserImpl.getInstance().eventBus().fire(event);

        for (Path path : event.resourcePacks()) {
            try {
                GeyserResourcePack pack = readPack(path);
                packMap.put(pack.manifest().header().uuid().toString(), pack);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Load CDN entries
        packMap.putAll(loadRemotePacks());
        GeyserDefineResourcePacksEventImpl defineEvent = new GeyserDefineResourcePacksEventImpl(packMap);

        return defineEvent.getPacks();
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
        if (!PACK_MATCHER.matches(path)) {
            throw new IllegalArgumentException("Resource pack " + path.getFileName() + " must be a .zip or .mcpack file!");
        }

        ResourcePackManifest manifest = readManifest(path, path.getFileName().toString());
        String contentKey;

        try {
            // Check if a file exists with the same name as the resource pack suffixed by .key,
            // and set this as content key. (e.g. test.zip, key file would be test.zip.key)
            Path keyFile = path.resolveSibling(path.getFileName().toString() + ".key");
            contentKey = Files.exists(keyFile) ? Files.readString(keyFile, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().error("Failed to read content key for resource pack " + path.getFileName(), e);
            contentKey = "";
        }

        return new GeyserResourcePack(new GeyserPathPackCodec(path), manifest, contentKey);
    }

    /**
     * Reads a Resource pack from a URL codec, and returns a resource pack. Unlike {@link ResourcePackLoader#readPack(Path)}
     * this method reads content keys differently.
     *
     * @param codec the URL pack codec with the url to download the pack from
     * @return a {@link GeyserResourcePack} representation
     * @throws IllegalArgumentException if there was an error reading the pack.
     */
    public static GeyserResourcePack readPack(GeyserUrlPackCodec codec) throws IllegalArgumentException {
        Path path = codec.getFallback().path();
        ResourcePackManifest manifest = readManifest(path, codec.url());
        return new GeyserResourcePack(codec, manifest, codec.contentKey());
    }

    private static ResourcePackManifest readManifest(Path path, String packLocation) throws IllegalArgumentException {
        AtomicReference<GeyserResourcePackManifest> manifestReference = new AtomicReference<>();

        try (ZipFile zip = new ZipFile(path.toFile());
             Stream<? extends ZipEntry> stream = zip.stream()) {
            stream.forEach(x -> {
                String name = x.getName();
                if (SHOW_RESOURCE_PACK_LENGTH_WARNING && name.length() >= 80) {
                    GeyserImpl.getInstance().getLogger().warning("The resource pack " + packLocation
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
                throw new IllegalArgumentException(packLocation + " does not contain a valid pack_manifest.json or manifest.json");
            }

            return manifest;
        } catch (Exception e) {
            throw new IllegalArgumentException(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", packLocation), e);
        }
    }

    private Map<String, ResourcePack> loadRemotePacks() {
        GeyserImpl instance = GeyserImpl.getInstance();
        // Unable to make this a static variable, as the test would fail
        final Path cachedCdnPacksDirectory = instance.getBootstrap().getConfigFolder().resolve("cache").resolve("remote_packs");

        if (!Files.exists(cachedCdnPacksDirectory)) {
            try {
                Files.createDirectories(cachedCdnPacksDirectory);
            } catch (IOException e) {
                instance.getLogger().error("Could not create remote pack cache directory", e);
                return new Object2ObjectOpenHashMap<>();
            }
        }

        List<String> remotePackUrls = instance.getConfig().getResourcePackUrls();
        Map<String, ResourcePack> packMap = new Object2ObjectOpenHashMap<>();

        for (String url : remotePackUrls) {
            try {
                GeyserUrlPackCodec codec = new GeyserUrlPackCodec(url);
                ResourcePack pack = codec.create();
                packMap.put(pack.manifest().header().uuid().toString(), pack);
            } catch (Exception e) {
                instance.getLogger().error(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", url));
                if (instance.getLogger().isDebug()) {
                    e.printStackTrace();
                }
            }
        }
        return packMap;
    }

    /**
     * Used when a Bedrock client requests a Bedrock resource pack from the server when it should be downloading it
     * from a remote provider. Since this would be called each time a Bedrock client requests a piece of the Bedrock pack,
     * this uses a cache to ensure we aren't re-checking a dozen times.
     *
     * @param codec the codec of the resource pack that wasn't successfully downloaded by a Bedrock client.
     */
    public static void testUrlPack(UrlPackCodec codec) {
        if (CACHED_FAILED_PACKS.getIfPresent(codec.url()) == null) {
            CACHED_FAILED_PACKS.put(codec.url(), codec);
            GeyserImpl.getInstance().getLogger().warning("A client was not able to download the resource pack at %s. Is it still available? Running check now:");
            downloadPack(codec.url(), true);
        }
    }

    public static CompletableFuture<@Nullable PathPackCodec> downloadPack(String url, boolean testing) throws IllegalArgumentException {
        return CompletableFuture.supplyAsync(() -> {
            Path path = WebUtils.checkUrlAndDownloadRemotePack(url, testing);

            // Already warned about these above
            if (path == null) {
                return null;
            }

            // Check if the pack is a .zip or .mcpack file
            if (!PACK_MATCHER.matches(path)) {
                throw new IllegalArgumentException("Invalid pack format! Not a .zip or .mcpack file.");
            }

            try {
                try (ZipFile zip = new ZipFile(path.toFile())) {
                    if (zip.stream().noneMatch(x -> x.getName().contains("manifest.json"))) {
                        throw new IllegalArgumentException("The pack at the url " + url + " does not contain a manifest file!");
                    }

                    // Check if a "manifest.json" or "pack_manifest.json" file is located directly in the zip... does not work otherwise.
                    // (something like MyZip.zip/manifest.json) will not, but will if it's a subfolder (MyPack.zip/MyPack/manifest.json)
                    if (zip.getEntry("manifest.json") != null || zip.getEntry("pack_manifest.json") != null) {
                        if (testing) {
                            GeyserImpl.getInstance().getLogger().info("The remote resource pack from " + url + " contains a manifest.json file at the root of the zip file. " +
                                    "This may not work for remote packs, and could cause Bedrock clients to fall back to request the pack from the server. " +
                                    "Please put the pack file in a subfolder, and provide that zip in the URL.");
                        }
                    }
                }
            } catch (IOException e) {
                throw new IllegalArgumentException(GeyserLocale.getLocaleStringLog("geyser.resource_pack.broken", url), e);
            }

            if (testing) {
                try {
                    Files.delete(path);
                    return null;
                } catch (IOException e) {
                    throw new IllegalStateException("Could not delete debug pack! " + e.getMessage(), e);
                }
            }

            return new GeyserPathPackCodec(path);
        });
    }

    public static void clear() {
        Registries.RESOURCE_PACKS.get().clear();

        // Now: let's clean up broken remote packs, so we don't cache them
        Path location = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache").resolve("remote_packs");
        for (UrlPackCodec codec : CACHED_FAILED_PACKS.asMap().values()) {
            int hash = codec.url().hashCode();
            Path packLocation = location.resolve(hash + ".zip");
            Path packMetadata = packLocation.resolveSibling(hash + ".metadata");

            try {
                if (packMetadata.toFile().exists()) {
                    Files.delete(packMetadata);
                }
                if (packLocation.toFile().exists()) {
                    Files.delete(packLocation);
                }
            } catch (IOException e) {
                GeyserImpl.getInstance().getLogger().error("Could not delete broken cached resource packs! " + e);
            }
        }
    }
}
