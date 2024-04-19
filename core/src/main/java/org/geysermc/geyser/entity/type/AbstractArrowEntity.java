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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

public class AbstractArrowEntity extends Entity {

    public AbstractArrowEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);

        // Set the correct texture if using the resource pack
        setFlag(EntityFlag.BRIBED, definition.entityType() == EntityType.SPECTRAL_ARROW);

        setMotion(motion);
    }

    public void setArrowFlags(ByteEntityMetadata entityMetadata) {
        byte data = entityMetadata.getPrimitiveValue();

        setFlag(EntityFlag.CRITICAL, (data & 0x01) == 0x01);
    }

    // Ignore the rotation sent by the Java server since the
    // Java client calculates the rotation from the motion
    @Override
    public void setYaw(float yaw) {
    }

    @Override
    public void setPitch(float pitch) {
    }

    @Override
    public void setHeadYaw(float headYaw) {
    }

    @Override
    public void setMotion(Vector3f motion) {
        super.setMotion(motion);

        double horizontalSpeed = Math.sqrt(motion.getX() * motion.getX() + motion.getZ() * motion.getZ());
        setYaw((float) Math.toDegrees(Math.atan2(motion.getX(), motion.getZ())));
        setPitch((float) Math.toDegrees(Math.atan2(motion.getY(), horizontalSpeed)));
        setHeadYaw(getYaw());
    }
}
