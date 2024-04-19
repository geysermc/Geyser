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

package org.geysermc.geyser.entity.type.living.animal.tameable;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.DyeItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.Set;
import java.util.UUID;

public class WolfEntity extends TameableEntity {
    /**
     * A list of all foods a wolf can eat on Java Edition.
     * Used to display interactive tag or particles if needed.
     * TODO generate
     */
    private static final Set<Item> WOLF_FOODS = Set.of(Items.PUFFERFISH, Items.TROPICAL_FISH, Items.CHICKEN, Items.COOKED_CHICKEN,
            Items.PORKCHOP, Items.BEEF, Items.RABBIT, Items.COOKED_PORKCHOP, Items.COOKED_BEEF, Items.ROTTEN_FLESH, Items.MUTTON, Items.COOKED_MUTTON,
            Items.COOKED_RABBIT);

    private byte collarColor;

    public WolfEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    public void setTameableFlags(ByteEntityMetadata entityMetadata) {
        super.setTameableFlags(entityMetadata);
        // Reset wolf color
        byte xd = entityMetadata.getPrimitiveValue();
        boolean angry = (xd & 0x02) == 0x02;
        if (angry) {
            dirtyMetadata.put(EntityDataTypes.COLOR, (byte) 0);
        }
    }

    public void setCollarColor(IntEntityMetadata entityMetadata) {
        collarColor = (byte) entityMetadata.getPrimitiveValue();
        if (getFlag(EntityFlag.ANGRY)) {
            return;
        }

        dirtyMetadata.put(EntityDataTypes.COLOR, collarColor);
        if (ownerBedrockId == 0) {
            // If a color is set and there is no owner entity ID, set one.
            // Otherwise, the entire wolf is set to that color: https://user-images.githubusercontent.com/9083212/99209989-92691200-2792-11eb-911d-9a315c955be9.png
            dirtyMetadata.put(EntityDataTypes.OWNER_EID, session.getPlayerEntity().getGeyserId());
        }
    }

    // 1.16+
    public void setWolfAngerTime(IntEntityMetadata entityMetadata) {
        int time = entityMetadata.getPrimitiveValue();
        setFlag(EntityFlag.ANGRY, time != 0);
        dirtyMetadata.put(EntityDataTypes.COLOR, time != 0 ? (byte) 0 : collarColor);
    }

    @Override
    public boolean canEat(Item item) {
        // Cannot be a baby to eat these foods
        return WOLF_FOODS.contains(item) && !isBaby();
    }

    @Override
    protected boolean canBeLeashed() {
        return !getFlag(EntityFlag.ANGRY) && super.canBeLeashed();
    }

    @NonNull
    @Override
    protected InteractiveTag testMobInteraction(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        if (getFlag(EntityFlag.ANGRY)) {
            return InteractiveTag.NONE;
        }
        if (itemInHand.asItem() == Items.BONE && !getFlag(EntityFlag.TAMED)) {
            // Bone and untamed - can tame
            return InteractiveTag.TAME;
        } else {
            if (itemInHand.asItem() instanceof DyeItem item) {
                // If this fails, as of Java Edition 1.18.1, you cannot toggle sit/stand
                if (item.dyeColor() != this.collarColor) {
                    return InteractiveTag.DYE;
                }
            } else if (getFlag(EntityFlag.TAMED) && ownerBedrockId == session.getPlayerEntity().getGeyserId()) {
                // Tamed and owned by player - can sit/stand
                return getFlag(EntityFlag.SITTING) ? InteractiveTag.STAND : InteractiveTag.SIT;
            }
        }
        return super.testMobInteraction(hand, itemInHand);
    }

    @NonNull
    @Override
    protected InteractionResult mobInteract(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        if (ownerBedrockId == session.getPlayerEntity().getGeyserId() || getFlag(EntityFlag.TAMED)
                || itemInHand.asItem() == Items.BONE && !getFlag(EntityFlag.ANGRY)) {
            // Sitting toggle or feeding; not angry
            return InteractionResult.CONSUME;
        } else {
            return InteractionResult.PASS;
        }
    }
}
