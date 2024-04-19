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

package org.geysermc.geyser.entity.type.living.animal;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;

public class PandaEntity extends AnimalEntity {
    private Gene mainGene = Gene.NORMAL;
    private Gene hiddenGene = Gene.NORMAL;

    public PandaEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setEatingCounter(IntEntityMetadata entityMetadata) {
        int count = entityMetadata.getPrimitiveValue();
        setFlag(EntityFlag.EATING, count > 0);
        dirtyMetadata.put(EntityDataTypes.EATING_COUNTER, count);
        if (count != 0) {
            // Particles and sound
            EntityEventPacket packet = new EntityEventPacket();
            packet.setRuntimeEntityId(geyserId);
            packet.setType(EntityEventType.EATING_ITEM);
            packet.setData(session.getItemMappings().getStoredItems().bamboo().getBedrockDefinition().getRuntimeId() << 16);
            session.sendUpstreamPacket(packet);
        }
    }

    public void setMainGene(ByteEntityMetadata entityMetadata) {
        mainGene = Gene.fromId(entityMetadata.getPrimitiveValue());
        updateAppearance();
    }

    public void setHiddenGene(ByteEntityMetadata entityMetadata) {
        hiddenGene = Gene.fromId(entityMetadata.getPrimitiveValue());
        updateAppearance();
    }

    public void setPandaFlags(ByteEntityMetadata entityMetadata) {
        byte xd = entityMetadata.getPrimitiveValue();
        setFlag(EntityFlag.SNEEZING, (xd & 0x02) == 0x02);
        setFlag(EntityFlag.ROLLING, (xd & 0x04) == 0x04);
        setFlag(EntityFlag.SITTING, (xd & 0x08) == 0x08);
        // Required to put these both for sitting to actually show
        dirtyMetadata.put(EntityDataTypes.SITTING_AMOUNT, (xd & 0x08) == 0x08 ? 1f : 0f);
        dirtyMetadata.put(EntityDataTypes.SITTING_AMOUNT_PREVIOUS, (xd & 0x08) == 0x08 ? 1f : 0f);
        setFlag(EntityFlag.LAYING_DOWN, (xd & 0x10) == 0x10);
    }

    @Override
    public boolean canEat(Item item) {
        return item == Items.BAMBOO;
    }

    @NonNull
    @Override
    protected InteractiveTag testMobInteraction(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        if (mainGene == Gene.WORRIED && session.isThunder()) {
            return InteractiveTag.NONE;
        }
        return super.testMobInteraction(hand, itemInHand);
    }

    @NonNull
    @Override
    protected InteractionResult mobInteract(@NonNull Hand hand, @NonNull GeyserItemStack itemInHand) {
        if (mainGene == Gene.WORRIED && session.isThunder()) {
            // Huh!
            return InteractionResult.PASS;
        } else if (getFlag(EntityFlag.LAYING_DOWN)) {
            // Stop the panda from laying down
            // TODO laying up is client-side?
            return InteractionResult.SUCCESS;
        } else if (canEat(itemInHand)) {
            if (getFlag(EntityFlag.BABY)) {
                playEntityEvent(EntityEventType.BABY_ANIMAL_FEED);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    protected boolean canBeLeashed() {
        return false;
    }

    /**
     * Update the panda's appearance, and take into consideration the recessive brown and weak traits that only show up
     * when both main and hidden genes match
     */
    private void updateAppearance() {
        if (mainGene.isRecessive) {
            if (mainGene == hiddenGene) {
                // Main and hidden genes match; this is what the panda looks like.
                dirtyMetadata.put(EntityDataTypes.VARIANT, mainGene.ordinal());
            } else {
                // Genes have no effect on appearance
                dirtyMetadata.put(EntityDataTypes.VARIANT, Gene.NORMAL.ordinal());
            }
        } else {
            // No need to worry about hidden gene
            dirtyMetadata.put(EntityDataTypes.VARIANT, mainGene.ordinal());
        }
    }

    enum Gene {
        NORMAL(false),
        LAZY(false),
        WORRIED(false),
        PLAYFUL(false),
        BROWN(true),
        WEAK(true),
        AGGRESSIVE(false);

        private static final Gene[] VALUES = values();

        private final boolean isRecessive;

        Gene(boolean isRecessive) {
            this.isRecessive = isRecessive;
        }

        @Nullable
        private static Gene fromId(int id) {
            if (id < 0 || id >= VALUES.length) {
                return NORMAL;
            }
            return VALUES[id];
        }
    }
}
