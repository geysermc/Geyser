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
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEvent2Packet;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class EndermanEntity extends MonsterEntity {

    public EndermanEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setCarriedBlock(IntEntityMetadata entityMetadata) {
        BlockDefinition bedrockBlockId = session.getBlockMappings().getBedrockBlock(entityMetadata.getPrimitiveValue());

        dirtyMetadata.put(EntityDataTypes.CARRY_BLOCK_STATE, bedrockBlockId);
    }

    /**
     * Controls the screaming sound
     */
    public void setScreaming(BooleanEntityMetadata entityMetadata) {
        //TODO see if Bedrock controls this differently
        // Java Edition this controls which ambient sound is used
        if (entityMetadata.getPrimitiveValue()) {
            LevelSoundEvent2Packet packet = new LevelSoundEvent2Packet();
            packet.setSound(SoundEvent.STARE);
            packet.setPosition(this.position);
            packet.setExtraData(-1);
            packet.setIdentifier("minecraft:enderman");
            session.sendUpstreamPacket(packet);
        }
    }

    public void setAngry(BooleanEntityMetadata entityMetadata) {
        // "Is staring/provoked" - controls visuals
        setFlag(EntityFlag.ANGRY, entityMetadata.getPrimitiveValue());
    }
}
