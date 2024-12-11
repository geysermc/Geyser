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

package org.geysermc.geyser.item;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.item.custom.CustomBlockPlacer;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.CustomRenderOffsets;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@ToString
public final class GeyserNonVanillaCustomItemData extends GeyserCustomItemData implements NonVanillaCustomItemData {
    private final String identifier;
    private final int javaId;
    private final int stackSize;
    private final int maxDamage;
    private final int attackDamage;
    private final String toolType;
    private final String toolTier;
    private final String armorType;
    private final int protectionValue;
    private final String translationString;
    private final Set<String> repairMaterials;
    private final boolean isHat;
    private final boolean isFoil;
    private final boolean isTool;
    private final boolean isEdible;
    private final boolean canAlwaysEat;
    private final boolean isChargeable;
    private final CustomBlockPlacer blockPlacer;

    public GeyserNonVanillaCustomItemData(Builder builder) {
        super(builder.name, builder.customItemOptions, builder.displayName, builder.icon, builder.allowOffhand,
                builder.displayHandheld, builder.creativeCategory, builder.creativeGroup,
                builder.textureSize, builder.renderOffsets, builder.tags);

        this.identifier = builder.identifier;
        this.javaId = builder.javaId;
        this.stackSize = builder.stackSize;
        this.maxDamage = builder.maxDamage;
        this.attackDamage = builder.attackDamage;
        this.toolType = builder.toolType;
        this.toolTier = builder.toolTier;
        this.armorType = builder.armorType;
        this.protectionValue = builder.protectionValue;
        this.translationString = builder.translationString;
        this.repairMaterials = builder.repairMaterials;
        this.isHat = builder.hat;
        this.isFoil = builder.foil;
        this.isTool = builder.tool;
        this.isEdible = builder.edible;
        this.canAlwaysEat = builder.canAlwaysEat;
        this.isChargeable = builder.chargeable;
        this.blockPlacer = builder.blockPlacer;
    }

    @Override
    public @NonNull String identifier() {
        return identifier;
    }

    @Override
    public int javaId() {
        return javaId;
    }

    @Override
    public int stackSize() {
        return stackSize;
    }

    @Override
    public int maxDamage() {
        return maxDamage;
    }

    @Override
    public int attackDamage() {
        return attackDamage;
    }

    @Override
    public String toolType() {
        return toolType;
    }

    @Override
    public String toolTier() {
        return toolTier;
    }

    @Override
    public @Nullable String armorType() {
        return armorType;
    }

    @Override
    public int protectionValue() {
        return protectionValue;
    }

    @Override
    public String translationString() {
        return translationString;
    }

    @Override
    public Set<String> repairMaterials() {
        return repairMaterials;
    }

    @Override
    public boolean isHat() {
        return isHat;
    }

    @Override
    public boolean isFoil() {
        return isFoil;
    }

    @Override
    public boolean isEdible() {
        return isEdible;
    }

    @Override
    public boolean canAlwaysEat() {
        return canAlwaysEat;
    }

    @Override
    public boolean isChargeable() {
        return isChargeable;
    }

    @Override
    public String block() {
        return blockPlacer == null ? null : blockPlacer.block();
    }

    @Override
    public CustomBlockPlacer blockPlacer() {
        return blockPlacer;
    }

    public static class Builder extends GeyserCustomItemData.Builder implements NonVanillaCustomItemData.Builder {
        private String identifier = null;
        private int javaId = -1;

        private int stackSize = 64;

        private int maxDamage = 0;

        private int attackDamage = 0;

        private String toolType = null;
        private String toolTier = null;

        private String armorType = null;
        private int protectionValue = 0;

        private String translationString;

        private Set<String> repairMaterials;

        private boolean hat = false;
        private boolean foil = false;
        private boolean tool = false;
        private boolean edible = false;
        private boolean canAlwaysEat = false;
        private boolean chargeable = false;
        private CustomBlockPlacer blockPlacer = null;

        @Override
        public Builder name(@NonNull String name) {
            return (Builder) super.name(name);
        }

        @Override
        public Builder customItemOptions(@NonNull CustomItemOptions customItemOptions) {
            //Do nothing, as that value won't be read
            return this;
        }

        @Override
        public Builder allowOffhand(boolean allowOffhand) {
            return (Builder) super.allowOffhand(allowOffhand);
        }

        @Override
        public Builder displayHandheld(boolean displayHandheld) {
            return (Builder) super.displayHandheld(displayHandheld);
        }

        @Override
        public Builder displayName(@NonNull String displayName) {
            return (Builder) super.displayName(displayName);
        }

        @Override
        public Builder icon(@NonNull String icon) {
            return (Builder) super.icon(icon);
        }

        @Override
        public Builder textureSize(int textureSize) {
            return (Builder) super.textureSize(textureSize);
        }

        @Override
        public Builder renderOffsets(CustomRenderOffsets renderOffsets) {
            return (Builder) super.renderOffsets(renderOffsets);
        }

        @Override
        public Builder tags(@Nullable Set<String> tags) {
            return (Builder) super.tags(tags);
        }

        @Override
        public Builder identifier(@NonNull String identifier) {
            this.identifier = identifier;
            return this;
        }

        @Override
        public Builder javaId(int javaId) {
            this.javaId = javaId;
            return this;
        }

        @Override
        public Builder stackSize(int stackSize) {
            this.stackSize = stackSize;
            return this;
        }

        @Override
        public Builder maxDamage(int maxDamage) {
            this.maxDamage = maxDamage;
            return this;
        }

        @Override
        public NonVanillaCustomItemData.Builder attackDamage(int attackDamage) {
            this.attackDamage = attackDamage;
            return this;
        }

        @Override
        public Builder toolType(@Nullable String toolType) {
            this.toolType = toolType;
            return this;
        }

        @Override
        public Builder toolTier(@Nullable String toolTier) {
            this.toolTier = toolTier;
            return this;
        }

        @Override
        public Builder armorType(@Nullable String armorType) {
            this.armorType = armorType;
            return this;
        }

        @Override
        public Builder protectionValue(int protectionValue) {
            this.protectionValue = protectionValue;
            return this;
        }

        @Override
        public Builder translationString(@Nullable String translationString) {
            this.translationString = translationString;
            return this;
        }

        @Override
        public Builder repairMaterials(@Nullable Set<String> repairMaterials) {
            this.repairMaterials = repairMaterials;
            return this;
        }

        @Override
        public Builder creativeCategory(int creativeCategory) {
            return (Builder) super.creativeCategory(creativeCategory);
        }

        @Override
        public Builder creativeGroup(@Nullable String creativeGroup) {
            return (Builder) super.creativeGroup(creativeGroup);
        }

        @Override
        public Builder hat(boolean isHat) {
            this.hat = isHat;
            return this;
        }

        @Override
        public Builder foil(boolean isFoil) {
            this.foil = isFoil;
            return this;
        }

        @Override
        public Builder edible(boolean isEdible) {
            this.edible = isEdible;
            return this;
        }

        @Override
        public Builder canAlwaysEat(boolean canAlwaysEat) {
            this.canAlwaysEat = canAlwaysEat;
            return this;
        }

        @Override
        public Builder chargeable(boolean isChargeable) {
            this.chargeable = isChargeable;
            return this;
        }

        @Override
        public Builder block(String block) {
            this.blockPlacer = new CustomBlockPlacer(block, false);
            return this;
        }

        @Override
        public Builder blockPlacer(CustomBlockPlacer blockPlacer) {
            this.blockPlacer = blockPlacer;
            return this;
        }

        @Override
        public NonVanillaCustomItemData build() {
            if (identifier == null || javaId == -1) {
                throw new IllegalArgumentException("Identifier and javaId must be set");
            }

            super.customItemOptions(CustomItemOptions.builder().build());
            super.build();
            return new GeyserNonVanillaCustomItemData(this);
        }
    }
}
