/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.network.translators.world.block.entity;

import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.nukkitx.nbt.NbtMapBuilder;
import com.nukkitx.protocol.bedrock.data.structure.StructureMirror;
import com.nukkitx.protocol.bedrock.data.structure.StructureRotation;

@BlockEntity(name = "StructureBlock")
public class StructureBlockBlockEntityTranslator extends BlockEntityTranslator {
    @Override
    public void translateTag(NbtMapBuilder builder, CompoundTag tag, int blockState) {
        if (tag.size() < 5) {
            return; // These values aren't here
        }

        builder.putString("structureName", getOrDefault(tag.get("name"), ""));

        String mode = getOrDefault(tag.get("mode"), "");
        int bedrockData;
        // Corner and data don't actually translate, but they also don't crash
        switch (mode) {
            case "LOAD":
                bedrockData = 2;
                break;
            case "CORNER":
                bedrockData = 3;
                break;
            case "DATA":
                bedrockData = 4;
                break;
            default: // SAVE
                bedrockData = 1;
                break;
        }
        // 5 is Bedrock's "3D Export" option but Java doesn't have that.
        builder.putInt("data", bedrockData);
        builder.putString("dataField", ""); // ??? possibly related to Java's "metadata"

        // Mirror behaves different in Java and Bedrock - it requires modifying the position in space as well
        String mirror = getOrDefault(tag.get("mirror"), "");
        byte bedrockMirror;
        switch (mirror) {
            case "LEFT_RIGHT":
                bedrockMirror = 1;
                break;
            case "FRONT_BACK":
                bedrockMirror = 2;
                break;
            default: // Or NONE
                bedrockMirror = 0;
                break;
        }
        builder.putByte("mirror", bedrockMirror);

        builder.putByte("ignoreEntities", getOrDefault(tag.get("ignoreEntities"), (byte) 0));
        builder.putByte("isPowered", getOrDefault(tag.get("powered"), (byte) 0));
        builder.putLong("seed", getOrDefault(tag.get("seed"), 0L));
        builder.putByte("showBoundingBox", getOrDefault(tag.get("showboundingbox"), (byte) 0));

        String rotation = getOrDefault(tag.get("rotation"), "");
        byte bedrockRotation;
        switch (rotation) {
            case "CLOCKWISE_90":
                bedrockRotation = 1;
                break;
            case "CLOCKWISE_180":
                bedrockRotation = 2;
                break;
            case "COUNTERCLOCKWISE_90":
                bedrockRotation = 3;
                break;
            default: // Or NONE keep it as 0
                bedrockRotation = 0;
                break;
        }
        builder.putByte("rotation", bedrockRotation);

        int xStructureSize = getOrDefault(tag.get("sizeX"), 0);
        int zStructureSize = getOrDefault(tag.get("sizeZ"), 0);
        int newXStructureSize = xStructureSize;
        int newZStructureSize = zStructureSize;

        // The "positions" are also offsets on Java
        int posX = getOrDefault(tag.get("posX"), 0);
        int posZ = getOrDefault(tag.get("posZ"), 0);

        // Modify positions if mirrored - Bedrock doesn't have this
        if (bedrockMirror == (byte) StructureMirror.Z.ordinal()) {
            posX = posX + xStructureSize;
            newXStructureSize = xStructureSize * -1;
        } else if (bedrockMirror == (byte) StructureMirror.X.ordinal()) {
            posZ = posZ + zStructureSize;
            newZStructureSize = zStructureSize * -1;
        }

        // Bedrock rotates with the same origin; Java does not
        StructureRotation structureRotation = StructureRotation.values()[bedrockRotation];
        switch (structureRotation) {
            case ROTATE_90:
                if (xStructureSize >= 0) {
                    posX += 1;
                }
                if (zStructureSize < 0) {
                    posZ += 1;
                }
                posX -= zStructureSize;
                break;
            case ROTATE_180:
                if (xStructureSize >= 0) {
                    posX += 1;
                }
                if (zStructureSize >= 0) {
                    posZ += 1;
                }
                posX -= xStructureSize;
                posZ -= zStructureSize;
                break;
            case ROTATE_270:
                if (xStructureSize < 0) {
                    posX += 1;
                }
                if (zStructureSize >= 0) {
                    posZ += 1;
                }
                posZ -= xStructureSize;
                break;
            default:
                if (xStructureSize < 0) {
                    posX += 1;
                }
                if (zStructureSize < 0) {
                    posZ += 1;
                }
                break;
        }

        builder.putInt("xStructureSize", newXStructureSize);
        builder.putInt("yStructureSize", getOrDefault(tag.get("sizeY"), 0));
        builder.putInt("zStructureSize", newZStructureSize);

        builder.putInt("xStructureOffset", posX);
        builder.putInt("yStructureOffset", getOrDefault(tag.get("posY"), 0));
        builder.putInt("zStructureOffset", posZ);

        builder.putFloat("integrity", getOrDefault(tag.get("integrity"), 0f)); // Is 1.0f by default on Java but 100.0f on Bedrock

        // Java's "showair" is unrepresented
    }
}
