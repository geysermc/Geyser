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

package org.geysermc.geyser.entity.type.living.monster.raid;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class SpellcasterIllagerEntity extends AbstractIllagerEntity {
    private static final int SUMMON_VEX_PARTICLE_COLOR = (179 << 16) | (179 << 8) | 204;
    private static final int ATTACK_PARTICLE_COLOR = (102 << 16) | (77 << 8) | 89;
    private static final int WOLOLO_PARTICLE_COLOR = (179 << 16) | (128 << 8) | 51;

    public SpellcasterIllagerEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
        // OptionalPack usage
        setFlag(EntityFlag.BRIBED, this.definition == EntityDefinitions.ILLUSIONER);
    }

    public void setSpellType(ByteEntityMetadata entityMetadata) {
        int spellType = entityMetadata.getPrimitiveValue();
        // Summon vex, attack, or wololo
        setFlag(EntityFlag.CASTING, spellType == 1 || spellType == 2 || spellType == 3);
        int rgbData = switch (spellType) {
            // Set the spell color based on Java values
            case 1 -> SUMMON_VEX_PARTICLE_COLOR;
            case 2 -> ATTACK_PARTICLE_COLOR;
            case 3 -> WOLOLO_PARTICLE_COLOR;
            default -> 0;
        };
        dirtyMetadata.put(EntityDataTypes.EVOKER_SPELL_CASTING_COLOR, rgbData);
    }
}
