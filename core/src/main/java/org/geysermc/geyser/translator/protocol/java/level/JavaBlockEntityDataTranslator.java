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

package org.geysermc.geyser.translator.protocol.java.level;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.level.block.BlockEntityType;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundBlockEntityDataPacket;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.geysermc.geyser.level.block.BlockStateValues;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.level.block.entity.BlockEntityTranslator;
import org.geysermc.geyser.translator.level.block.entity.RequiresBlockState;
import org.geysermc.geyser.translator.level.block.entity.SkullBlockEntityTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockEntityUtils;

@Translator(packet = ClientboundBlockEntityDataPacket.class)
public class JavaBlockEntityDataTranslator extends PacketTranslator<ClientboundBlockEntityDataPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundBlockEntityDataPacket packet) {
        final BlockEntityType type = packet.getType();
        if (type == null) {
            return;
        }
        BlockEntityTranslator translator = BlockEntityUtils.getBlockEntityTranslator(type);
        // The Java block state is used in BlockEntityTranslator.translateTag() to make up for some inconsistencies
        // between Java block states and Bedrock block entity data
        int blockState;
        if (translator instanceof RequiresBlockState) {
            blockState = session.getGeyser().getWorldManager().getBlockAt(session, packet.getPosition());
        } else {
            blockState = BlockStateValues.JAVA_AIR_ID;
        }

        Vector3i position = packet.getPosition();
        BlockEntityUtils.updateBlockEntity(session, translator.getBlockEntityTag(type, position.getX(), position.getY(), position.getZ(),
                packet.getNbt(), blockState), packet.getPosition());
        // Check for custom skulls.
        if (session.getPreferencesCache().getEffectiveShowSkulls() && packet.getNbt() != null && packet.getNbt().contains("SkullOwner")) {
            SkullBlockEntityTranslator.translateSkull(session, packet.getNbt(), position.getX(), position.getY(), position.getZ(), blockState);
        }

        // If block entity is command block, OP permission level is appropriate, player is in creative mode and the NBT is not empty
        // TODO 1.18 re-test
        if (type == BlockEntityType.COMMAND_BLOCK && session.getOpPermissionLevel() >= 2 &&
                session.getGameMode() == GameMode.CREATIVE && packet.getNbt() != null && packet.getNbt().size() > 5) {
            ContainerOpenPacket openPacket = new ContainerOpenPacket();
            openPacket.setBlockPosition(position);
            openPacket.setId((byte) 1);
            openPacket.setType(ContainerType.COMMAND_BLOCK);
            openPacket.setUniqueEntityId(-1);
            session.sendUpstreamPacket(openPacket);
        }
    }
}
