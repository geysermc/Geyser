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

package org.geysermc.geyser.pack;

import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.CustomSkull;
import org.geysermc.geyser.skin.SkinProvider;
import org.geysermc.geyser.util.FileUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class SkullResourcePackManager {

    private static final long RESOURCE_PACK_VERSION = 8;

    private static final Path SKULL_SKIN_CACHE_PATH = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache").resolve("player_skulls");

    public static final Map<String, Path> SKULL_SKINS = new Object2ObjectOpenHashMap<>();

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static @Nullable Path createResourcePack() {
        Path cachePath = GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("cache");
        try {
            Files.createDirectories(cachePath);
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().severe("Unable to create directories for player skull resource pack!", e);
            return null;
        }
        cleanSkullSkinCache();

        Path packPath = cachePath.resolve("player_skulls.mcpack");
        File packFile = packPath.toFile();
        if (BlockRegistries.CUSTOM_SKULLS.get().isEmpty() || !GeyserImpl.getInstance().config().addNonBedrockItems()) {
            packFile.delete(); // No need to keep resource pack
            return null;
        }
        if (packFile.exists() && canReusePack(packFile)) {
            GeyserImpl.getInstance().getLogger().info("Reusing cached player skull resource pack.");
            return packPath;
        }

        // We need to create the resource pack from scratch
        GeyserImpl.getInstance().getLogger().info("Creating skull resource pack.");
        packFile.delete();
        try (ZipOutputStream zipOS = new ZipOutputStream(Files.newOutputStream(packPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE))) {
            addBaseResources(zipOS);
            addSkinTextures(zipOS);
            addAttachables(zipOS);
            GeyserImpl.getInstance().getLogger().info("Finished creating skull resource pack.");
            return packPath;
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().severe("Unable to create player skull resource pack!", e);
            GeyserImpl.getInstance().getLogger().severe("Bedrock players will see dirt blocks instead of custom skull blocks.");
            packFile.delete();
        }
        return null;
    }

    public static void cacheSkullSkin(String skinHash) throws IOException {
        String skinUrl = Constants.MINECRAFT_SKIN_SERVER_URL + skinHash;
        Path skinPath = SKULL_SKINS.get(skinHash);
        if (skinPath != null) {
            return;
        }

        Files.createDirectories(SKULL_SKIN_CACHE_PATH);
        skinPath = SKULL_SKIN_CACHE_PATH.resolve(skinHash + ".png");
        if (Files.exists(skinPath)) {
            SKULL_SKINS.put(skinHash, skinPath);
            return;
        }

        BufferedImage image = SkinProvider.requestImage(skinUrl, false);
        // Resize skins to 48x16 to save on space and memory
        BufferedImage skullTexture = new BufferedImage(48, 16, image.getType());
        // Reorder skin parts to fit into the space
        // Right, Front, Left, Back, Top, Bottom - head
        // Right, Front, Left, Back, Top, Bottom - hat
        Graphics g = skullTexture.createGraphics();
        // Right, Front, Left, Back of the head
        g.drawImage(image, 0, 0, 32, 8, 0, 8, 32, 16, null);
        // Right, Front, Left, Back of the hat
        g.drawImage(image, 0, 8, 32, 16, 32, 8, 64, 16, null);
        // Top and bottom of the head
        g.drawImage(image, 32, 0, 48, 8, 8, 0, 24, 8, null);
        // Top and bottom of the hat
        g.drawImage(image, 32, 8, 48, 16, 40, 0, 56, 8, null);
        g.dispose();
        image.flush();

        ImageIO.write(skullTexture, "png", skinPath.toFile());
        SKULL_SKINS.put(skinHash, skinPath);
        GeyserImpl.getInstance().getLogger().debug("Cached player skull to " + skinPath + " for " + skinHash);
    }

    public static void cleanSkullSkinCache() {
        // No need to clean up if skin cache does not exist
        if (!Files.exists(SKULL_SKIN_CACHE_PATH)) {
            return;
        }

        try (Stream<Path> stream = Files.list(SKULL_SKIN_CACHE_PATH)) {
            int removeCount = 0;
            for (Path path : stream.toList()) {
                String skinHash = path.getFileName().toString();
                skinHash = skinHash.substring(0, skinHash.length() - ".png".length());
                if (!SKULL_SKINS.containsKey(skinHash) && path.toFile().delete()) {
                    removeCount++;
                }
            }
            if (removeCount != 0) {
                GeyserImpl.getInstance().getLogger().debug("Removed " + removeCount + " unnecessary skull skins.");
            }
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().debug("Unable to clean up skull skin cache.");
            if (GeyserImpl.getInstance().config().debugMode()) {
                e.printStackTrace();
            }
        }
    }

    private static void addBaseResources(ZipOutputStream zipOS) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GeyserImpl.getInstance().getBootstrap().getResourceOrThrow("bedrock/skull_resource_pack_files.txt")))) {
            List<String> lines = reader.lines().toList();
            for (String path : lines) {
                ZipEntry entry = new ZipEntry(path);

                zipOS.putNextEntry(entry);
                String resourcePath = "bedrock/" + path;
                switch (path) {
                    case "skull_resource_pack/manifest.json" ->
                            fillTemplate(zipOS, resourcePath, SkullResourcePackManager::fillManifestJson);
                    case "skull_resource_pack/textures/terrain_texture.json" ->
                            fillTemplate(zipOS, resourcePath, SkullResourcePackManager::fillTerrainTextureJson);
                    default -> zipOS.write(FileUtils.readAllBytes(resourcePath));
                }
                zipOS.closeEntry();
            }

            addFloorGeometries(zipOS);

            ZipEntry entry = new ZipEntry("skull_resource_pack/pack_icon.png");
            zipOS.putNextEntry(entry);
            zipOS.write(FileUtils.readAllBytes("assets/geyser/icon.png"));
            zipOS.closeEntry();
        }
    }

    private static void addFloorGeometries(ZipOutputStream zipOS) throws IOException {
        String template = FileUtils.readToString("bedrock/skull_resource_pack/models/blocks/player_skull_floor.geo.json");
        String[] quadrants = {"a", "b", "c", "d"};
        for (int i = 0; i < quadrants.length; i++) {
            String quadrant = quadrants[i];
            float yRotation = i * 22.5f;
            String contents = template
                    .replace("${quadrant}", quadrant)
                    .replace("${y_rotation}", String.valueOf(yRotation));

            ZipEntry entry = new ZipEntry("skull_resource_pack/models/blocks/player_skull_floor_" + quadrant + ".geo.json");
            zipOS.putNextEntry(entry);
            zipOS.write(contents.getBytes(StandardCharsets.UTF_8));
            zipOS.closeEntry();
        }
    }

    private static void addAttachables(ZipOutputStream zipOS) throws IOException {
        String template = FileUtils.readToString("bedrock/skull_resource_pack/attachables/template_attachable.json");
        for (CustomSkull skull : BlockRegistries.CUSTOM_SKULLS.get().values()) {
            ZipEntry entry = new ZipEntry("skull_resource_pack/attachables/" + truncateHash(skull.getSkinHash()) + ".json");
            zipOS.putNextEntry(entry);
            zipOS.write(fillAttachableJson(template, skull).getBytes(StandardCharsets.UTF_8));
            zipOS.closeEntry();
        }
    }

    private static void addSkinTextures(ZipOutputStream zipOS) throws IOException {
        for (Path skinPath : SKULL_SKINS.values()) {
            ZipEntry entry = new ZipEntry("skull_resource_pack/textures/blocks/" + truncateHash(skinPath.getFileName().toString()) + ".png");
            zipOS.putNextEntry(entry);
            try (InputStream stream = Files.newInputStream(skinPath)) {
                stream.transferTo(zipOS);
            }
            zipOS.closeEntry();
        }
    }

    private static void fillTemplate(ZipOutputStream zipOS, String path, UnaryOperator<String> filler) throws IOException {
        String template = FileUtils.readToString(path);
        String result = filler.apply(template);
        zipOS.write(result.getBytes(StandardCharsets.UTF_8));
    }

    private static String fillAttachableJson(String template, CustomSkull skull) {
        return template.replace("${identifier}", skull.getCustomBlockData().identifier())
                .replace("${texture}", truncateHash(skull.getSkinHash()));
    }

    private static String fillManifestJson(String template) {
        Pair<UUID, UUID> uuids = generatePackUUIDs();
        return template.replace("${uuid1}", uuids.first().toString())
                .replace("${uuid2}", uuids.second().toString());
    }

    private static String fillTerrainTextureJson(String template) {
        StringBuilder textures = new StringBuilder();
        for (String skinHash : SKULL_SKINS.keySet()) {
            String texture = String.format("\"geyser.%s_player_skin\":{\"textures\":\"textures/blocks/%s\"},\n", skinHash, truncateHash(skinHash));
            textures.append(texture);
        }
        if (textures.length() != 0) {
            // Remove trailing comma
            textures.delete(textures.length() - 2, textures.length());
        }
        return template.replace("${texture_data}", textures);
    }

    private static Pair<UUID, UUID> generatePackUUIDs() {
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < 8; i++) {
                md.update((byte) ((RESOURCE_PACK_VERSION >> (i * 8)) & 0xFF));
            }
            SKULL_SKINS.keySet().stream()
                    .sorted()
                    .map(hash -> hash.getBytes(StandardCharsets.UTF_8))
                    .forEach(md::update);

            ByteBuffer skinHashes = ByteBuffer.wrap(md.digest());
            uuid1 = new UUID(skinHashes.getLong(), skinHashes.getLong());
            uuid2 = new UUID(skinHashes.getLong(), skinHashes.getLong());
        } catch (NoSuchAlgorithmException e) {
            GeyserImpl.getInstance().getLogger().severe("Unable to get SHA-256 Message Digest instance! Bedrock players will have to re-downloaded the player skull resource pack after each server restart.", e);
        }

        return Pair.of(uuid1, uuid2);
    }

    private static boolean canReusePack(File packFile) {
        Pair<UUID, UUID> uuids = generatePackUUIDs();
        try (ZipFile zipFile = new ZipFile(packFile)) {
            Optional<? extends ZipEntry> manifestEntry = zipFile.stream()
                    .filter(entry -> entry.getName().contains("manifest.json"))
                    .findFirst();
            if (manifestEntry.isPresent()) {
                GeyserResourcePackManifest manifest = FileUtils.loadJson(zipFile.getInputStream(manifestEntry.get()), GeyserResourcePackManifest.class);
                if (!uuids.first().equals(manifest.header().uuid())) {
                    return false;
                }
                Optional<UUID> resourceUUID = manifest.modules().stream()
                        .filter(module -> "resources".equals(module.type()))
                        .findFirst()
                        .map(ResourcePackManifest.Module::uuid);
                return resourceUUID.isPresent() && uuids.second().equals(resourceUUID.get());
            }
        } catch (IOException e) {
            GeyserImpl.getInstance().getLogger().debug("Cached player skull resource pack was invalid! The pack will be recreated.");
        }
        return false;
    }

    private static String truncateHash(String hash) {
        return hash.substring(0, Math.min(hash.length(), 32));
    }
}
