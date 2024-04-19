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

package org.geysermc.geyser.entity.type.living.monster;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;

public class PiglinEntity extends BasePiglinEntity {

    public PiglinEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setBaby(BooleanEntityMetadata entityMetadata) {
        boolean isBaby = entityMetadata.getPrimitiveValue();
        dirtyMetadata.put(EntityDataTypes.SCALE, isBaby? .55f : 1f);
        setFlag(EntityFlag.BABY, isBaby);

        updateMountOffset();
    }

    public void setChargingCrossbow(BooleanEntityMetadata entityMetadata) {
        setFlag(EntityFlag.CHARGING, entityMetadata.getPrimitiveValue());
    }

    public void setDancing(BooleanEntityMetadata entityMetadata) {
        setFlag(EntityFlag.DANCING, entityMetadata.getPrimitiveValue());
    }

    @Override
    public void updateOffHand(GeyserSession session) {
        // Check if the Piglin is holding Gold and set the ADMIRING flag accordingly so its pose updates
        setFlag(EntityFlag.ADMIRING, session.getTagCache().shouldPiglinAdmire(session.getItemMappings().getMapping(this.offHand).getJavaItem()));
        super.updateBedrockMetadata();

        super.updateOffHand(session);
    }

    @NonNull
    @Override
    protected InteractiveTag testMobInteraction(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        InteractiveTag tag = super.testMobInteraction(hand, itemInHand);
        if (tag != InteractiveTag.NONE) {
            return tag;
        } else {
            return canGiveGoldTo(itemInHand) ? InteractiveTag.BARTER : InteractiveTag.NONE;
        }
    }

    @NonNull
    @Override
    protected InteractionResult mobInteract(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        InteractionResult superResult = super.mobInteract(hand, itemInHand);
        if (superResult.consumesAction()) {
            return superResult;
        } else {
            return canGiveGoldTo(itemInHand) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
    }

    private boolean canGiveGoldTo(@NonNull GeyserItemStack itemInHand) {
        return !getFlag(EntityFlag.BABY) && itemInHand.asItem() == Items.GOLD_INGOT && !getFlag(EntityFlag.ADMIRING);
    }
}
