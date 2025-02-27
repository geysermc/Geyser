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

package org.geysermc.geyser.item.type;

import com.google.common.collect.ImmutableMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.item.BedrockEnchantment;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.enchantment.Enchantment;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.ItemMapping;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.item.BedrockItemBuilder;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.MinecraftKey;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentType;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DyedItemColor;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.ItemEnchantments;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Item {
    private static final Map<Block, Item> BLOCK_TO_ITEM = new HashMap<>();
    protected final Key javaIdentifier;
    private int javaId = -1;
    private final int attackDamage;
    private DataComponents baseComponents; // unmodifiable

    public Item(String javaIdentifier, Builder builder) {
        this.javaIdentifier = MinecraftKey.key(javaIdentifier);
        if (builder.components != null) {
            this.baseComponents = builder.components;
        }
        this.attackDamage = builder.attackDamage;
    }

    public String javaIdentifier() {
        return javaIdentifier.asString();
    }

    public int javaId() {
        return javaId;
    }

    public int defaultMaxDamage() {
        return baseComponents.getOrDefault(DataComponentTypes.MAX_DAMAGE, 0);
    }

    public int defaultAttackDamage() {
        return attackDamage;
    }

    public int defaultMaxStackSize() {
        return baseComponents.getOrDefault(DataComponentTypes.MAX_STACK_SIZE, 1);
    }

    /**
     * Returns an unmodifiable {@link DataComponents} view containing known data components.
     * Optionally, additional components can be provided to replace (or add to)
     * the items' base components.
     * To add data components, use {@link GeyserItemStack#getOrCreateComponents()}.
     */
    @NonNull
    @UnmodifiableView
    public DataComponents gatherComponents(@Nullable DataComponents others) {
        if (others == null) {
            return baseComponents;
        }

        // Start with the base components that always exist
        DataComponents components = baseComponents.clone();
        // Add all additional components; these can override base components!
        // e.g. custom stack size
        components.getDataComponents().putAll(others.getDataComponents());

        // Return an unmodified map of the merged components
        return new DataComponents(ImmutableMap.copyOf(components.getDataComponents()));
    }

    /**
     * Returns this items value (or null) for a specific {@link DataComponentType}.
     * Prefer using {@link GeyserItemStack#getComponent(DataComponentType)}
     * to also query additional components that would override the default ones.
     */
    @Nullable
    public <T> T getComponent(@NonNull DataComponentType<T> type) {
        return baseComponents.get(type);
    }

    public String translationKey() {
        return "item." + javaIdentifier.namespace() + "." + javaIdentifier.value();
    }

    /* Translation methods to Bedrock and back */

    public ItemData.Builder translateToBedrock(GeyserSession session, int count, DataComponents components, ItemMapping mapping, ItemMappings mappings) {
        if (this == Items.AIR || count <= 0) {
            // Return, essentially, air
            return ItemData.builder();
        }

        return ItemData.builder()
                .definition(mapping.getBedrockDefinition())
                .damage(mapping.getBedrockData())
                .count(count);
    }

    public @NonNull GeyserItemStack translateToJava(GeyserSession session, @NonNull ItemData itemData, @NonNull ItemMapping mapping, @NonNull ItemMappings mappings) {
        return GeyserItemStack.of(javaId, itemData.getCount());
    }

    public ItemMapping toBedrockDefinition(DataComponents components, ItemMappings mappings) {
        return mappings.getMapping(javaId);
    }

    /**
     * Takes components from Java Edition and map them into Bedrock.
     */
    public void translateComponentsToBedrock(@NonNull GeyserSession session, @NonNull DataComponents components, @NonNull BedrockItemBuilder builder) {
        List<Component> loreComponents = components.get(DataComponentTypes.LORE);
        if (loreComponents != null && components.get(DataComponentTypes.HIDE_TOOLTIP) == null) {
            List<String> lore = builder.getOrCreateLore();
            for (Component loreComponent : loreComponents) {
                lore.add(MessageTranslator.convertMessage(loreComponent, session.locale()));
            }
        }

        Integer damage = components.get(DataComponentTypes.DAMAGE);
        if (damage != null) {
            builder.setDamage(damage);
        }

        List<NbtMap> enchantNbtList = new ArrayList<>();
        ItemEnchantments enchantments = components.get(DataComponentTypes.ENCHANTMENTS);
        if (enchantments != null) {
            for (Map.Entry<Integer, Integer> enchantment : enchantments.getEnchantments().entrySet()) {
                NbtMap enchantNbt = remapEnchantment(session, enchantment.getKey(), enchantment.getValue(), builder);
                if (enchantNbt != null) {
                    enchantNbtList.add(enchantNbt);
                }
            }
        }

        if (!enchantNbtList.isEmpty()) {
            builder.putList("ench", NbtType.COMPOUND, enchantNbtList);
        }

        Integer repairCost = components.get(DataComponentTypes.REPAIR_COST);
        // Java sets repair cost to 0 on all items via default components, that trips up Bedrock crafting.
        // See https://github.com/GeyserMC/Geyser/issues/5220 for more details
        if (repairCost != null && repairCost != 0) {
            builder.putInt("RepairCost", repairCost);
        }

        // If the tag exists, it's unbreakable; the value is just weather to show the tooltip. As of Java 1.21
        if (components.getDataComponents().containsKey(DataComponentTypes.UNBREAKABLE)) {
            builder.putByte("Unbreakable", (byte) 1);
        }

        // Prevents the client from trying to stack items with untranslated components
        // Relies on correct hash code implementation, and some luck
        // However, we should only set a hash when the components differ from the default ones,
        // otherwise Bedrock can't stack these when crafting items since it's predicted recipe output
        // does not contain the GeyserHash. See https://github.com/GeyserMC/Geyser/issues/5220 for more details
        if (!baseComponents.equals(components)) {
            builder.putInt("GeyserHash", components.hashCode()); // TODO: don't rely on this
        }
    }

    /**
     * Takes NBT from Bedrock Edition and converts any value that Java parses differently. <br>
     * Do note that this method is, these days, only called in three places (as of 2023/~1.19):
     * <ul>
     *     <li>Extra recipe loading</li>
     *     <li>Creative menu</li>
     *     <li>Stonecutters</li>
     * </ul>
     * Therefore, if translation cannot be achieved for a certain item, it is not necessarily bad.
     */
    public void translateNbtToJava(@NonNull GeyserSession session, @NonNull NbtMap bedrockTag, @NonNull DataComponents components, @NonNull ItemMapping mapping) {
        // TODO see if any items from the creative menu need this
//        CompoundTag displayTag = tag.get("display");
//        if (displayTag != null) {
//            if (displayTag.contains("Name")) {
//                StringTag nameTag = displayTag.get("Name");
//                displayTag.put(new StringTag("Name", MessageTranslator.convertToJavaMessage(nameTag.getValue())));
//            }
//
//            if (displayTag.contains("Lore")) {
//                ListTag loreTag = displayTag.get("Lore");
//                List<Tag> lore = new ArrayList<>();
//                for (Tag subTag : loreTag.getValue()) {
//                    if (!(subTag instanceof StringTag)) continue;
//                    lore.add(new StringTag("", MessageTranslator.convertToJavaMessage(((StringTag) subTag).getValue())));
//                }
//                displayTag.put(new ListTag("Lore", lore));
//            }
//        }
    }

    protected final @Nullable NbtMap remapEnchantment(GeyserSession session, int enchantId, int level, BedrockItemBuilder builder) {
        Enchantment enchantment = session.getRegistryCache().enchantments().byId(enchantId);
        if (enchantment == null) {
            GeyserLogger.get().debug("Unknown Java enchantment while NBT item translating: " + enchantId);
            return null;
        }

        BedrockEnchantment bedrockEnchantment = enchantment.bedrockEnchantment();
        if (bedrockEnchantment == null) {
            String enchantmentTranslation = MinecraftLocale.getLocaleString(enchantment.description(), session.locale());
            addJavaOnlyEnchantment(session, builder, enchantmentTranslation, level);
            return null;
        }

        return NbtMap.builder()
                .putShort("id", (short) bedrockEnchantment.ordinal())
                .putShort("lvl", (short) level)
                .build();
    }

    private void addJavaOnlyEnchantment(GeyserSession session, BedrockItemBuilder builder, String enchantmentName, int level) {
        String lvlTranslation = MinecraftLocale.getLocaleString("enchantment.level." + level, session.locale());

        builder.getOrCreateLore().add(0, ChatColor.RESET + ChatColor.GRAY + enchantmentName + " " + lvlTranslation);
    }

    protected final void translateDyedColor(DataComponents components, BedrockItemBuilder builder) {
        DyedItemColor dyedItemColor = components.get(DataComponentTypes.DYED_COLOR);
        if (dyedItemColor != null) {
            builder.putInt("customColor", dyedItemColor.getRgb());
        }
    }

    /**
     * Override if the Bedrock equivalent of an item uses damage for extra data, and should not be tracked
     * when translating an item.
     */
    public boolean ignoreDamage() {
        return false;
    }

    /* Translation methods end */

    public GeyserItemStack newItemStack(int count, DataComponents components) {
        return GeyserItemStack.of(this.javaId, count, components);
    }

    public void setJavaId(int javaId) { // TODO like this?
        if (this.javaId != -1) {
            throw new RuntimeException("Item ID has already been set!");
        }
        this.javaId = javaId;
        if (this.baseComponents == null) {
            this.baseComponents = Registries.DEFAULT_DATA_COMPONENTS.get(javaId);
        }
    }

    @Override
    public String toString() {
        return "Item{" +
                "javaIdentifier='" + javaIdentifier + '\'' +
                ", javaId=" + javaId +
                '}';
    }

    /**
     * @return the block associated with this item, or air if nothing
     */
    @NonNull
    public static Item byBlock(Block block) {
        return BLOCK_TO_ITEM.getOrDefault(block, Items.AIR);
    }

    protected static void registerBlock(Block block, Item item) {
        BLOCK_TO_ITEM.put(block, item);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private DataComponents components;
        private int attackDamage;

        public Builder attackDamage(double attackDamage) {
            // Bedrock edition does not support attack damage being a double
            this.attackDamage = (int) attackDamage;
            return this;
        }

        public Builder components(DataComponents components) {
            this.components = components;
            return this;
        }

        private Builder() {
        }
    }
}
