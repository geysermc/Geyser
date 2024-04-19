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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import lombok.Getter;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.living.animal.AnimalEntity;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Optional;
import java.util.UUID;

public class TameableEntity extends AnimalEntity {
    /**
     * Used in the interactive tag manager to track if the session player owns this entity
     */
    @Getter
    protected long ownerBedrockId;

    public TameableEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setTameableFlags(ByteEntityMetadata entityMetadata) {
        byte xd = entityMetadata.getPrimitiveValue();
        setFlag(EntityFlag.SITTING, (xd & 0x01) == 0x01);
        setFlag(EntityFlag.ANGRY, (xd & 0x02) == 0x02);
        setFlag(EntityFlag.TAMED, (xd & 0x04) == 0x04);
    }

    public void setOwner(EntityMetadata<Optional<UUID>, ?> entityMetadata) {
        // Note: Must be set for wolf collar color to work
        if (entityMetadata.getValue().isPresent()) {
            // Owner UUID of entity
            UUID uuid = entityMetadata.getValue().get();
            Entity entity;
            if (uuid.equals(session.getPlayerEntity().getUuid())) {
                entity = session.getPlayerEntity();
            } else {
                entity = session.getEntityCache().getPlayerEntity(uuid);
            }
            // Used as both a check since the player isn't in the entity cache and a normal fallback
            if (entity == null) {
                // Set to tame, but indicate that we are not the player that owns this
                ownerBedrockId = Long.MAX_VALUE;
            } else {
                // Translate to entity ID
                ownerBedrockId = entity.getGeyserId();
            }
        } else {
            // Reset
            ownerBedrockId = 0L;
        }
        dirtyMetadata.put(EntityDataTypes.OWNER_EID, ownerBedrockId);
    }

    @Override
    protected boolean canBeLeashed() {
        return isNotLeashed();
    }
}
