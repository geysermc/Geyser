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

package org.geysermc.geyser.translator.level.block.entity;

import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;

@BlockEntity(type = BlockEntityType.MOB_SPAWNER)
public class SpawnerBlockEntityTranslator extends BlockEntityTranslator {

    @Override
    public NbtMap getBlockEntityTag(GeyserSession session, BlockEntityType type, int x, int y, int z, CompoundTag tag, int blockState) {
        // Sending an empty EntityIdentifier to empty the spawner is ignored by the client, so we send a whole new spawner!
        // Fixes https://github.com/GeyserMC/Geyser/issues/4214
        CompoundTag spawnData = tag.get("SpawnData");
        if (spawnData != null) {
            CompoundTag entityTag = spawnData.get("entity");
            if (entityTag.isEmpty()) {
                Vector3i position = Vector3i.from(x, y, z);
                // Set to air and back to reset the spawner - "just" updating the spawner doesn't work
                UpdateBlockPacket emptyBlockPacket = new UpdateBlockPacket();
                emptyBlockPacket.setDataLayer(0);
                emptyBlockPacket.setBlockPosition(position);
                emptyBlockPacket.setDefinition(session.getBlockMappings().getBedrockAir());
                session.sendUpstreamPacket(emptyBlockPacket);

                UpdateBlockPacket spawnerBlockPacket = new UpdateBlockPacket();
                spawnerBlockPacket.setDataLayer(0);
                spawnerBlockPacket.setBlockPosition(position);
                spawnerBlockPacket.setDefinition(session.getBlockMappings().getMobSpawnerBlock());
                session.sendUpstreamPacket(spawnerBlockPacket);
            }
        }

        return super.getBlockEntityTag(session, type, x, y, z, tag, blockState);
    }

    @Override
    public void translateTag(NbtMapBuilder builder, CompoundTag tag, int blockState) {
        Tag current;

        if ((current = tag.get("MaxNearbyEntities")) != null) {
            builder.put("MaxNearbyEntities", current.getValue());
        }

        if ((current = tag.get("RequiredPlayerRange")) != null) {
            builder.put("RequiredPlayerRange", current.getValue());
        }

        if ((current = tag.get("SpawnCount")) != null) {
            builder.put("SpawnCount", current.getValue());
        }

        if ((current = tag.get("MaxSpawnDelay")) != null) {
            builder.put("MaxSpawnDelay", current.getValue());
        }

        if ((current = tag.get("Delay")) != null) {
            builder.put("Delay", current.getValue());
        }

        if ((current = tag.get("SpawnRange")) != null) {
            builder.put("SpawnRange", current.getValue());
        }

        if ((current = tag.get("MinSpawnDelay")) != null) {
            builder.put("MinSpawnDelay", current.getValue());
        }

        translateSpawnData(builder, tag.get("SpawnData"));

        builder.put("isMovable", (byte) 1);
    }

    static void translateSpawnData(@NonNull NbtMapBuilder builder, @Nullable CompoundTag spawnData) {
        if (spawnData == null) {
            return;
        }

        CompoundTag entityTag = spawnData.get("entity");
        if (entityTag.get("id") instanceof StringTag idTag) {
            // As of 1.19.3, spawners can be empty
            String entityId = idTag.getValue();
            builder.put("EntityIdentifier", entityId);

            GeyserEntityDefinition<?> definition = Registries.ENTITY_IDENTIFIERS.get(entityId);
            if (definition != null) {
                builder.put("DisplayEntityWidth", definition.width());
                builder.put("DisplayEntityHeight", definition.height());
                builder.put("DisplayEntityScale", 1.0f);
            }
        }
    }
}
