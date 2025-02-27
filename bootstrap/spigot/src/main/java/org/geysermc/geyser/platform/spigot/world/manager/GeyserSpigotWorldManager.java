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

package org.geysermc.geyser.platform.spigot.world.manager;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.DecoratedPot;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.erosion.bukkit.BukkitUtils;
import org.geysermc.erosion.bukkit.SchedulerUtils;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.level.GameRule;
import org.geysermc.geyser.level.WorldManager;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The base world manager to use when there is no supported NMS revision
 */
public class GeyserSpigotWorldManager extends WorldManager {
    private final Plugin plugin;

    public GeyserSpigotWorldManager(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int getBlockAt(GeyserSession session, int x, int y, int z) {
        Player bukkitPlayer;
        if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUsername())) == null) {
            return org.geysermc.geyser.level.block.type.Block.JAVA_AIR_ID;
        }
        World world = bukkitPlayer.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            // If the chunk isn't loaded, how could we even be here?
            return org.geysermc.geyser.level.block.type.Block.JAVA_AIR_ID;
        }

        return getBlockNetworkId(world.getBlockAt(x, y, z));
    }

    public int getBlockNetworkId(Block block) {
        if (SchedulerUtils.FOLIA && !Bukkit.isOwnedByCurrentRegion(block)) {
            // Terrible behavior, but this is basically what's always been happening behind the scenes anyway.
            CompletableFuture<String> blockData = new CompletableFuture<>();
            Bukkit.getRegionScheduler().execute(this.plugin, block.getLocation(), () -> blockData.complete(block.getBlockData().getAsString()));
            return BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(blockData.join(), org.geysermc.geyser.level.block.type.Block.JAVA_AIR_ID);
        }
        return BlockRegistries.JAVA_IDENTIFIER_TO_ID.getOrDefault(block.getBlockData().getAsString(), org.geysermc.geyser.level.block.type.Block.JAVA_AIR_ID); // TODO could just make this a BlockState lookup?
    }

    @Override
    public boolean hasOwnChunkCache() {
        return true;
    }

    public boolean getGameRuleBool(GeyserSession session, GameRule gameRule) {
        org.bukkit.GameRule<?> bukkitGameRule = org.bukkit.GameRule.getByName(gameRule.getJavaID());
        if (bukkitGameRule == null) {
            GeyserLogger.get().debug("Unknown game rule " + gameRule.getJavaID());
            return gameRule.getDefaultBooleanValue();
        }

        Player bukkitPlayer = Objects.requireNonNull(Bukkit.getPlayer(session.getPlayerEntity().getUuid()));
        Object value = bukkitPlayer.getWorld().getGameRuleValue(bukkitGameRule);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        GeyserLogger.get().debug("Expected a bool for " + gameRule + " but got " + value);
        return gameRule.getDefaultBooleanValue();
    }

    @Override
    public int getGameRuleInt(GeyserSession session, GameRule gameRule) {
        org.bukkit.GameRule<?> bukkitGameRule = org.bukkit.GameRule.getByName(gameRule.getJavaID());
        if (bukkitGameRule == null) {
            GeyserLogger.get().debug("Unknown game rule " + gameRule.getJavaID());
            return gameRule.getDefaultIntValue();
        }
        Player bukkitPlayer = Objects.requireNonNull(Bukkit.getPlayer(session.getPlayerEntity().getUuid()));
        Object value = bukkitPlayer.getWorld().getGameRuleValue(bukkitGameRule);
        if (value instanceof Integer intValue) {
            return intValue;
        }
        GeyserLogger.get().debug("Expected an int for " + gameRule + " but got " + value);
        return gameRule.getDefaultIntValue();
    }

    @Override
    public GameMode getDefaultGameMode(GeyserSession session) {
        return GameMode.byId(Bukkit.getDefaultGameMode().ordinal());
    }

    public void getDecoratedPotData(GeyserSession session, Vector3i pos, Consumer<List<String>> apply) {
        Player bukkitPlayer;
        if ((bukkitPlayer = Bukkit.getPlayer(session.getPlayerEntity().getUuid())) == null) {
            return;
        }
        Block block = bukkitPlayer.getWorld().getBlockAt(pos.getX(), pos.getY(), pos.getZ());
        SchedulerUtils.runTask(this.plugin, () -> {
            var state = BukkitUtils.getBlockState(block);
            if (!(state instanceof DecoratedPot pot)) {
                return;
            }
            apply.accept(pot.getShards().stream().map(material -> material.getKey().toString()).toList());
        }, block);
    }

    /**
     * This should be set to true if we are post-1.13 but before the latest version, and we should convert the old block state id
     * to the current one.
     *
     * @return whether there is a difference between client block state and server block state that requires extra processing
     */
    public boolean isLegacy() {
        return false;
    }
}
