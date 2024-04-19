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

package org.geysermc.geyser.entity.type.living;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.SpawnEggItem;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;

public class MobEntity extends LivingEntity {
    /**
     * If another mob is holding this mob by a leash, this variable tracks their Bedrock entity ID.
     */
    @Getter
    private long leashHolderBedrockId;

    public MobEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        setLeashHolderBedrockId(-1);
    }

    public void setMobFlags(ByteEntityMetadata entityMetadata) {
        byte xd = entityMetadata.getPrimitiveValue();
        setFlag(EntityFlag.NO_AI, (xd & 0x01) == 0x01);
    }

    public void setLeashHolderBedrockId(long bedrockId) {
        this.leashHolderBedrockId = bedrockId;
        dirtyMetadata.put(EntityDataTypes.LEASH_HOLDER, bedrockId);
    }

    @Override
    protected final InteractiveTag testInteraction(Hand hand) {
        if (!isAlive()) {
            // dead lol
            return InteractiveTag.NONE;
        } else if (leashHolderBedrockId == session.getPlayerEntity().getGeyserId()) {
            return InteractiveTag.REMOVE_LEASH;
        } else {
            GeyserItemStack itemStack = session.getPlayerInventory().getItemInHand(hand);
            if (itemStack.asItem() == Items.LEAD && canBeLeashed()) {
                // We shall leash
                return InteractiveTag.LEASH;
            } else if (itemStack.asItem() == Items.NAME_TAG) {
                InteractionResult result = checkInteractWithNameTag(itemStack);
                if (result.consumesAction()) {
                    return InteractiveTag.NAME;
                }
            }

            InteractiveTag tag = testMobInteraction(hand, itemStack);
            return tag != InteractiveTag.NONE ? tag : super.testInteraction(hand);
        }
    }

    @Override
    public final InteractionResult interact(Hand hand) {
        if (!isAlive()) {
            // dead lol
            return InteractionResult.PASS;
        } else if (leashHolderBedrockId == session.getPlayerEntity().getGeyserId()) {
            // TODO looks like the client assumes it will go through and removes the attachment itself?
            return InteractionResult.SUCCESS;
        } else {
            GeyserItemStack itemInHand = session.getPlayerInventory().getItemInHand(hand);
            InteractionResult result = checkPriorityInteractions(itemInHand);
            if (result.consumesAction()) {
                return result;
            } else {
                InteractionResult mobResult = mobInteract(hand, itemInHand);
                return mobResult.consumesAction() ? mobResult : super.interact(hand);
            }
        }
    }

    private InteractionResult checkPriorityInteractions(GeyserItemStack itemInHand) {
        if (itemInHand.asItem() == Items.LEAD && canBeLeashed()) {
            // We shall leash
            return InteractionResult.SUCCESS;
        } else if (itemInHand.asItem() == Items.NAME_TAG) {
            InteractionResult result = checkInteractWithNameTag(itemInHand);
            if (result.consumesAction()) {
                return result;
            }
        } else {
            if (itemInHand.asItem() instanceof SpawnEggItem) {
                // Using the spawn egg on this entity to create a child
                return InteractionResult.CONSUME;
            }
        }

        return InteractionResult.PASS;
    }

    @NonNull
    protected InteractiveTag testMobInteraction(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        return InteractiveTag.NONE;
    }

    @NonNull
    protected InteractionResult mobInteract(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        return InteractionResult.PASS;
    }

    protected boolean canBeLeashed() {
        return isNotLeashed() && !isEnemy();
    }

    protected final boolean isNotLeashed() {
        return leashHolderBedrockId == -1L;
    }

    /**
     * Returns if the entity is hostile. Used to determine if it can be leashed.
     */
    protected boolean isEnemy() {
        return false;
    }
}
