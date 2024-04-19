/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;

import java.util.UUID;

public class InteractionEntity extends Entity {

    /**
     * true - java client hears swing sound when attacking, and arm swings when right-clicking
     * false - java client hears no swing sound when attacking, and arm does not swing when right-clicking
     */
    private boolean response = false;

    public InteractionEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();

        // hide the armor stand but keep the hitbox active
        setFlag(EntityFlag.INVISIBLE, true);
    }

    @Override
    public InteractionResult interact(Hand hand) {
        // these InteractionResults do mirror the java client
        // but the bedrock client won't arm swing itself because of our armor stand workaround
        if (response) {
            AnimatePacket animatePacket = new AnimatePacket();
            animatePacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());
            animatePacket.setAction(AnimatePacket.Action.SWING_ARM);
            session.sendUpstreamPacket(animatePacket);

            session.sendDownstreamGamePacket(new ServerboundSwingPacket(hand));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.CONSUME;
    }

    public void setWidth(FloatEntityMetadata width) {
        setBoundingBoxWidth(width.getPrimitiveValue());
    }

    public void setHeight(FloatEntityMetadata height) {
        setBoundingBoxHeight(height.getPrimitiveValue());
    }

    public void setResponse(BooleanEntityMetadata response) {
        this.response = response.getPrimitiveValue();
    }
}
