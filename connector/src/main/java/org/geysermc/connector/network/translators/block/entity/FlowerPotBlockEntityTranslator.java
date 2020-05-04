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

package org.geysermc.connector.network.translators.block.entity;

import com.github.steveice10.mc.protocol.data.game.world.block.BlockState;
import com.nukkitx.math.vector.Vector3i;
import com.nukkitx.nbt.CompoundTagBuilder;
import com.nukkitx.nbt.tag.CompoundTag;
import com.nukkitx.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.block.BlockStateValues;
import org.geysermc.connector.network.translators.block.BlockTranslator;
import org.geysermc.connector.utils.BlockEntityUtils;

public class FlowerPotBlockEntityTranslator implements BedrockOnlyBlockEntity, RequiresBlockState {

    @Override
    public boolean isBlock(BlockState blockState) {
        return BlockStateValues.getFlowerPotValues().containsKey(blockState.getId());
    }

    @Override
    public void updateBlock(GeyserSession session, BlockState blockState, Vector3i position) {
        CompoundTagBuilder tagBuilder = CompoundTagBuilder.builder()
                .intTag("x", position.getX())
                .intTag("y", position.getY())
                .intTag("z", position.getZ())
                .byteTag("isMovable", (byte) 1)
                .stringTag("id", "FlowerPot");
        String name = BlockStateValues.getFlowerPotValues().get(blockState.getId());
        System.out.println(name);
        if (name != null) {
            CompoundTag plant = null;
            if (plant != null) {
                tagBuilder.tag(plant.toBuilder().build("PlantBlock"));
            }
        }
        BlockEntityUtils.updateBlockEntity(session, tagBuilder.buildRootTag(), position);
        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setRuntimeId(BlockTranslator.getBedrockBlockId(blockState));
        updateBlockPacket.setBlockPosition(position);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.PRIORITY);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NONE);
        updateBlockPacket.getFlags().add(UpdateBlockPacket.Flag.NEIGHBORS);
        session.getUpstream().sendPacket(updateBlockPacket);
    }
}
