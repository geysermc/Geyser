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

package org.geysermc.geyser.entity.type.living.animal;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.IntEntityMetadata;
import com.google.common.collect.ImmutableList;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import it.unimi.dsi.fastutil.ints.IntList;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.entity.type.living.AbstractFishEntity;
import org.geysermc.geyser.session.GeyserSession;

import java.util.List;
import java.util.UUID;

public class TropicalFishEntity extends AbstractFishEntity {

    /**
     * A list of variant numbers that are given special names
     * The index of the variant in this list is used as part of the locale key
     */
    private static final IntList PREDEFINED_VARIANTS = IntList.of(117506305, 117899265, 185008129, 117441793, 118161664, 65536, 50726144, 67764993, 234882305, 67110144, 117441025, 16778497, 101253888, 50660352, 918529, 235340288, 918273, 67108865, 917504, 459008, 67699456, 67371009);

    private static final List<String> VARIANT_NAMES = ImmutableList.of("kob", "sunstreak", "snooper", "dasher", "brinely", "spotty", "flopper", "stripey", "glitter", "blockfish", "betty", "clayfish");
    private static final List<String> COLOR_NAMES = ImmutableList.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    public TropicalFishEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setFishVariant(IntEntityMetadata entityMetadata) {
        int varNumber = entityMetadata.getPrimitiveValue();

        dirtyMetadata.put(EntityDataTypes.VARIANT, getShape(varNumber)); // Shape 0-1
        dirtyMetadata.put(EntityDataTypes.MARK_VARIANT, getPattern(varNumber)); // Pattern 0-5
        dirtyMetadata.put(EntityDataTypes.COLOR, getBaseColor(varNumber)); // Base color 0-15
        dirtyMetadata.put(EntityDataTypes.COLOR_2, getPatternColor(varNumber)); // Pattern color 0-15
    }

    public static int getShape(int variant) {
        return Math.min(variant & 0xFF, 1);
    }

    public static int getPattern(int variant) {
        return Math.min((variant >> 8) & 0xFF, 5);
    }

    public static byte getBaseColor(int variant) {
        byte color = (byte) ((variant >> 16) & 0xFF);
        if (!(0 <= color && color <= 15)) {
            return 0;
        }
        return color;
    }

    public static byte getPatternColor(int variant) {
        byte color = (byte) ((variant >> 24) & 0xFF);
        if (!(0 <= color && color <= 15)) {
            return 0;
        }
        return color;
    }

    public static String getVariantName(int variant) {
        int id = 6 * getShape(variant) + getPattern(variant);
        return VARIANT_NAMES.get(id);
    }

    public static String getColorName(byte colorId) {
        return COLOR_NAMES.get(colorId);
    }

    public static int getPredefinedId(int variant) {
        return PREDEFINED_VARIANTS.indexOf(variant);
    }
}
