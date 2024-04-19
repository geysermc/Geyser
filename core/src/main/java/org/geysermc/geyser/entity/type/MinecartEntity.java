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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InteractiveTag;

import java.util.UUID;

public class MinecartEntity extends Entity {

    public MinecartEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position.add(0d, definition.offset(), 0d), motion, yaw, pitch, headYaw);
    }

    public void setCustomBlock(IntEntityMetadata entityMetadata) {
        dirtyMetadata.put(EntityDataTypes.DISPLAY_BLOCK_STATE, session.getBlockMappings().getBedrockBlock(entityMetadata.getPrimitiveValue()));
    }

    public void setCustomBlockOffset(IntEntityMetadata entityMetadata) {
        dirtyMetadata.put(EntityDataTypes.DISPLAY_OFFSET, entityMetadata.getPrimitiveValue());
    }

    public void setShowCustomBlock(BooleanEntityMetadata entityMetadata) {
        // If the custom block should be enabled
        // Needs a byte based off of Java's boolean
        dirtyMetadata.put(EntityDataTypes.CUSTOM_DISPLAY, (byte) (entityMetadata.getPrimitiveValue() ? 1 : 0));
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        super.moveAbsolute(position.add(0d, this.definition.offset(), 0d), yaw, pitch, headYaw, isOnGround, teleported);
    }

    @Override
    public Vector3f getBedrockRotation() {
        // Note: minecart rotation on rails does not care about the actual rotation value
        return Vector3f.from(0, getYaw(), 0);
    }

    @Override
    protected InteractiveTag testInteraction(Hand hand) {
        if (definition == EntityDefinitions.CHEST_MINECART || definition == EntityDefinitions.HOPPER_MINECART) {
            return InteractiveTag.OPEN_CONTAINER;
        } else {
            if (session.isSneaking()) {
                return InteractiveTag.NONE;
            } else if (!passengers.isEmpty()) {
                // Can't enter if someone is inside
                return InteractiveTag.NONE;
            } else {
                // Attempt to enter
                return InteractiveTag.RIDE_MINECART;
            }
        }
    }

    @Override
    public InteractionResult interact(Hand hand) {
        if (definition == EntityDefinitions.CHEST_MINECART || definition == EntityDefinitions.HOPPER_MINECART) {
            // Opening the UI of this minecart
            return InteractionResult.SUCCESS;
        } else {
            if (session.isSneaking()) {
                return InteractionResult.PASS;
            } else if (!passengers.isEmpty()) {
                // Can't enter if someone is inside
                return InteractionResult.PASS;
            } else {
                // Attempt to enter
                return InteractionResult.SUCCESS;
            }
        }
    }
}
