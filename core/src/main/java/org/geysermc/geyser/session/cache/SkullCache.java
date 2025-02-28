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

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.block.custom.CustomBlockState;
import org.geysermc.geyser.entity.type.player.SkullPlayerEntity;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.WallSkullBlock;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.CustomSkull;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.skin.SkinManager;

import java.io.IOException;
import java.util.*;

public class SkullCache {
    private final int maxVisibleSkulls;
    private final boolean cullingEnabled;

    private final int skullRenderDistanceSquared;

    @Getter
    private final Map<Vector3i, Skull> skulls = new Object2ObjectOpenHashMap<>();

    private final List<Skull> inRangeSkulls = new ArrayList<>();

    private int totalSkullEntities = 0;

    private final GeyserSession session;

    private Vector3f lastPlayerPosition;

    public SkullCache(GeyserSession session) {
        this.session = session;
        this.maxVisibleSkulls = session.getGeyser().config().advanced().maxVisibleCustomSkulls();
        this.cullingEnabled = this.maxVisibleSkulls != -1;

        // Normal skulls are not rendered beyond 64 blocks
        int distance = Math.min(session.getGeyser().config().advanced().customSkullRenderDistance(), 64);
        this.skullRenderDistanceSquared = distance * distance;
    }

    public Skull putSkull(Vector3i position, UUID uuid, String texturesProperty, BlockState blockState) {
        Skull skull = skulls.computeIfAbsent(position, Skull::new);
        skull.uuid = uuid;
        if (!texturesProperty.equals(skull.texturesProperty)) {
            skull.texturesProperty = texturesProperty;
            skull.skinHash = null;
            try {
                SkinManager.GameProfileData gameProfileData = SkinManager.GameProfileData.loadFromJson(texturesProperty);
                if (gameProfileData != null && gameProfileData.skinUrl() != null) {
                    String skinUrl = gameProfileData.skinUrl();
                    skull.skinHash = skinUrl.substring(skinUrl.lastIndexOf('/') + 1);
                } else {
                    session.getGeyser().getLogger().debug("Player skull with invalid Skin tag: " + position + " Textures: " + texturesProperty);
                }
            } catch (IOException e) {
                session.getGeyser().getLogger().debug("Player skull with invalid Skin tag: " + position + " Textures: " + texturesProperty);
                if (GeyserImpl.getInstance().config().debugMode()) {
                    e.printStackTrace();
                }
            }
        }
        skull.blockState = blockState;
        skull.blockDefinition = translateCustomSkull(skull.skinHash, blockState);

        if (skull.blockDefinition != null) {
            reassignSkullEntity(skull);
            return skull;
        }

        if (skull.entity != null) {
            skull.entity.updateSkull(skull);
        } else {
            if (!cullingEnabled) {
                assignSkullEntity(skull);
                return skull;
            }
            if (lastPlayerPosition == null) {
                return skull;
            }
            skull.distanceSquared = position.distanceSquared(lastPlayerPosition.getX(), lastPlayerPosition.getY(), lastPlayerPosition.getZ());
            if (skull.distanceSquared < skullRenderDistanceSquared) {
                // Keep list in order
                int i = Collections.binarySearch(inRangeSkulls, skull, Comparator.comparingInt(Skull::getDistanceSquared));
                if (i < 0) { // skull.distanceSquared is a new distance value
                    i = -i - 1;
                }
                inRangeSkulls.add(i, skull);

                if (i < maxVisibleSkulls) {
                    // Reassign entity from the farthest skull to this one
                    if (inRangeSkulls.size() > maxVisibleSkulls) {
                        freeSkullEntity(inRangeSkulls.get(maxVisibleSkulls));
                    }
                    assignSkullEntity(skull);
                }
            }
        }
        return skull;
    }

    public void removeSkull(Vector3i position) {
        Skull skull = skulls.remove(position);
        if (skull != null) {
            reassignSkullEntity(skull);
        }
    }

    public Skull updateSkull(Vector3i position, BlockState blockState) {
        Skull skull = skulls.get(position);
        if (skull != null) {
            putSkull(position, skull.uuid, skull.texturesProperty, blockState);
        }
        return skull;
    }

    public void updateVisibleSkulls() {
        if (cullingEnabled) {
            // No need to recheck skull visibility for small movements
            if (lastPlayerPosition != null && session.getPlayerEntity().getPosition().distanceSquared(lastPlayerPosition) < 4) {
                return;
            }
            lastPlayerPosition = session.getPlayerEntity().getPosition();

            inRangeSkulls.clear();
            for (Skull skull : skulls.values()) {
                if (skull.blockDefinition != null) {
                    continue;
                }

                skull.distanceSquared = skull.position.distanceSquared(lastPlayerPosition.getX(), lastPlayerPosition.getY(), lastPlayerPosition.getZ());
                if (skull.distanceSquared > skullRenderDistanceSquared) {
                    freeSkullEntity(skull);
                } else {
                    inRangeSkulls.add(skull);
                }
            }
            inRangeSkulls.sort(Comparator.comparingInt(Skull::getDistanceSquared));

            for (int i = inRangeSkulls.size() - 1; i >= 0; i--) {
                if (i < maxVisibleSkulls) {
                    assignSkullEntity(inRangeSkulls.get(i));
                } else {
                    freeSkullEntity(inRangeSkulls.get(i));
                }
            }
        }
    }

    private void assignSkullEntity(Skull skull) {
        if (skull.entity != null) {
            return;
        }
        if (!cullingEnabled || totalSkullEntities < maxVisibleSkulls) {
            // Create a new entity
            long geyserId = session.getEntityCache().getNextEntityId().incrementAndGet();
            skull.entity = new SkullPlayerEntity(session, geyserId);
            skull.entity.spawnEntity();
            skull.entity.updateSkull(skull);
            totalSkullEntities++;
        }
    }

    private void freeSkullEntity(Skull skull) {
        if (skull.entity != null) {
            skull.entity.despawnEntity();
            totalSkullEntities--;
            skull.entity = null;
        }
    }

    private void reassignSkullEntity(Skull skull) {
        boolean hadEntity = skull.entity != null;
        freeSkullEntity(skull);

        if (cullingEnabled) {
            inRangeSkulls.remove(skull);
            if (hadEntity && inRangeSkulls.size() >= maxVisibleSkulls) {
                // Reassign entity to the closest skull without an entity
                assignSkullEntity(inRangeSkulls.get(maxVisibleSkulls - 1));
            }
        }
    }

    public void clear() {
        for (Skull skull : skulls.values()) {
            if (skull.entity != null) {
                skull.entity.despawnEntity();
            }
        }
        skulls.clear();
        inRangeSkulls.clear();
        totalSkullEntities = 0;
        lastPlayerPosition = null;
    }

    private @Nullable BlockDefinition translateCustomSkull(String skinHash, BlockState blockState) {
        CustomSkull customSkull = BlockRegistries.CUSTOM_SKULLS.get(skinHash);
        if (customSkull != null) {
            CustomBlockState customBlockState;
            if (blockState.block() instanceof WallSkullBlock) {
                customBlockState = customSkull.getWallBlockState(WallSkullBlock.getDegrees(blockState));
            } else {
                customBlockState = customSkull.getFloorBlockState(blockState.getValue(Properties.ROTATION_16));
            }

            return session.getBlockMappings().getCustomBlockStateDefinitions().get(customBlockState);
        }
        return null;
    }

    @RequiredArgsConstructor
    @Data
    public static class Skull {
        private UUID uuid;
        private String texturesProperty;
        private String skinHash;

        private BlockState blockState;
        private BlockDefinition blockDefinition;
        private SkullPlayerEntity entity;

        private final Vector3i position;
        private int distanceSquared;
    }
}
