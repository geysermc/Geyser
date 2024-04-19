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

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityDeltaPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SquidEntity extends WaterEntity implements Tickable {
    private float targetPitch;
    private float targetYaw;

    private CompletableFuture<Boolean> inWater = CompletableFuture.completedFuture(Boolean.FALSE);

    public SquidEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    public void tick() {
        boolean pitchChanged;
        boolean yawChanged;
        float oldPitch = pitch;
        if (inWater.join()) {
            float oldYaw = yaw;
            pitch += (targetPitch - pitch) * 0.1f;
            yaw += (targetYaw - yaw) * 0.1f;
            yawChanged = oldYaw != yaw;
        } else {
            pitch += (-90 - pitch) * 0.02f;
            yawChanged = false;
        }
        pitchChanged = oldPitch != pitch;

        if (pitchChanged || yawChanged) {
            MoveEntityDeltaPacket packet = new MoveEntityDeltaPacket();
            packet.setRuntimeEntityId(geyserId);

            if (pitchChanged) {
                packet.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_PITCH);
                packet.setPitch(pitch);
            }
            if (yawChanged) {
                packet.getFlags().add(MoveEntityDeltaPacket.Flag.HAS_YAW);
                packet.setYaw(yaw);
            }

            session.sendUpstreamPacket(packet);
        }
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        super.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
        checkInWater();
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        super.moveAbsolute(position, yaw, pitch, headYaw, isOnGround, teleported);
        checkInWater();
    }

    @Override
    public void setYaw(float yaw) {
        // Let the Java server control yaw when the squid is out of water
        if (!inWater.join()) {
            this.yaw = yaw;
        }
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
        targetPitch = (float) Math.toDegrees(-Math.atan2(horizontalSpeed, motion.getY()));
        targetYaw = (float) Math.toDegrees(-Math.atan2(motion.getX(), motion.getZ()));
    }

    @Override
    public Vector3f getBedrockRotation() {
        return Vector3f.from(getPitch(), getYaw(), getYaw());
    }

    @Override
    protected boolean canBeLeashed() {
        return isNotLeashed();
    }

    private void checkInWater() {
        if (getFlag(EntityFlag.RIDING)) {
            inWater = CompletableFuture.completedFuture(false);
        } else {
            inWater = session.getGeyser().getWorldManager().getBlockAtAsync(session, position.toInt())
                    .thenApply(block -> BlockStateValues.getWaterLevel(block) != -1);
        }
    }
}
