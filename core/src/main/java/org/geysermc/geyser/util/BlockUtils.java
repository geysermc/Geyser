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

import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.EntityEffectCache;
import org.geysermc.geyser.translator.collision.BlockCollision;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ToolData;

public final class BlockUtils {

    /**
     * Returns the total mining progress added by mining the block in a single tick
     * @return the mining progress added by this tick.
     */
    public static float getBlockMiningProgressPerTick(GeyserSession session, Block block, GeyserItemStack itemInHand) {
        float destroySpeed = block.destroyTime();
        if (destroySpeed == -1) {
            return 0;
        }

        int speedMultiplier = hasCorrectTool(session, block, itemInHand) ? 30 : 100;
        return getPlayerDestroySpeed(session, block, itemInHand) / destroySpeed / speedMultiplier;
    }

    private static boolean hasCorrectTool(GeyserSession session, Block block, GeyserItemStack stack) {
        return !block.requiresCorrectToolForDrops() || isCorrectItemForDrops(session, block, stack);
    }

    private static boolean isCorrectItemForDrops(GeyserSession session, Block block, GeyserItemStack stack) {
        ToolData tool = stack.getComponent(DataComponentTypes.TOOL);
        if (tool == null) {
            return false;
        }

        for (ToolData.Rule rule : tool.getRules()) {
            if (rule.getCorrectForDrops() != null) {
                if (session.getTagCache().isBlock(rule.getBlocks(), block)) {
                    return rule.getCorrectForDrops();
                }
            }
        }

        return false;
    }

    private static float getItemDestroySpeed(GeyserSession session, Block block, GeyserItemStack stack) {
        ToolData tool = stack.getComponent(DataComponentTypes.TOOL);
        if (tool == null) {
            return 1f;
        }

        for (ToolData.Rule rule : tool.getRules()) {
            if (rule.getSpeed() != null) {
                if (session.getTagCache().isBlock(rule.getBlocks(), block)) {
                    return rule.getSpeed();
                }
            }
        }

        return tool.getDefaultMiningSpeed();
    }

    private static float getPlayerDestroySpeed(GeyserSession session, Block block, GeyserItemStack itemInHand) {
        float destroySpeed = getItemDestroySpeed(session, block, itemInHand);
        EntityEffectCache effectCache = session.getEffectCache();

        if (destroySpeed > 1.0F) {
            destroySpeed += session.getPlayerEntity().attributeOrDefault(GeyserAttributeType.MINING_EFFICIENCY);
        }

        int miningSpeedMultiplier = getMiningSpeedAmplification(effectCache);
        if (miningSpeedMultiplier > 0) {
            destroySpeed *= miningSpeedMultiplier * 0.2F;
        }

        if (effectCache.getMiningFatigue() != 0) {
            float slowdown = switch (effectCache.getMiningFatigue()) {
                case 1 -> 0.3F;
                case 2 -> 0.09F;
                case 3 -> 0.0027F;
                default -> 8.1E-4F;
            };
            destroySpeed *= slowdown;
        }

        destroySpeed *= session.getPlayerEntity().attributeOrDefault(GeyserAttributeType.BLOCK_BREAK_SPEED);
        if (session.getCollisionManager().isWaterInEyes()) {
            destroySpeed *= session.getPlayerEntity().attributeOrDefault(GeyserAttributeType.SUBMERGED_MINING_SPEED);
        }

        if (!session.getPlayerEntity().isOnGround()) {
            destroySpeed /= 5F;
        }

        return destroySpeed;
    }

    private static int getMiningSpeedAmplification(EntityEffectCache cache) {
        return Math.max(cache.getHaste(), cache.getConduitPower());
    }

    public static double getSessionBreakTimeTicks(GeyserSession session, Block block) {
        return Math.ceil(1 / getBlockMiningProgressPerTick(session, block, session.getPlayerInventory().getItemInHand()));
    }

    /**
     * Given a position, return the position if a block were located on the specified block face.
     * @param blockPos the block position
     * @param face the face of the block - see {@link org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction}
     * @return the block position with the block face accounted for
     */
    public static Vector3i getBlockPosition(Vector3i blockPos, int face) {
        return switch (face) {
            case 0 -> blockPos.sub(0, 1, 0);
            case 1 -> blockPos.add(0, 1, 0);
            case 2 -> blockPos.sub(0, 0, 1);
            case 3 -> blockPos.add(0, 0, 1);
            case 4 -> blockPos.sub(1, 0, 0);
            case 5 -> blockPos.add(1, 0, 0);
            default -> blockPos;
        };
    }

    /**
     * Taking in a complete Java block state identifier, output just the block ID of this block state without the states.
     * Examples:
     * minecraft:oak_log[axis=x] = minecraft:oak_log
     * minecraft:stone_brick_wall[east=low,north=tall,south=none,up=true,waterlogged=false,west=tall] = minecraft:stone_brick_wall
     * minecraft:stone = minecraft:stone
     *
     * @param fullJavaIdentifier a full Java block identifier, with possible block states.
     * @return a clean identifier in the format of minecraft:block
     */
    public static String getCleanIdentifier(String fullJavaIdentifier) {
        int stateIndex = fullJavaIdentifier.indexOf('[');
        if (stateIndex == -1) {
            // Identical to its clean variation
            return fullJavaIdentifier;
        }
        return fullJavaIdentifier.substring(0, stateIndex);
    }

    public static BlockCollision getCollision(int blockId) {
        if (blockId == -1) return null;
        return BlockRegistries.COLLISIONS.get(blockId);
    }

    public static BlockCollision getCollisionAt(GeyserSession session, Vector3i blockPos) {
        return getCollision(session.getGeyser().getWorldManager().getBlockAt(session, blockPos));
    }

    private BlockUtils() {
    }
}
