/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.skin;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import it.unimi.dsi.fastutil.bytes.ByteArrays;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.event.bedrock.SessionSkinApplyEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.skin.Cape;
import org.geysermc.geyser.api.skin.Skin;
import org.geysermc.geyser.api.skin.SkinData;
import org.geysermc.geyser.api.skin.SkinGeometry;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.FileUtils;
import org.geysermc.geyser.util.WebUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Predicate;

public class SkinProvider {
    private static ExecutorService EXECUTOR_SERVICE;

    static final Skin EMPTY_SKIN;
    static final Cape EMPTY_CAPE = new Cape("", "no-cape", ByteArrays.EMPTY_ARRAY, true);

    private static final Cache<String, Cape> CACHED_JAVA_CAPES = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
    private static final Cache<String, Skin> CACHED_JAVA_SKINS = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private static final Cache<String, Cape> CACHED_BEDROCK_CAPES = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();
    private static final Cache<String, Skin> CACHED_BEDROCK_SKINS = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    private static final Map<String, CompletableFuture<Cape>> requestedCapes = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<Skin>> requestedSkins = new ConcurrentHashMap<>();

    private static final Map<UUID, SkinGeometry> cachedGeometry = new ConcurrentHashMap<>();

    /**
     * Citizens NPCs use UUID version 2, while legitimate Minecraft players use version 4, and
     * offline mode players use version 3.
     */
    private static final Predicate<UUID> IS_NPC = uuid -> uuid.version() == 2;

    static final SkinGeometry SKULL_GEOMETRY;
    static final SkinGeometry WEARING_CUSTOM_SKULL;
    static final SkinGeometry WEARING_CUSTOM_SKULL_SLIM;

    static {
        // Generate the empty texture to use as an emergency fallback
        final int pink = -524040;
        final int black = -16777216;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(64 * 4 + 64 * 4);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                int rgba;
                if (y > 32) {
                    rgba = x >= 32 ? pink : black;
                } else {
                    rgba = x >= 32 ? black : pink;
                }
                outputStream.write((rgba >> 16) & 0xFF); // Red
                outputStream.write((rgba >> 8) & 0xFF); // Green
                outputStream.write(rgba & 0xFF); // Blue
                outputStream.write((rgba >> 24) & 0xFF); // Alpha
            }
        }
        EMPTY_SKIN = new Skin("geysermc:empty", outputStream.toByteArray(), true);

        /* Load in the custom skull geometry */
        String skullData = new String(FileUtils.readAllBytes("bedrock/skin/geometry.humanoid.customskull.json"), StandardCharsets.UTF_8);
        SKULL_GEOMETRY = new SkinGeometry("{\"geometry\" :{\"default\" :\"geometry.humanoid.customskull\"}}", skullData);

        /* Load in the player head skull geometry */
        String wearingCustomSkull = new String(FileUtils.readAllBytes("bedrock/skin/geometry.humanoid.wearingCustomSkull.json"), StandardCharsets.UTF_8);
        WEARING_CUSTOM_SKULL = new SkinGeometry("{\"geometry\" :{\"default\" :\"geometry.humanoid.wearingCustomSkull\"}}", wearingCustomSkull);
        String wearingCustomSkullSlim = new String(FileUtils.readAllBytes("bedrock/skin/geometry.humanoid.wearingCustomSkullSlim.json"), StandardCharsets.UTF_8);
        WEARING_CUSTOM_SKULL_SLIM = new SkinGeometry("{\"geometry\" :{\"default\" :\"geometry.humanoid.wearingCustomSkullSlim\"}}", wearingCustomSkullSlim);

        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser.getConfig().isAllowThirdPartyEars() || geyser.getConfig().isAllowThirdPartyCapes()) {
            geyser.getLogger().warning("Third-party ears/capes have been removed from Geyser, if you still wish to have this functionality please use the extension: https://github.com/GeyserMC/ThirdPartyCosmetics");
        }
    }

    public static ExecutorService getExecutorService() {
        if (EXECUTOR_SERVICE == null) {
            EXECUTOR_SERVICE = Executors.newFixedThreadPool(14);
        }
        return EXECUTOR_SERVICE;
    }

    public static void shutdown() {
        if (EXECUTOR_SERVICE != null) {
            EXECUTOR_SERVICE.shutdown();
            EXECUTOR_SERVICE = null;
        }
    }

    public static void registerCacheImageTask(GeyserImpl geyser) {
        // Schedule Daily Image Expiry if we are caching them
        if (geyser.getConfig().getCacheImages() > 0) {
            geyser.getScheduledThread().scheduleAtFixedRate(() -> {
                File cacheFolder = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache").resolve("images").toFile();
                if (!cacheFolder.exists()) {
                    return;
                }

                int count = 0;
                final long expireTime = ((long) GeyserImpl.getInstance().getConfig().getCacheImages()) * ((long)1000 * 60 * 60 * 24);
                for (File imageFile : Objects.requireNonNull(cacheFolder.listFiles())) {
                    if (imageFile.lastModified() < System.currentTimeMillis() - expireTime) {
                        //noinspection ResultOfMethodCallIgnored
                        imageFile.delete();
                        count++;
                    }
                }

                if (count > 0) {
                    GeyserImpl.getInstance().getLogger().debug(String.format("Removed %d cached image files as they have expired", count));
                }
            }, 10, 1, TimeUnit.DAYS);
        }
    }

    /**
     * Search our cached database for an already existing, translated skin of this Java URL.
     */
    static Skin getCachedSkin(String skinUrl) {
        return CACHED_JAVA_SKINS.getIfPresent(skinUrl);
    }

    /**
     * If skin data fails to apply, or there is no skin data to apply, determine what skin we should give as a fallback.
     */
    static SkinData determineFallbackSkinData(UUID uuid) {
        Skin skin = null;
        Cape cape = null;
        SkinGeometry geometry = SkinGeometry.WIDE;

        if (GeyserImpl.getInstance().getConfig().getRemote().authType() != AuthType.ONLINE) {
            // Let's see if this player is a Bedrock player, and if so, let's pull their skin.
            GeyserSession session = GeyserImpl.getInstance().connectionByUuid(uuid);
            if (session != null) {
                String skinId = session.getClientData().getSkinId();
                skin = CACHED_BEDROCK_SKINS.getIfPresent(skinId);
                String capeId = session.getClientData().getCapeId();
                cape = CACHED_BEDROCK_CAPES.getIfPresent(capeId);
                geometry = cachedGeometry.getOrDefault(uuid, geometry);
            }
        }

        if (skin == null) {
            // We don't have a skin for the player right now. Fall back to a default.
            ProvidedSkins.ProvidedSkin providedSkin = ProvidedSkins.getDefaultPlayerSkin(uuid);
            skin = providedSkin.getData();
            geometry = providedSkin.isSlim() ? SkinGeometry.SLIM : SkinGeometry.WIDE;
        }

        if (cape == null) {
            cape = EMPTY_CAPE;
        }

        return new SkinData(skin, cape, geometry);
    }

    /**
     * Used as a fallback if an official Java cape doesn't exist for this user.
     */
    @NonNull
    private static Cape getCachedBedrockCape(UUID uuid) {
        GeyserSession session = GeyserImpl.getInstance().connectionByUuid(uuid);
        if (session != null) {
            String capeId = session.getClientData().getCapeId();
            Cape bedrockCape = CACHED_BEDROCK_CAPES.getIfPresent(capeId);
            if (bedrockCape != null) {
                return bedrockCape;
            }
        }
        return EMPTY_CAPE;
    }

    @Nullable
    static Cape getCachedCape(String capeUrl) {
        if (capeUrl == null) {
            return null;
        }
        return CACHED_JAVA_CAPES.getIfPresent(capeUrl);
    }

    static CompletableFuture<SkinData> requestSkinData(PlayerEntity entity, GeyserSession session) {
        SkinManager.GameProfileData data = SkinManager.GameProfileData.from(entity);
        if (data == null) {
            // This player likely does not have a textures property
            return CompletableFuture.completedFuture(determineFallbackSkinData(entity.getUuid()));
        }

        return requestSkinAndCape(entity.getUuid(), data.skinUrl(), data.capeUrl())
                .thenApplyAsync(skinAndCape -> {
                    try {
                        Skin skin = skinAndCape.skin();
                        Cape cape = skinAndCape.cape();
                        SkinGeometry geometry = data.isAlex() ? SkinGeometry.SLIM : SkinGeometry.WIDE;

                        // Whether we should see if this player has a Bedrock skin we should check for on failure of
                        // any skin property
                        boolean checkForBedrock = entity.getUuid().version() != 4;

                        if (cape.failed() && checkForBedrock) {
                            cape = getCachedBedrockCape(entity.getUuid());
                        }

                        // Call event to allow extensions to modify the skin, cape and geo
                        boolean isBedrock = GeyserImpl.getInstance().connectionByUuid(entity.getUuid()) != null;
                        SkinData skinData = new SkinData(skin, cape, geometry);
                        final EventSkinData eventSkinData = new EventSkinData(skinData);
                        GeyserImpl.getInstance().eventBus().fire(new SessionSkinApplyEvent(session, entity.getUsername(), entity.getUuid(), data.isAlex(), isBedrock, skinData) {
                            @Override
                            public SkinData skinData() {
                                return eventSkinData.skinData();
                            }

                            @Override
                            public void skin(@NonNull Skin newSkin) {
                                eventSkinData.skinData(new SkinData(Objects.requireNonNull(newSkin), eventSkinData.skinData().cape(), eventSkinData.skinData().geometry()));
                            }

                            @Override
                            public void cape(@NonNull Cape newCape) {
                                eventSkinData.skinData(new SkinData(eventSkinData.skinData().skin(), Objects.requireNonNull(newCape), eventSkinData.skinData().geometry()));
                            }

                            @Override
                            public void geometry(@NonNull SkinGeometry newGeometry) {
                                eventSkinData.skinData(new SkinData(eventSkinData.skinData().skin(), eventSkinData.skinData().cape(), Objects.requireNonNull(newGeometry)));
                            }
                        });

                        return eventSkinData.skinData();
                    } catch (Exception e) {
                        GeyserImpl.getInstance().getLogger().error(GeyserLocale.getLocaleStringLog("geyser.skin.fail", entity.getUuid()), e);
                    }

                    return new SkinData(skinAndCape.skin(), skinAndCape.cape(), null);
                });
    }

    private static CompletableFuture<SkinAndCape> requestSkinAndCape(UUID playerId, String skinUrl, String capeUrl) {
        return CompletableFuture.supplyAsync(() -> {
            long time = System.currentTimeMillis();

            SkinAndCape skinAndCape = new SkinAndCape(
                    getOrDefault(requestSkin(playerId, skinUrl, false), EMPTY_SKIN, 5),
                    getOrDefault(requestCape(capeUrl, false), EMPTY_CAPE, 5)
            );

            GeyserImpl.getInstance().getLogger().debug("Took " + (System.currentTimeMillis() - time) + "ms for " + playerId);
            return skinAndCape;
        }, getExecutorService());
    }

    static CompletableFuture<Skin> requestSkin(UUID playerId, String textureUrl, boolean newThread) {
        if (textureUrl == null || textureUrl.isEmpty()) return CompletableFuture.completedFuture(EMPTY_SKIN);
        CompletableFuture<Skin> requestedSkin = requestedSkins.get(textureUrl);
        if (requestedSkin != null) {
            // already requested
            return requestedSkin;
        }

        Skin cachedSkin = CACHED_JAVA_SKINS.getIfPresent(textureUrl);
        if (cachedSkin != null) {
            return CompletableFuture.completedFuture(cachedSkin);
        }

        CompletableFuture<Skin> future;
        if (newThread) {
            future = CompletableFuture.supplyAsync(() -> supplySkin(playerId, textureUrl), getExecutorService())
                    .whenCompleteAsync((skin, throwable) -> {
                        CACHED_JAVA_SKINS.put(textureUrl, skin);
                        requestedSkins.remove(textureUrl);
                    });
            requestedSkins.put(textureUrl, future);
        } else {
            Skin skin = supplySkin(playerId, textureUrl);
            future = CompletableFuture.completedFuture(skin);
            CACHED_JAVA_SKINS.put(textureUrl, skin);
        }
        return future;
    }

    private static CompletableFuture<Cape> requestCape(String capeUrl, boolean newThread) {
        if (capeUrl == null || capeUrl.isEmpty()) return CompletableFuture.completedFuture(EMPTY_CAPE);
        CompletableFuture<Cape> requestedCape = requestedCapes.get(capeUrl);
        if (requestedCape != null) {
            return requestedCape;
        }

        Cape cachedCape = CACHED_JAVA_CAPES.getIfPresent(capeUrl);
        if (cachedCape != null) {
            return CompletableFuture.completedFuture(cachedCape);
        }

        CompletableFuture<Cape> future;
        if (newThread) {
            future = CompletableFuture.supplyAsync(() -> supplyCape(capeUrl), getExecutorService())
                    .whenCompleteAsync((cape, throwable) -> {
                        CACHED_JAVA_CAPES.put(capeUrl, cape);
                        requestedCapes.remove(capeUrl);
                    });
            requestedCapes.put(capeUrl, future);
        } else {
            Cape cape = supplyCape(capeUrl); // blocking
            future = CompletableFuture.completedFuture(cape);
            CACHED_JAVA_CAPES.put(capeUrl, cape);
        }
        return future;
    }

    static void storeBedrockSkin(UUID playerID, String skinId, byte[] skinData) {
        Skin skin = new Skin(skinId, skinData);
        CACHED_BEDROCK_SKINS.put(skin.textureUrl(), skin);
    }

    static void storeBedrockCape(String capeId, byte[] capeData) {
        Cape cape = new Cape(capeId, capeId, capeData);
        CACHED_BEDROCK_CAPES.put(capeId, cape);
    }

    static void storeBedrockGeometry(UUID playerID, byte[] geometryName, byte[] geometryData) {
        SkinGeometry geometry = new SkinGeometry(new String(geometryName), new String(geometryData));
        cachedGeometry.put(playerID, geometry);
    }

    private static Skin supplySkin(UUID uuid, String textureUrl) {
        try {
            byte[] skin = requestImageData(textureUrl, false);
            return new Skin(textureUrl, skin);
        } catch (Exception ignored) {} // just ignore I guess

        return new Skin("empty", EMPTY_SKIN.skinData(), true);
    }

    private static Cape supplyCape(String capeUrl) {
        byte[] cape = EMPTY_CAPE.capeData();
        try {
            cape = requestImageData(capeUrl, true);
        } catch (Exception ignored) {
        } // just ignore I guess

        String[] urlSection = capeUrl.split("/"); // A real url is expected at this stage

        return new Cape(
                capeUrl,
                urlSection[urlSection.length - 1], // get the texture id and use it as cape id
                cape,
                cape.length == 0
        );
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static BufferedImage requestImage(String imageUrl, boolean isCape) throws IOException {
        BufferedImage image = null;

        // First see if we have a cached file. We also update the modification stamp so we know when the file was last used
        File imageFile = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache").resolve("images").resolve(UUID.nameUUIDFromBytes(imageUrl.getBytes()) + ".png").toFile();
        if (imageFile.exists()) {
            try {
                GeyserImpl.getInstance().getLogger().debug("Reading cached image from file " + imageFile.getPath() + " for " + imageUrl);
                imageFile.setLastModified(System.currentTimeMillis());
                image = ImageIO.read(imageFile);
            } catch (IOException ignored) {}
        }

        // If no image we download it
        if (image == null) {
            image = downloadImage(imageUrl);
            GeyserImpl.getInstance().getLogger().debug("Downloaded " + imageUrl);

            // Write to cache if we are allowed
            if (GeyserImpl.getInstance().getConfig().getCacheImages() > 0) {
                imageFile.getParentFile().mkdirs();
                try {
                    ImageIO.write(image, "png", imageFile);
                    GeyserImpl.getInstance().getLogger().debug("Writing cached skin to file " + imageFile.getPath() + " for " + imageUrl);
                } catch (IOException e) {
                    GeyserImpl.getInstance().getLogger().error("Failed to write cached skin to file " + imageFile.getPath() + " for " + imageUrl);
                }
            }
        }

        // if the requested image is a cape
        if (isCape) {
            if (image.getWidth() > 64 || image.getHeight() > 32) {
                // Prevent weirdly-scaled capes from being cut off
                BufferedImage newImage = new BufferedImage(128, 64, BufferedImage.TYPE_INT_ARGB);
                Graphics g = newImage.createGraphics();
                g.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
                g.dispose();
                image.flush();
                image = scale(newImage, 64, 32);
            } else if (image.getWidth() < 64 || image.getHeight() < 32) {
                // Bedrock doesn't like smaller-sized capes, either.
                BufferedImage newImage = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
                Graphics g = newImage.createGraphics();
                g.drawImage(image, 0, 0, image.getWidth(), image.getHeight(), null);
                g.dispose();
                image.flush();
                image = newImage;
            }
        } else {
            // Very rarely, skins can be larger than Minecraft's default.
            // Bedrock will not render anything above a width of 128.
            if (image.getWidth() > 128) {
                // On Height: Scale by the amount we divided width by, or simply cut down to 128
                image = scale(image, 128, image.getHeight() >= 256 ? (image.getHeight() / (image.getWidth() / 128)) : 128);
            }

            // TODO remove alpha channel
        }

        return image;
    }

    private static byte[] requestImageData(String imageUrl, boolean isCape) throws Exception {
        BufferedImage image = requestImage(imageUrl, isCape);
        byte[] data = bufferedImageToImageData(image);
        image.flush();
        return data;
    }

    /**
     * Request textures from a player's UUID
     *
     * @param uuid the player's UUID without any hyphens
     * @return a completable GameProfile with textures included
     */
    public static CompletableFuture<@Nullable String> requestTexturesFromUUID(String uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonNode node = WebUtils.getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
                JsonNode properties = node.get("properties");
                if (properties == null) {
                    GeyserImpl.getInstance().getLogger().debug("No properties found in Mojang response for " + uuid);
                    return null;
                }
                return node.get("properties").get(0).get("value").asText();
            } catch (Exception e) {
                GeyserImpl.getInstance().getLogger().debug("Unable to request textures for " + uuid);
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    e.printStackTrace();
                }
                return null;
            }
        }, getExecutorService());
    }

    /**
     * Request textures from a player's username
     *
     * @param username the player's username
     * @return a completable GameProfile with textures included
     */
    public static CompletableFuture<@Nullable String> requestTexturesFromUsername(String username) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Offline skin, or no present UUID
                JsonNode node = WebUtils.getJson("https://api.mojang.com/users/profiles/minecraft/" + username);
                JsonNode id = node.get("id");
                if (id == null) {
                    GeyserImpl.getInstance().getLogger().debug("No UUID found in Mojang response for " + username);
                    return null;
                }
                return id.asText();
            } catch (Exception e) {
                if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                    e.printStackTrace();
                }
                return null;
            }
        }, getExecutorService()).thenCompose(uuid -> {
            if (uuid == null) {
                return CompletableFuture.completedFuture(null);
            }
            return requestTexturesFromUUID(uuid);
        });
    }

    private static BufferedImage downloadImage(String imageUrl) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(imageUrl).openConnection();
        con.setRequestProperty("User-Agent", WebUtils.getUserAgent());
        con.setConnectTimeout(10000);
        con.setReadTimeout(10000);

        BufferedImage image = ImageIO.read(con.getInputStream());

        if (image == null) {
            throw new IllegalArgumentException("Failed to read image from: %s".formatted(imageUrl));
        }
        return image;
    }

    public static BufferedImage scale(BufferedImage bufferedImage, int newWidth, int newHeight) {
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(bufferedImage, 0, 0, newWidth, newHeight, null);
        g2.dispose();
        bufferedImage.flush();
        return resized;
    }

    /**
     * Get the RGBA int for a given index in some image data
     *
     * @param index Index to get
     * @param data Image data to find in
     * @return An int representing RGBA
     */
    private static int getRGBA(int index, byte[] data) {
        return (data[index] & 0xFF) << 16 | (data[index + 1] & 0xFF) << 8 |
                data[index + 2] & 0xFF | (data[index + 3] & 0xFF) << 24;
    }

    /**
     * Convert a byte[] to a BufferedImage
     *
     * @param imageData The byte[] to convert
     * @param imageWidth The width of the target image
     * @param imageHeight The height of the target image
     * @return The converted BufferedImage
     */
    public static BufferedImage imageDataToBufferedImage(byte[] imageData, int imageWidth, int imageHeight) {
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        int index = 0;
        for (int y = 0; y < imageHeight; y++) {
            for (int x = 0; x < imageWidth; x++) {
                image.setRGB(x, y, getRGBA(index, imageData));
                index += 4;
            }
        }

        return image;
    }

    /**
     * Convert a BufferedImage to a byte[]
     *
     * @param image The BufferedImage to convert
     * @return The converted byte[]
     */
    public static byte[] bufferedImageToImageData(BufferedImage image) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(image.getWidth() * 4 + image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgba = image.getRGB(x, y);
                outputStream.write((rgba >> 16) & 0xFF);
                outputStream.write((rgba >> 8) & 0xFF);
                outputStream.write(rgba & 0xFF);
                outputStream.write((rgba >> 24) & 0xFF);
            }
        }
        return outputStream.toByteArray();
    }

    public static <T> T getOrDefault(CompletableFuture<T> future, T defaultValue, int timeoutInSeconds) {
        try {
            return future.get(timeoutInSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {}
        return defaultValue;
    }

    public record SkinAndCape(Skin skin, Cape cape) {
    }

    public static class EventSkinData {
        private SkinData skinData;

        public EventSkinData(SkinData skinData) {
            this.skinData = skinData;
        }

        public SkinData skinData() {
            return skinData;
        }

        public void skinData(SkinData skinData) {
            this.skinData = skinData;
        }
    }
}
