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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Pose;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ObjectEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.session.GeyserSession;

import java.util.OptionalInt;
import java.util.UUID;

public class FrogEntity extends AnimalEntity {
    public FrogEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    public void setPose(Pose pose) {
        setFlag(EntityFlag.JUMP_GOAL_JUMP, pose == Pose.LONG_JUMPING);
        setFlag(EntityFlag.CROAKING, pose == Pose.CROAKING);
        setFlag(EntityFlag.EAT_MOB, pose == Pose.USING_TONGUE);

        super.setPose(pose);
    }

    public void setFrogVariant(IntEntityMetadata entityMetadata) {
        int variant = entityMetadata.getPrimitiveValue();
        dirtyMetadata.put(EntityDataTypes.VARIANT, switch (variant) {
            case 1 -> 2; // White
            case 2 -> 1; // Green
            default -> variant;
        });
    }

    public void setTongueTarget(ObjectEntityMetadata<OptionalInt> entityMetadata) {
        OptionalInt entityId = entityMetadata.getValue();
        if (entityId.isPresent()) {
            Entity entity = session.getEntityCache().getEntityByJavaId(entityId.getAsInt());
            if (entity != null) {
                dirtyMetadata.put(EntityDataTypes.TARGET_EID, entity.getGeyserId());
            }
        } else {
            dirtyMetadata.put(EntityDataTypes.TARGET_EID, 0L);
        }
    }

    @Override
    public boolean canEat(Item item) {
        return item == Items.SLIME_BALL;
    }
}
