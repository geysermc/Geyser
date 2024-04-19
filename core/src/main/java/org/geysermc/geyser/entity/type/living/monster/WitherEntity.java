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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class WitherEntity extends MonsterEntity {

    public WitherEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        dirtyMetadata.put(EntityDataTypes.WITHER_AERIAL_ATTACK, (short) 1);
    }

    public void setTarget1(IntEntityMetadata entityMetadata) {
        setTargetId(EntityDataTypes.WITHER_TARGET_A, entityMetadata);
    }

    public void setTarget2(IntEntityMetadata entityMetadata) {
        setTargetId(EntityDataTypes.WITHER_TARGET_B, entityMetadata);
    }

    public void setTarget3(IntEntityMetadata entityMetadata) {
        setTargetId(EntityDataTypes.WITHER_TARGET_C, entityMetadata);
    }

    private void setTargetId(EntityDataType<Long> entityData, IntEntityMetadata entityMetadata) {
        int entityId = entityMetadata.getPrimitiveValue();
        Entity entity = session.getEntityCache().getEntityByJavaId(entityId);
        if (entity != null) {
            dirtyMetadata.put(entityData, entity.getGeyserId());
        } else {
            dirtyMetadata.put(entityData, (long) 0);
        }
    }

    public void setInvulnerableTicks(IntEntityMetadata entityMetadata) {
        int value = entityMetadata.getPrimitiveValue();
        dirtyMetadata.put(EntityDataTypes.WITHER_INVULNERABLE_TICKS, value);

        // Show the shield for the first few seconds of spawning (like Java)
        if (value >= 165) {
            dirtyMetadata.put(EntityDataTypes.WITHER_AERIAL_ATTACK, (short) 0);
        } else {
            dirtyMetadata.put(EntityDataTypes.WITHER_AERIAL_ATTACK, (short) 1);
        }
    }
}
