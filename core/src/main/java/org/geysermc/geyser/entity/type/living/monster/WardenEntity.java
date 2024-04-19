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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Pose;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.MathUtils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class WardenEntity extends MonsterEntity implements Tickable {
    private int heartBeatDelay = 40;
    private int tickCount;

    private int sonicBoomTickDuration;

    public WardenEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        dirtyMetadata.put(EntityDataTypes.HEARTBEAT_INTERVAL_TICKS, heartBeatDelay);
    }

    @Override
    public void setPose(Pose pose) {
        setFlag(EntityFlag.DIGGING, pose == Pose.DIGGING);
        setFlag(EntityFlag.EMERGING, pose == Pose.EMERGING);
        setFlag(EntityFlag.ROARING, pose == Pose.ROARING);
        setFlag(EntityFlag.SNIFFING, pose == Pose.SNIFFING);
        super.setPose(pose);
    }

    public void setAngerLevel(IntEntityMetadata entityMetadata) {
        float anger = (float) entityMetadata.getPrimitiveValue() / 80f;
        heartBeatDelay = 40 - GenericMath.floor(MathUtils.clamp(anger, 0.0F, 1.0F) * 30F);
        dirtyMetadata.put(EntityDataTypes.HEARTBEAT_INTERVAL_TICKS, heartBeatDelay);
    }

    @Override
    public void tick() {
        if (++tickCount % heartBeatDelay == 0 && !silent) {
            // We have to do these calculations because they're clientside on Java Edition but we mute entities
            // to prevent hearing their step sounds
            ThreadLocalRandom random = ThreadLocalRandom.current();

            PlaySoundPacket packet = new PlaySoundPacket();
            packet.setSound("mob.warden.heartbeat");
            packet.setPosition(position);
            packet.setPitch((random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f);
            packet.setVolume(1.0f);
            session.sendUpstreamPacket(packet);
        }

        if (sonicBoomTickDuration > 0) {
            sonicBoomTickDuration--;
            if (sonicBoomTickDuration == 0) {
                setFlag(EntityFlag.SONIC_BOOM, false);
                updateBedrockMetadata();
            }
        }
    }

    public void onSonicBoom() {
        setFlag(EntityFlag.SONIC_BOOM, true);
        updateBedrockMetadata();

        sonicBoomTickDuration = 3 * 20;
    }
}
