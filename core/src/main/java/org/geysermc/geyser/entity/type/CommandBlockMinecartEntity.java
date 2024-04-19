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

import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;

public class CommandBlockMinecartEntity extends DefaultBlockMinecartEntity {

    public CommandBlockMinecartEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        // Required, or else the GUI will not open
        dirtyMetadata.put(EntityDataTypes.CONTAINER_TYPE, (byte) 16);
        dirtyMetadata.put(EntityDataTypes.CONTAINER_SIZE, 1);
        // Required, or else the client does not bother to send a packet back with the new information
        dirtyMetadata.put(EntityDataTypes.COMMAND_BLOCK_ENABLED, true);
    }

    /**
     * By default, the command block shown is purple on Bedrock, which does not match Java Edition's orange.
     */
    @Override
    public void updateDefaultBlockMetadata() {
        dirtyMetadata.put(EntityDataTypes.DISPLAY_BLOCK_STATE, session.getBlockMappings().getCommandBlock());
        dirtyMetadata.put(EntityDataTypes.DISPLAY_OFFSET, 6);
    }

    @Override
    protected InteractiveTag testInteraction(Hand hand) {
        if (session.canUseCommandBlocks()) {
            return InteractiveTag.OPEN_CONTAINER;
        } else {
            return InteractiveTag.NONE;
        }
    }

    @Override
    public InteractionResult interact(Hand hand) {
        if (session.canUseCommandBlocks()) {
            // Client-side GUI required
            ContainerOpenPacket openPacket = new ContainerOpenPacket();
            openPacket.setBlockPosition(Vector3i.ZERO);
            openPacket.setId((byte) 1);
            openPacket.setType(ContainerType.COMMAND_BLOCK);
            openPacket.setUniqueEntityId(geyserId);
            session.sendUpstreamPacket(openPacket);

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }
}
