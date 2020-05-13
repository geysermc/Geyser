/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 *  @author GeyserMC
 *  @link https://github.com/GeyserMC/Geyser
 *
 */

package org.geysermc.connector.entity;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.EntityFlag;
import com.nukkitx.protocol.bedrock.packet.SetEntityMotionPacket;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.item.ItemTranslator;

public class FireworkEntity extends Entity {

    public FireworkEntity(long entityId, long geyserId, EntityType entityType, Vector3f position, Vector3f motion, Vector3f rotation) {
        super(entityId, geyserId, entityType, position, motion, rotation);
    }

    @Override
    public void updateBedrockMetadata(EntityMetadata entityMetadata, GeyserSession session) {
        if (session.getInventory().getItem(session.getInventory().getHeldItemSlot() + 36).getId() == ItemTranslator.FIREWORK_ROCKET && session.getPlayerEntity().getMetadata().getFlags().getFlag(EntityFlag.GLIDING)) {
            float yaw = session.getPlayerEntity().getRotation().getX();
            float pitch = session.getPlayerEntity().getRotation().getY();
            //Uses math from NukkitX
            session.getPlayerEntity().setMotion(Vector3f.from(
                    -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 2,
                    -Math.sin(Math.toRadians(pitch)) * 2,
                    Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)) * 2));

            SetEntityMotionPacket entityMotionPacket = new SetEntityMotionPacket();
            entityMotionPacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());
            entityMotionPacket.setMotion(session.getPlayerEntity().getMotion());

            session.sendUpstreamPacket(entityMotionPacket);
        }
        super.updateBedrockMetadata(entityMetadata, session);
    }
}