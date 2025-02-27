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

package org.geysermc.geyser.util;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.GeyserLocale;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

/**
 * Implementation note: try to design processes to fail softly if the client jar can't be downloaded,
 * either if Mojang is down or internet access to Mojang is spotty.
 */
public final class AssetUtils {
    private static final String CLIENT_JAR_HASH_FILE = "client_jar.hash";

    private static final Map<String, Asset> ASSET_MAP = new HashMap<>();

    private static VersionDownload CLIENT_JAR_INFO;

    private static final Queue<ClientJarTask> CLIENT_JAR_TASKS = new ArrayDeque<>();
    /**
     * Download the client jar even if the hash is correct
     */
    private static boolean FORCE_DOWNLOAD_JAR = false;

    public static Asset getAsset(String name) {
        return ASSET_MAP.get(name);
    }

    public static boolean isAssetKnown(String name) {
        return ASSET_MAP.containsKey(name);
    }

    /**
     * Add task to be ran after the client jar is downloaded or found to be cached.
     *
     * @param required if set to true, the client jar will always be downloaded, even if a pre-existing hash is matched.
     *                 This means an asset or texture is missing.
     */
    public static void addTask(boolean required, ClientJarTask task) {
        CLIENT_JAR_TASKS.add(task);
        FORCE_DOWNLOAD_JAR |= required;
    }

    /**
     * Fetch the latest versions asset cache from Mojang so we can grab the locale files later
     */
    public static CompletableFuture<Void> generateAssetCache() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Get the version manifest from Mojang
                VersionManifest versionManifest = GeyserImpl.JSON_MAPPER.readValue(
                        WebUtils.getBody("https://launchermeta.mojang.com/mc/game/version_manifest.json"), VersionManifest.class);

                // Get the url for the latest version of the games manifest
                String latestInfoURL = "";
                for (Version version : versionManifest.getVersions()) {
                    if (version.getId().equals(GameProtocol.getJavaCodec().getMinecraftVersion())) {
                        latestInfoURL = version.getUrl();
                        break;
                    }
                }

                // Make sure we definitely got a version
                if (latestInfoURL.isEmpty()) {
                    throw new Exception(GeyserLocale.getLocaleStringLog("geyser.locale.fail.latest_version"));
                }

                // Get the individual version manifest
                VersionInfo versionInfo = GeyserImpl.JSON_MAPPER.readValue(WebUtils.getBody(latestInfoURL), VersionInfo.class);

                // Get the client jar for use when downloading the en_us locale
                GeyserLogger.get().debug(GeyserImpl.JSON_MAPPER.writeValueAsString(versionInfo.getDownloads()));
                CLIENT_JAR_INFO = versionInfo.getDownloads().get("client");
                GeyserLogger.get().debug(GeyserImpl.JSON_MAPPER.writeValueAsString(CLIENT_JAR_INFO));

                // Get the assets list
                JsonNode assets = GeyserImpl.JSON_MAPPER.readTree(WebUtils.getBody(versionInfo.getAssetIndex().getUrl())).get("objects");

                // Put each asset into an array for use later
                Iterator<Map.Entry<String, JsonNode>> assetIterator = assets.fields();
                while (assetIterator.hasNext()) {
                    Map.Entry<String, JsonNode> entry = assetIterator.next();
                    if (!entry.getKey().startsWith("minecraft/lang/")) {
                        // No need to cache non-language assets as we don't use them
                        continue;
                    }

                    Asset asset = GeyserImpl.JSON_MAPPER.treeToValue(entry.getValue(), Asset.class);
                    ASSET_MAP.put(entry.getKey(), asset);
                }

            } catch (Exception e) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.locale.fail.asset_cache", (!e.getMessage().isEmpty() ? e.getMessage() : e.getStackTrace())));
            }
            return null;
        });
    }

    public static void downloadAndRunClientJarTasks() {
        if (CLIENT_JAR_INFO == null) {
            // Likely failed to download
            GeyserLogger.get().debug("Skipping en_US hash check as client jar is null.");
            return;
        }

        if (!FORCE_DOWNLOAD_JAR) { // Don't bother checking the hash if we need to download new files anyway.
            String curHash = null;
            try {
                File hashFile = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache").resolve(CLIENT_JAR_HASH_FILE).toFile();
                if (hashFile.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(hashFile))) {
                        curHash = br.readLine().trim();
                    }
                }
            } catch (IOException ignored) { }
            String targetHash = CLIENT_JAR_INFO.getSha1();
            if (targetHash.equals(curHash)) {
                // Just run all tasks - no new download required
                ClientJarTask task;
                while ((task = CLIENT_JAR_TASKS.poll()) != null) {
                    task.whenDone.run();
                }
                return;
            }
        }

        try {
            // Let the user know we are downloading the JAR
            GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.locale.download.en_us"));
            GeyserLogger.get().debug("Download URL: " + CLIENT_JAR_INFO.getUrl());

            Path tmpFilePath = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("tmp_locale.jar");
            WebUtils.downloadFile(CLIENT_JAR_INFO.getUrl(), tmpFilePath.toString());

            // Load in the JAR as a zip and extract the files
            try (ZipFile localeJar = new ZipFile(tmpFilePath.toString())) {
                ClientJarTask task;
                while ((task = CLIENT_JAR_TASKS.poll()) != null) {
                    try (InputStream fileStream = localeJar.getInputStream(localeJar.getEntry(task.asset))) {
                        task.ifNewDownload.accept(fileStream);
                        task.whenDone.run();
                    }
                }
            }

            // Store the latest jar hash
            Path cache = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache");
            Files.createDirectories(cache);
            FileUtils.writeFile(cache.resolve(CLIENT_JAR_HASH_FILE).toString(), CLIENT_JAR_INFO.getSha1().toCharArray());

            // Delete the nolonger needed client/server jar
            Files.delete(tmpFilePath);

            GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.locale.download.en_us.done"));
        } catch (Exception e) {
            GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.locale.fail.en_us"), e);
        }
    }

    public static void saveFile(Path location, InputStream fileStream) throws IOException {
        try (OutputStream outStream = Files.newOutputStream(location)) {

            // Write the file to the locale dir
            byte[] buf = new byte[fileStream.available()];
            int length;
            while ((length = fileStream.read(buf)) != -1) {
                outStream.write(buf, 0, length);
            }

            // Flush all changes to disk and cleanup
            outStream.flush();
        }
    }

    /**
     * A process that requires we download the client jar.
     * Designed to accommodate Geyser updates that require more assets from the jar.
     */
    public record ClientJarTask(String asset, InputStreamConsumer ifNewDownload, Runnable whenDone) {
    }

    @FunctionalInterface
    public interface InputStreamConsumer {
        void accept(InputStream stream) throws IOException;
    }

    /* Classes that map to JSON files served by Mojang */

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class VersionManifest {
        @JsonProperty("latest")
        private LatestVersion latestVersion;

        @JsonProperty("versions")
        private List<Version> versions;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class LatestVersion {
        @JsonProperty("release")
        private String release;

        @JsonProperty("snapshot")
        private String snapshot;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class Version {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("url")
        private String url;

        @JsonProperty("time")
        private String time;

        @JsonProperty("releaseTime")
        private String releaseTime;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class VersionInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("type")
        private String type;

        @JsonProperty("time")
        private String time;

        @JsonProperty("releaseTime")
        private String releaseTime;

        @JsonProperty("assetIndex")
        private AssetIndex assetIndex;

        @JsonProperty("downloads")
        private Map<String, VersionDownload> downloads;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class VersionDownload {
        @JsonProperty("sha1")
        private String sha1;

        @JsonProperty("size")
        private int size;

        @JsonProperty("url")
        private String url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    static class AssetIndex {
        @JsonProperty("id")
        private String id;

        @JsonProperty("sha1")
        private String sha1;

        @JsonProperty("size")
        private int size;

        @JsonProperty("totalSize")
        private int totalSize;

        @JsonProperty("url")
        private String url;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @Getter
    public static class Asset {
        @JsonProperty("hash")
        private String hash;

        @JsonProperty("size")
        private int size;
    }

    private AssetUtils() {
    }
}
