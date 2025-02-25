/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.level.block.entity;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;

@BlockEntity(type = BlockEntityType.BRUSHABLE_BLOCK)
public class BrushableBlockEntityTranslator extends BlockEntityTranslator implements RequiresBlockState {
    @Override
    public void translateTag(GeyserSession session, NbtMapBuilder bedrockNbt, @Nullable NbtMap javaNbt, BlockState blockState) {
        if (javaNbt == null) {
            return;
        }

        NbtMap itemTag = javaNbt.getCompound("item");
        if (itemTag.isEmpty()) {
            return;
        }
        byte hitDirection = javaNbt.getByte("hit_direction", (byte) -1);
        if (hitDirection == -1) {
            // java server sends no direction when the item recedes back into the block (if player stops brushing)
            return;
        }

        String id = itemTag.getString("id");
        if (Items.AIR.javaIdentifier().equals(id)) {
            return; // server sends air when the block contains nothing
        }

        ItemMapping mapping = session.getItemMappings().getMapping(id);
        if (mapping == null) {
            return;
        }
        NbtMapBuilder itemBuilder = NbtMap.builder()
            .putString("Name", mapping.getBedrockIdentifier())
            .putByte("Count", itemTag.getByte("Count"));

        bedrockNbt.putCompound("item", itemBuilder.build());
        // controls which side the item protrudes from
        bedrockNbt.putByte("brush_direction", hitDirection);
        // controls how much the item protrudes
        bedrockNbt.putInt("brush_count", blockState.getValue(Properties.DUSTED));
    }
}
