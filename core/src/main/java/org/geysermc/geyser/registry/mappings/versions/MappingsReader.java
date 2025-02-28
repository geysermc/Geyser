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

package org.geysermc.geyser.registry.mappings.versions;

import com.google.gson.JsonObject;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;
import org.geysermc.geyser.item.exception.InvalidCustomMappingsFileException;
import org.geysermc.geyser.registry.mappings.util.CustomBlockMapping;

import java.nio.file.Path;
import java.util.function.BiConsumer;

public abstract class MappingsReader {
    public abstract void readItemMappings(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomItemData> consumer);
    public abstract void readBlockMappings(Path file, JsonObject mappingsRoot, BiConsumer<String, CustomBlockMapping> consumer);

    public abstract CustomItemData readItemMappingEntry(JsonObject node) throws InvalidCustomMappingsFileException;
    public abstract CustomBlockMapping readBlockMappingEntry(String identifier, JsonObject node) throws InvalidCustomMappingsFileException;

    protected @Nullable CustomRenderOffsets fromJsonObject(JsonObject node) {
        if (node == null) {
            return null;
        }

        return new CustomRenderOffsets(
                getHandOffsets(node, "main_hand"),
                getHandOffsets(node, "off_hand")
        );
    }

    protected CustomRenderOffsets.@Nullable Hand getHandOffsets(JsonObject node, String hand) {
        if (!(node.get(hand) instanceof JsonObject tmpNode)) {
            return null;
        }

        return new CustomRenderOffsets.Hand(
                getPerspectiveOffsets(tmpNode, "first_person"),
                getPerspectiveOffsets(tmpNode, "third_person")
        );
    }

    protected CustomRenderOffsets.@Nullable Offset getPerspectiveOffsets(JsonObject node, String perspective) {
        if (!(node.get(perspective) instanceof JsonObject tmpNode)) {
            return null;
        }

        return new CustomRenderOffsets.Offset(
                getOffsetXYZ(tmpNode, "position"),
                getOffsetXYZ(tmpNode, "rotation"),
                getOffsetXYZ(tmpNode, "scale")
        );
    }

    protected CustomRenderOffsets.@Nullable OffsetXYZ getOffsetXYZ(JsonObject node, String offsetType) {
        if (!(node.get(offsetType) instanceof JsonObject tmpNode)) {
            return null;
        }

        if (!tmpNode.has("x") || !tmpNode.has("y") || !tmpNode.has("z")) {
            return null;
        }

        return new CustomRenderOffsets.OffsetXYZ(
                tmpNode.get("x").getAsFloat(),
                tmpNode.get("y").getAsFloat(),
                tmpNode.get("z").getAsFloat()
        );
    }
}
