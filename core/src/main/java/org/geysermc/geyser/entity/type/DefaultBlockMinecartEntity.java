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
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.session.GeyserSession;

import java.util.UUID;

/**
 * This class is used as a base for minecarts with a default block to display like furnaces and spawners
 */
public class DefaultBlockMinecartEntity extends MinecartEntity {

    public int customBlock = 0;
    public int customBlockOffset = 0;
    public boolean showCustomBlock = false;

    public DefaultBlockMinecartEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);

        dirtyMetadata.put(EntityDataTypes.CUSTOM_DISPLAY, (byte) 1);
    }

    @Override
    public void spawnEntity() {
        updateDefaultBlockMetadata();
        super.spawnEntity();
    }

    @Override
    public void setCustomBlock(IntEntityMetadata entityMetadata) {
        customBlock = entityMetadata.getPrimitiveValue();

        if (showCustomBlock) {
            dirtyMetadata.put(EntityDataTypes.DISPLAY_BLOCK_STATE, session.getBlockMappings().getBedrockBlock(customBlock));
        }
    }

    @Override
    public void setCustomBlockOffset(IntEntityMetadata entityMetadata) {
        customBlockOffset = entityMetadata.getPrimitiveValue();

        if (showCustomBlock) {
            dirtyMetadata.put(EntityDataTypes.DISPLAY_OFFSET, customBlockOffset);
        }
    }

    @Override
    public void setShowCustomBlock(BooleanEntityMetadata entityMetadata) {
        if (entityMetadata.getPrimitiveValue()) {
            showCustomBlock = true;
            dirtyMetadata.put(EntityDataTypes.DISPLAY_BLOCK_STATE, session.getBlockMappings().getBedrockBlock(customBlock));
            dirtyMetadata.put(EntityDataTypes.DISPLAY_OFFSET, customBlockOffset);
        } else {
            showCustomBlock = false;
            updateDefaultBlockMetadata();
        }
    }

    public void updateDefaultBlockMetadata() {
    }
}
