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

package org.geysermc.geyser.entity.type;

import lombok.Getter;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BlockEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;

import java.util.UUID;

/**
 * Item frames are an entity in Java but a block entity in Bedrock.
 */
public class ItemFrameEntity extends Entity {
    /**
     * Used for getting the Bedrock block position.
     * Blocks deal with integers whereas entities deal with floats.
     */
    private final Vector3i bedrockPosition;
    /**
     * Specific block 'state' we are emulating in Bedrock.
     */
    private final BlockDefinition blockDefinition;
    /**
     * Rotation of item in frame.
     */
    private float rotation = 0.0f;
    /**
     * Cached item frame's Bedrock compound tag.
     */
    private NbtMap cachedTag;
    /**
     * The item currently in the item frame. Used for block picking.
     */
    @Getter
    private ItemStack heldItem = null;
    /**
     * Determines if this entity needs updated on the client end/
     */
    private boolean changed = true;

    public ItemFrameEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, EntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw, Direction direction) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);

        NbtMapBuilder blockBuilder = NbtMap.builder()
                .putString("name", this.definition.entityType() == EntityType.GLOW_ITEM_FRAME ? "minecraft:glow_frame" : "minecraft:frame");
        NbtMapBuilder statesBuilder = NbtMap.builder()
                .putInt("facing_direction", direction.ordinal())
                .putByte("item_frame_map_bit", (byte) 0)
                .putByte("item_frame_photo_bit", (byte) 0);
        blockBuilder.put("states", statesBuilder.build());

        blockDefinition = session.getBlockMappings().getItemFrame(blockBuilder.build());
        bedrockPosition = Vector3i.from(position.getFloorX(), position.getFloorY(), position.getFloorZ());

        session.getItemFrameCache().put(bedrockPosition, this);
    }

    @Override
    protected void initializeMetadata() {
        // lol nah don't do anything
        // This isn't a real entity for Bedrock so it isn't going to do anything
    }

    @Override
    public void spawnEntity() {
        updateBlock(true);
        GeyserLogger.get().debug("Spawned item frame at location " + bedrockPosition + " with java id " + entityId);
        valid = true;
    }

    public void setItemInFrame(EntityMetadata<ItemStack, ?> entityMetadata) {
        if (entityMetadata.getValue() != null) {
            this.heldItem = entityMetadata.getValue();
            ItemData itemData = ItemTranslator.translateToBedrock(session, heldItem);
            String customIdentifier = session.getItemMappings().getCustomIdMappings().get(itemData.getDefinition().getRuntimeId());

            NbtMapBuilder builder = NbtMap.builder();
            builder.putByte("Count", (byte) itemData.getCount());
            NbtMap itemDataTag = itemData.getTag();
            if (itemDataTag != null) {
                // Remove custom name that Geyser sets for items due to translating non-"custom_name" components
                String customName = ItemTranslator.getCustomName(session, heldItem.getDataComponentsPatch(),
                    session.getItemMappings().getMapping(heldItem), 'f', true, false);
                if (customName == null) {
                    // No custom name found, must modify tag if custom name exists
                    NbtMapBuilder copy = itemDataTag.toBuilder();
                    copy.remove("display"); // Also removes lore, but, should not matter
                    itemDataTag = copy.build();
                }

                builder.put("tag", itemDataTag);
            }
            builder.putShort("Damage", (short) itemData.getDamage());
            builder.putString("Name", customIdentifier != null ? customIdentifier : session.getItemMappings().getMapping(entityMetadata.getValue()).getBedrockIdentifier());
            NbtMapBuilder tag = getDefaultTag().toBuilder();
            tag.put("Item", builder.build());
            tag.putFloat("ItemDropChance", 1.0f);
            tag.putFloat("ItemRotation", rotation);
            cachedTag = tag.build();
            changed = true;
        } else if (cachedTag != null) {
            cachedTag = getDefaultTag();
            changed = true;
        }
    }

    public void setItemRotation(IntEntityMetadata entityMetadata) {
        rotation = entityMetadata.getPrimitiveValue() * 45;
        if (cachedTag == null) {
            return;
        }
        NbtMapBuilder builder = cachedTag.toBuilder();
        builder.putFloat("ItemRotation", rotation);
        cachedTag = builder.build();
        changed = true;
    }

    @Override
    public void despawnEntity() {
        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(bedrockPosition);
        updateBlockPacket.setDefinition(session.getBlockMappings().getBedrockAir()); //TODO maybe set this to the world block or another item frame?
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.PRIORITY);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.sendUpstreamPacket(updateBlockPacket);

        session.getItemFrameCache().remove(bedrockPosition, this);

        valid = false;
    }

    private NbtMap getDefaultTag() {
        NbtMapBuilder builder = NbtMap.builder();
        builder.putInt("x", bedrockPosition.getX());
        builder.putInt("y", bedrockPosition.getY());
        builder.putInt("z", bedrockPosition.getZ());
        builder.putByte("isMovable", (byte) 1);
        builder.putString("id", this.definition.entityType() == EntityType.GLOW_ITEM_FRAME ? "GlowItemFrame" : "ItemFrame");
        return builder.build();
    }

    @Override
    public void updateBedrockMetadata() {
        updateBlock(false);
    }

    /**
     * Updates the item frame as a block
     */
    public void updateBlock(boolean force) {
        if (!changed && !force) {
            // Don't send a block update packet - nothing changed
            return;
        }
        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(bedrockPosition);
        updateBlockPacket.setDefinition(blockDefinition);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.PRIORITY);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NETWORK);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.sendUpstreamPacket(updateBlockPacket);

        BlockEntityDataPacket blockEntityDataPacket = new BlockEntityDataPacket();
        blockEntityDataPacket.setBlockPosition(bedrockPosition);
        if (cachedTag != null) {
            blockEntityDataPacket.setData(cachedTag);
        } else {
            blockEntityDataPacket.setData(getDefaultTag());
        }

        session.sendUpstreamPacket(blockEntityDataPacket);

        changed = false;
    }

    @Override
    public InteractionResult interact(Hand hand) {
        return InventoryUtils.isEmpty(heldItem) && session.getPlayerInventory().getItemInHand(hand).isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
    }

    /**
     * Finds the Java entity ID of an item frame from its Bedrock position.
     * @param position position of item frame in Bedrock.
     * @param session GeyserConnection.
     * @return Java entity ID or -1 if not found.
     */
    public static ItemFrameEntity getItemFrameEntity(GeyserSession session, Vector3i position) {
        return session.getItemFrameCache().get(position);
    }
}
