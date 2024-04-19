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

package org.geysermc.geyser.entity;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.geysermc.geyser.entity.type.*;
import org.geysermc.geyser.entity.type.living.*;
import org.geysermc.geyser.entity.type.living.animal.*;
import org.geysermc.geyser.entity.type.living.animal.horse.*;
import org.geysermc.geyser.entity.type.living.animal.tameable.CatEntity;
import org.geysermc.geyser.entity.type.living.animal.tameable.ParrotEntity;
import org.geysermc.geyser.entity.type.living.animal.tameable.TameableEntity;
import org.geysermc.geyser.entity.type.living.animal.tameable.WolfEntity;
import org.geysermc.geyser.entity.type.living.merchant.AbstractMerchantEntity;
import org.geysermc.geyser.entity.type.living.merchant.VillagerEntity;
import org.geysermc.geyser.entity.type.living.monster.*;
import org.geysermc.geyser.entity.type.living.monster.raid.PillagerEntity;
import org.geysermc.geyser.entity.type.living.monster.raid.RaidParticipantEntity;
import org.geysermc.geyser.entity.type.living.monster.raid.SpellcasterIllagerEntity;
import org.geysermc.geyser.entity.type.living.monster.raid.VindicatorEntity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.translator.text.MessageTranslator;

public final class EntityDefinitions {
    public static final GeyserEntityDefinition<AllayEntity> ALLAY;
    public static final GeyserEntityDefinition<AreaEffectCloudEntity> AREA_EFFECT_CLOUD;
    public static final GeyserEntityDefinition<ArmorStandEntity> ARMOR_STAND;
    public static final GeyserEntityDefinition<TippedArrowEntity> ARROW;
    public static final GeyserEntityDefinition<AxolotlEntity> AXOLOTL;
    public static final GeyserEntityDefinition<BatEntity> BAT;
    public static final GeyserEntityDefinition<BeeEntity> BEE;
    public static final GeyserEntityDefinition<BlazeEntity> BLAZE;
    public static final GeyserEntityDefinition<BoatEntity> BOAT;
    public static final GeyserEntityDefinition<CamelEntity> CAMEL;
    public static final GeyserEntityDefinition<CatEntity> CAT;
    public static final GeyserEntityDefinition<SpiderEntity> CAVE_SPIDER;
    public static final GeyserEntityDefinition<MinecartEntity> CHEST_MINECART;
    public static final GeyserEntityDefinition<ChickenEntity> CHICKEN;
    public static final GeyserEntityDefinition<ChestBoatEntity> CHEST_BOAT;
    public static final GeyserEntityDefinition<AbstractFishEntity> COD;
    public static final GeyserEntityDefinition<CommandBlockMinecartEntity> COMMAND_BLOCK_MINECART;
    public static final GeyserEntityDefinition<CowEntity> COW;
    public static final GeyserEntityDefinition<CreeperEntity> CREEPER;
    public static final GeyserEntityDefinition<DolphinEntity> DOLPHIN;
    public static final GeyserEntityDefinition<ChestedHorseEntity> DONKEY;
    public static final GeyserEntityDefinition<FireballEntity> DRAGON_FIREBALL;
    public static final GeyserEntityDefinition<ZombieEntity> DROWNED;
    public static final GeyserEntityDefinition<ThrowableItemEntity> EGG;
    public static final GeyserEntityDefinition<ElderGuardianEntity> ELDER_GUARDIAN;
    public static final GeyserEntityDefinition<EndermanEntity> ENDERMAN;
    public static final GeyserEntityDefinition<MonsterEntity> ENDERMITE;
    public static final GeyserEntityDefinition<EnderDragonEntity> ENDER_DRAGON;
    public static final GeyserEntityDefinition<ThrowableItemEntity> ENDER_PEARL;
    public static final GeyserEntityDefinition<EnderCrystalEntity> END_CRYSTAL;
    public static final GeyserEntityDefinition<SpellcasterIllagerEntity> EVOKER;
    public static final GeyserEntityDefinition<EvokerFangsEntity> EVOKER_FANGS;
    public static final GeyserEntityDefinition<ThrowableItemEntity> EXPERIENCE_BOTTLE;
    public static final GeyserEntityDefinition<ExpOrbEntity> EXPERIENCE_ORB;
    public static final GeyserEntityDefinition<Entity> EYE_OF_ENDER;
    public static final GeyserEntityDefinition<FallingBlockEntity> FALLING_BLOCK;
    public static final GeyserEntityDefinition<FireballEntity> FIREBALL;
    public static final GeyserEntityDefinition<FireworkEntity> FIREWORK_ROCKET;
    public static final GeyserEntityDefinition<FishingHookEntity> FISHING_BOBBER;
    public static final GeyserEntityDefinition<FoxEntity> FOX;
    public static final GeyserEntityDefinition<FrogEntity> FROG;
    public static final GeyserEntityDefinition<FurnaceMinecartEntity> FURNACE_MINECART; // Not present on Bedrock
    public static final GeyserEntityDefinition<GhastEntity> GHAST;
    public static final GeyserEntityDefinition<GiantEntity> GIANT;
    public static final GeyserEntityDefinition<ItemFrameEntity> GLOW_ITEM_FRAME;
    public static final GeyserEntityDefinition<GlowSquidEntity> GLOW_SQUID;
    public static final GeyserEntityDefinition<GoatEntity> GOAT;
    public static final GeyserEntityDefinition<GuardianEntity> GUARDIAN;
    public static final GeyserEntityDefinition<HoglinEntity> HOGLIN;
    public static final GeyserEntityDefinition<MinecartEntity> HOPPER_MINECART;
    public static final GeyserEntityDefinition<HorseEntity> HORSE;
    public static final GeyserEntityDefinition<ZombieEntity> HUSK;
    public static final GeyserEntityDefinition<SpellcasterIllagerEntity> ILLUSIONER; // Not present on Bedrock
    public static final GeyserEntityDefinition<InteractionEntity> INTERACTION;
    public static final GeyserEntityDefinition<IronGolemEntity> IRON_GOLEM;
    public static final GeyserEntityDefinition<ItemEntity> ITEM;
    public static final GeyserEntityDefinition<ItemFrameEntity> ITEM_FRAME;
    public static final GeyserEntityDefinition<LeashKnotEntity> LEASH_KNOT;
    public static final GeyserEntityDefinition<LightningEntity> LIGHTNING_BOLT;
    public static final GeyserEntityDefinition<LlamaEntity> LLAMA;
    public static final GeyserEntityDefinition<ThrowableEntity> LLAMA_SPIT;
    public static final GeyserEntityDefinition<MagmaCubeEntity> MAGMA_CUBE;
    public static final GeyserEntityDefinition<MinecartEntity> MINECART;
    public static final GeyserEntityDefinition<MooshroomEntity> MOOSHROOM;
    public static final GeyserEntityDefinition<ChestedHorseEntity> MULE;
    public static final GeyserEntityDefinition<OcelotEntity> OCELOT;
    public static final GeyserEntityDefinition<PaintingEntity> PAINTING;
    public static final GeyserEntityDefinition<PandaEntity> PANDA;
    public static final GeyserEntityDefinition<ParrotEntity> PARROT;
    public static final GeyserEntityDefinition<PhantomEntity> PHANTOM;
    public static final GeyserEntityDefinition<PigEntity> PIG;
    public static final GeyserEntityDefinition<PiglinEntity> PIGLIN;
    public static final GeyserEntityDefinition<BasePiglinEntity> PIGLIN_BRUTE;
    public static final GeyserEntityDefinition<PillagerEntity> PILLAGER;
    public static final GeyserEntityDefinition<PlayerEntity> PLAYER;
    public static final GeyserEntityDefinition<PolarBearEntity> POLAR_BEAR;
    public static final GeyserEntityDefinition<ThrownPotionEntity> POTION;
    public static final GeyserEntityDefinition<PufferFishEntity> PUFFERFISH;
    public static final GeyserEntityDefinition<RabbitEntity> RABBIT;
    public static final GeyserEntityDefinition<RaidParticipantEntity> RAVAGER;
    public static final GeyserEntityDefinition<AbstractFishEntity> SALMON;
    public static final GeyserEntityDefinition<SheepEntity> SHEEP;
    public static final GeyserEntityDefinition<ShulkerEntity> SHULKER;
    public static final GeyserEntityDefinition<SnifferEntity> SNIFFER;
    public static final GeyserEntityDefinition<ThrowableEntity> SHULKER_BULLET;
    public static final GeyserEntityDefinition<MonsterEntity> SILVERFISH;
    public static final GeyserEntityDefinition<SkeletonEntity> SKELETON;
    public static final GeyserEntityDefinition<SkeletonHorseEntity> SKELETON_HORSE;
    public static final GeyserEntityDefinition<SlimeEntity> SLIME;
    public static final GeyserEntityDefinition<FireballEntity> SMALL_FIREBALL;
    public static final GeyserEntityDefinition<ThrowableItemEntity> SNOWBALL;
    public static final GeyserEntityDefinition<SnowGolemEntity> SNOW_GOLEM;
    public static final GeyserEntityDefinition<SpawnerMinecartEntity> SPAWNER_MINECART; // Not present on Bedrock
    public static final GeyserEntityDefinition<AbstractArrowEntity> SPECTRAL_ARROW;
    public static final GeyserEntityDefinition<SpiderEntity> SPIDER;
    public static final GeyserEntityDefinition<SquidEntity> SQUID;
    public static final GeyserEntityDefinition<AbstractSkeletonEntity> STRAY;
    public static final GeyserEntityDefinition<StriderEntity> STRIDER;
    public static final GeyserEntityDefinition<TadpoleEntity> TADPOLE;
    public static final GeyserEntityDefinition<TextDisplayEntity> TEXT_DISPLAY;
    public static final GeyserEntityDefinition<TNTEntity> TNT;
    public static final GeyserEntityDefinition<MinecartEntity> TNT_MINECART;
    public static final GeyserEntityDefinition<TraderLlamaEntity> TRADER_LLAMA;
    public static final GeyserEntityDefinition<TridentEntity> TRIDENT;
    public static final GeyserEntityDefinition<TropicalFishEntity> TROPICAL_FISH;
    public static final GeyserEntityDefinition<TurtleEntity> TURTLE;
    public static final GeyserEntityDefinition<VexEntity> VEX;
    public static final GeyserEntityDefinition<VillagerEntity> VILLAGER;
    public static final GeyserEntityDefinition<VindicatorEntity> VINDICATOR;
    public static final GeyserEntityDefinition<AbstractMerchantEntity> WANDERING_TRADER;
    public static final GeyserEntityDefinition<WardenEntity> WARDEN;
    public static final GeyserEntityDefinition<RaidParticipantEntity> WITCH;
    public static final GeyserEntityDefinition<WitherEntity> WITHER;
    public static final GeyserEntityDefinition<AbstractSkeletonEntity> WITHER_SKELETON;
    public static final GeyserEntityDefinition<WitherSkullEntity> WITHER_SKULL;
    public static final GeyserEntityDefinition<WolfEntity> WOLF;
    public static final GeyserEntityDefinition<ZoglinEntity> ZOGLIN;
    public static final GeyserEntityDefinition<ZombieEntity> ZOMBIE;
    public static final GeyserEntityDefinition<ZombieHorseEntity> ZOMBIE_HORSE;
    public static final GeyserEntityDefinition<ZombieVillagerEntity> ZOMBIE_VILLAGER;
    public static final GeyserEntityDefinition<ZombifiedPiglinEntity> ZOMBIFIED_PIGLIN;

    /**
     * Is not sent over the network
     */
    public static final GeyserEntityDefinition<EnderDragonPartEntity> ENDER_DRAGON_PART;
    /**
     * Special Bedrock type
     */
    public static final GeyserEntityDefinition<WitherSkullEntity> WITHER_SKULL_DANGEROUS;

    static {
        GeyserEntityDefinition<Entity> entityBase = GeyserEntityDefinition.builder(Entity::new)
                .addTranslator(MetadataType.BYTE, Entity::setFlags)
                .addTranslator(MetadataType.INT, Entity::setAir) // Air/bubbles
                .addTranslator(MetadataType.OPTIONAL_CHAT, Entity::setDisplayName)
                .addTranslator(MetadataType.BOOLEAN, Entity::setDisplayNameVisible)
                .addTranslator(MetadataType.BOOLEAN, Entity::setSilent)
                .addTranslator(MetadataType.BOOLEAN, Entity::setGravity)
                .addTranslator(MetadataType.POSE, (entity, entityMetadata) -> entity.setPose(entityMetadata.getValue()))
                .addTranslator(MetadataType.INT, Entity::setFreezing)
                .build();

        // Extends entity
        {
            AREA_EFFECT_CLOUD = GeyserEntityDefinition.inherited(AreaEffectCloudEntity::new, entityBase)
                    .type(EntityType.AREA_EFFECT_CLOUD)
                    .height(0.5f).width(1.0f)
                    .addTranslator(MetadataType.FLOAT, AreaEffectCloudEntity::setRadius)
                    .addTranslator(MetadataType.INT, (entity, entityMetadata) -> entity.getDirtyMetadata().put(EntityDataTypes.EFFECT_COLOR, entityMetadata.getValue()))
                    .addTranslator(null) // Waiting
                    .addTranslator(MetadataType.PARTICLE, AreaEffectCloudEntity::setParticle)
                    .build();
            BOAT = GeyserEntityDefinition.inherited(BoatEntity::new, entityBase)
                    .type(EntityType.BOAT)
                    .height(0.6f).width(1.6f)
                    .offset(0.35f)
                    .addTranslator(MetadataType.INT, (boatEntity, entityMetadata) -> boatEntity.getDirtyMetadata().put(EntityDataTypes.HURT_TICKS, entityMetadata.getValue())) // Time since last hit
                    .addTranslator(MetadataType.INT, (boatEntity, entityMetadata) -> boatEntity.getDirtyMetadata().put(EntityDataTypes.HURT_DIRECTION, entityMetadata.getValue())) // Rocking direction
                    .addTranslator(MetadataType.FLOAT, (boatEntity, entityMetadata) ->
                            // 'Health' in Bedrock, damage taken in Java - it makes motion in Bedrock
                            boatEntity.getDirtyMetadata().put(EntityDataTypes.STRUCTURAL_INTEGRITY, 40 - ((int) ((FloatEntityMetadata) entityMetadata).getPrimitiveValue())))
                    .addTranslator(MetadataType.INT, BoatEntity::setVariant)
                    .addTranslator(MetadataType.BOOLEAN, BoatEntity::setPaddlingLeft)
                    .addTranslator(MetadataType.BOOLEAN, BoatEntity::setPaddlingRight)
                    .addTranslator(MetadataType.INT, (boatEntity, entityMetadata) -> boatEntity.getDirtyMetadata().put(EntityDataTypes.BOAT_BUBBLE_TIME, entityMetadata.getValue())) // May not actually do anything
                    .build();
            CHEST_BOAT = GeyserEntityDefinition.inherited(ChestBoatEntity::new, BOAT)
                    .type(EntityType.CHEST_BOAT)
                    .build();
            DRAGON_FIREBALL = GeyserEntityDefinition.inherited(FireballEntity::new, entityBase)
                    .type(EntityType.DRAGON_FIREBALL)
                    .heightAndWidth(1.0f)
                    .build();
            END_CRYSTAL = GeyserEntityDefinition.inherited(EnderCrystalEntity::new, entityBase)
                    .type(EntityType.END_CRYSTAL)
                    .heightAndWidth(2.0f)
                    .identifier("minecraft:ender_crystal")
                    .addTranslator(MetadataType.OPTIONAL_POSITION, EnderCrystalEntity::setBlockTarget)
                    .addTranslator(MetadataType.BOOLEAN,
                            (enderCrystalEntity, entityMetadata) -> enderCrystalEntity.setFlag(EntityFlag.SHOW_BOTTOM, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue())) // There is a base located on the ender crystal
                    .build();
            EXPERIENCE_ORB = GeyserEntityDefinition.<ExpOrbEntity>inherited(null, entityBase)
                    .type(EntityType.EXPERIENCE_ORB)
                    .identifier("minecraft:xp_orb")
                    .build();
            EVOKER_FANGS = GeyserEntityDefinition.inherited(EvokerFangsEntity::new, entityBase)
                    .type(EntityType.EVOKER_FANGS)
                    .height(0.8f).width(0.5f)
                    .identifier("minecraft:evocation_fang")
                    .build();
            EYE_OF_ENDER = GeyserEntityDefinition.inherited(Entity::new, entityBase)
                    .type(EntityType.EYE_OF_ENDER)
                    .heightAndWidth(0.25f)
                    .identifier("minecraft:eye_of_ender_signal")
                    .addTranslator(null)  // Item
                    .build();
            FALLING_BLOCK = GeyserEntityDefinition.<FallingBlockEntity>inherited(null, entityBase)
                    .type(EntityType.FALLING_BLOCK)
                    .heightAndWidth(0.98f)
                    .addTranslator(null) // "start block position"
                    .build();
            FIREWORK_ROCKET = GeyserEntityDefinition.inherited(FireworkEntity::new, entityBase)
                    .type(EntityType.FIREWORK_ROCKET)
                    .heightAndWidth(0.25f)
                    .identifier("minecraft:fireworks_rocket")
                    .addTranslator(MetadataType.ITEM, FireworkEntity::setFireworkItem)
                    .addTranslator(MetadataType.OPTIONAL_VARINT, FireworkEntity::setPlayerGliding)
                    .addTranslator(null) // Shot at angle
                    .build();
            FISHING_BOBBER = GeyserEntityDefinition.<FishingHookEntity>inherited(null, entityBase)
                    .type(EntityType.FISHING_BOBBER)
                    .identifier("minecraft:fishing_hook")
                    .addTranslator(MetadataType.INT, FishingHookEntity::setHookedEntity)
                    .addTranslator(null) // Biting TODO check
                    .build();
            ITEM = GeyserEntityDefinition.inherited(ItemEntity::new, entityBase)
                    .type(EntityType.ITEM)
                    .heightAndWidth(0.25f)
                    .offset(0.125f)
                    .addTranslator(MetadataType.ITEM, ItemEntity::setItem)
                    .build();
            LEASH_KNOT = GeyserEntityDefinition.inherited(LeashKnotEntity::new, entityBase)
                    .type(EntityType.LEASH_KNOT)
                    .height(0.5f).width(0.375f)
                    .build();
            LIGHTNING_BOLT = GeyserEntityDefinition.inherited(LightningEntity::new, entityBase)
                    .type(EntityType.LIGHTNING_BOLT)
                    .build();
            LLAMA_SPIT = GeyserEntityDefinition.inherited(ThrowableEntity::new, entityBase)
                    .type(EntityType.LLAMA_SPIT)
                    .heightAndWidth(0.25f)
                    .build();
            PAINTING = GeyserEntityDefinition.<PaintingEntity>inherited(null, entityBase)
                    .type(EntityType.PAINTING)
                    .addTranslator(MetadataType.PAINTING_VARIANT, PaintingEntity::setPaintingType)
                    .build();
            SHULKER_BULLET = GeyserEntityDefinition.inherited(ThrowableEntity::new, entityBase)
                    .type(EntityType.SHULKER_BULLET)
                    .heightAndWidth(0.3125f)
                    .build();
            TNT = GeyserEntityDefinition.inherited(TNTEntity::new, entityBase)
                    .type(EntityType.TNT)
                    .heightAndWidth(0.98f)
                    .addTranslator(MetadataType.INT, TNTEntity::setFuseLength)
                    .build();

            GeyserEntityDefinition<Entity> displayBase = GeyserEntityDefinition.inherited(entityBase.factory(), entityBase)
                    .addTranslator(null) // Interpolation delay
                    .addTranslator(null) // Transformation interpolation duration
                    .addTranslator(null) // Position/Rotation interpolation duration
                    .addTranslator(null) // Translation
                    .addTranslator(null) // Scale
                    .addTranslator(null) // Left rotation
                    .addTranslator(null) // Right rotation
                    .addTranslator(null) // Billboard render constraints
                    .addTranslator(null) // Brightness override
                    .addTranslator(null) // View range
                    .addTranslator(null) // Shadow radius
                    .addTranslator(null) // Shadow strength
                    .addTranslator(null) // Width
                    .addTranslator(null) // Height
                    .addTranslator(null) // Glow color override
                    .build();
            TEXT_DISPLAY = GeyserEntityDefinition.inherited(TextDisplayEntity::new, displayBase)
                    .type(EntityType.TEXT_DISPLAY)
                    .identifier("minecraft:armor_stand")
                    .addTranslator(MetadataType.CHAT, TextDisplayEntity::setText)
                    .addTranslator(null) // Line width
                    .addTranslator(null) // Background color
                    .addTranslator(null) // Text opacity
                    .addTranslator(null) // Bit mask
                    .build();

            INTERACTION = GeyserEntityDefinition.inherited(InteractionEntity::new, entityBase)
                    .type(EntityType.INTERACTION)
                    .heightAndWidth(1.0f) // default size until server specifies otherwise
                    .identifier("minecraft:armor_stand")
                    .addTranslator(MetadataType.FLOAT, InteractionEntity::setWidth)
                    .addTranslator(MetadataType.FLOAT, InteractionEntity::setHeight)
                    .addTranslator(MetadataType.BOOLEAN, InteractionEntity::setResponse)
                    .build();

            GeyserEntityDefinition<FireballEntity> fireballBase = GeyserEntityDefinition.inherited(FireballEntity::new, entityBase)
                    .addTranslator(null) // Item
                    .build();
            FIREBALL = GeyserEntityDefinition.inherited(FireballEntity::new, fireballBase)
                    .type(EntityType.FIREBALL)
                    .heightAndWidth(1.0f)
                    .build();
            SMALL_FIREBALL = GeyserEntityDefinition.inherited(FireballEntity::new, fireballBase)
                    .type(EntityType.SMALL_FIREBALL)
                    .heightAndWidth(0.3125f)
                    .build();

            GeyserEntityDefinition<ThrowableItemEntity> throwableItemBase = GeyserEntityDefinition.inherited(ThrowableItemEntity::new, entityBase)
                    .addTranslator(MetadataType.ITEM, ThrowableItemEntity::setItem)
                    .build();
            EGG = GeyserEntityDefinition.inherited(ThrowableItemEntity::new, throwableItemBase)
                    .type(EntityType.EGG)
                    .heightAndWidth(0.25f)
                    .build();
            ENDER_PEARL = GeyserEntityDefinition.inherited(ThrowableItemEntity::new, throwableItemBase)
                    .type(EntityType.ENDER_PEARL)
                    .heightAndWidth(0.25f)
                    .build();
            EXPERIENCE_BOTTLE = GeyserEntityDefinition.inherited(ThrowableItemEntity::new, throwableItemBase)
                    .type(EntityType.EXPERIENCE_BOTTLE)
                    .heightAndWidth(0.25f)
                    .identifier("minecraft:xp_bottle")
                    .build();
            POTION = GeyserEntityDefinition.inherited(ThrownPotionEntity::new, throwableItemBase)
                    .type(EntityType.POTION)
                    .heightAndWidth(0.25f)
                    .identifier("minecraft:splash_potion")
                    .build();
            SNOWBALL = GeyserEntityDefinition.inherited(ThrowableItemEntity::new, throwableItemBase)
                    .type(EntityType.SNOWBALL)
                    .heightAndWidth(0.25f)
                    .build();

            GeyserEntityDefinition<AbstractArrowEntity> abstractArrowBase = GeyserEntityDefinition.inherited(AbstractArrowEntity::new, entityBase)
                    .addTranslator(MetadataType.BYTE, AbstractArrowEntity::setArrowFlags)
                    .addTranslator(null) // "Piercing level"
                    .build();
            ARROW = GeyserEntityDefinition.inherited(TippedArrowEntity::new, abstractArrowBase)
                    .type(EntityType.ARROW)
                    .heightAndWidth(0.25f)
                    .addTranslator(MetadataType.INT, TippedArrowEntity::setPotionEffectColor)
                    .build();
            SPECTRAL_ARROW = GeyserEntityDefinition.inherited(abstractArrowBase.factory(), abstractArrowBase)
                    .type(EntityType.SPECTRAL_ARROW)
                    .heightAndWidth(0.25f)
                    .identifier("minecraft:arrow")
                    .build();
            TRIDENT = GeyserEntityDefinition.inherited(TridentEntity::new, abstractArrowBase) // TODO remove class
                    .type(EntityType.TRIDENT)
                    .identifier("minecraft:thrown_trident")
                    .addTranslator(null) // Loyalty
                    .addTranslator(MetadataType.BOOLEAN, (tridentEntity, entityMetadata) -> tridentEntity.setFlag(EntityFlag.ENCHANTED, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .build();

            // Item frames are handled differently as they are blocks, not items, in Bedrock
            ITEM_FRAME = GeyserEntityDefinition.<ItemFrameEntity>inherited(null, entityBase)
                    .type(EntityType.ITEM_FRAME)
                    .addTranslator(MetadataType.ITEM, ItemFrameEntity::setItemInFrame)
                    .addTranslator(MetadataType.INT, ItemFrameEntity::setItemRotation)
                    .build();
            GLOW_ITEM_FRAME = GeyserEntityDefinition.inherited(ITEM_FRAME.factory(), ITEM_FRAME)
                    .type(EntityType.GLOW_ITEM_FRAME)
                    .build();

            MINECART = GeyserEntityDefinition.inherited(MinecartEntity::new, entityBase)
                    .type(EntityType.MINECART)
                    .height(0.7f).width(0.98f)
                    .offset(0.35f)
                    .addTranslator(MetadataType.INT, (minecartEntity, entityMetadata) -> minecartEntity.getDirtyMetadata().put(EntityDataTypes.STRUCTURAL_INTEGRITY, entityMetadata.getValue()))
                    .addTranslator(MetadataType.INT, (minecartEntity, entityMetadata) -> minecartEntity.getDirtyMetadata().put(EntityDataTypes.HURT_DIRECTION, entityMetadata.getValue())) // Direction in which the minecart is shaking
                    .addTranslator(MetadataType.FLOAT, (minecartEntity, entityMetadata) ->
                            // Power in Java, hurt ticks in Bedrock
                            minecartEntity.getDirtyMetadata().put(EntityDataTypes.HURT_TICKS, Math.min((int) ((FloatEntityMetadata) entityMetadata).getPrimitiveValue(), 15)))
                    .addTranslator(MetadataType.INT, MinecartEntity::setCustomBlock)
                    .addTranslator(MetadataType.INT, MinecartEntity::setCustomBlockOffset)
                    .addTranslator(MetadataType.BOOLEAN, MinecartEntity::setShowCustomBlock)
                    .build();
            CHEST_MINECART = GeyserEntityDefinition.inherited(MINECART.factory(), MINECART)
                    .type(EntityType.CHEST_MINECART)
                    .build();
            COMMAND_BLOCK_MINECART = GeyserEntityDefinition.inherited(CommandBlockMinecartEntity::new, MINECART)
                    .type(EntityType.COMMAND_BLOCK_MINECART)
                    .addTranslator(MetadataType.STRING, (entity, entityMetadata) -> entity.getDirtyMetadata().put(EntityDataTypes.COMMAND_BLOCK_NAME, entityMetadata.getValue()))
                    .addTranslator(MetadataType.CHAT, (entity, entityMetadata) -> entity.getDirtyMetadata().put(EntityDataTypes.COMMAND_BLOCK_LAST_OUTPUT, MessageTranslator.convertMessage(entityMetadata.getValue())))
                    .build();
            FURNACE_MINECART = GeyserEntityDefinition.inherited(FurnaceMinecartEntity::new, MINECART)
                    .type(EntityType.FURNACE_MINECART)
                    .identifier("minecraft:minecart")
                    .addTranslator(MetadataType.BOOLEAN, FurnaceMinecartEntity::setHasFuel)
                    .build();
            HOPPER_MINECART = GeyserEntityDefinition.inherited(MINECART.factory(), MINECART)
                    .type(EntityType.HOPPER_MINECART)
                    .build();
            SPAWNER_MINECART = GeyserEntityDefinition.inherited(SpawnerMinecartEntity::new, MINECART)
                    .type(EntityType.SPAWNER_MINECART)
                    .identifier("minecraft:minecart")
                    .build();
            TNT_MINECART = GeyserEntityDefinition.inherited(MINECART.factory(), MINECART)
                    .type(EntityType.TNT_MINECART)
                    .build();

            WITHER_SKULL = GeyserEntityDefinition.inherited(WitherSkullEntity::new, entityBase)
                    .type(EntityType.WITHER_SKULL)
                    .heightAndWidth(0.3125f)
                    .addTranslator(MetadataType.BOOLEAN, WitherSkullEntity::setDangerous)
                    .build();
            WITHER_SKULL_DANGEROUS = GeyserEntityDefinition.inherited(WITHER_SKULL.factory(), WITHER_SKULL)
                    .build(false);
        }

        GeyserEntityDefinition<LivingEntity> livingEntityBase = GeyserEntityDefinition.inherited(LivingEntity::new, entityBase)
                .addTranslator(MetadataType.BYTE, LivingEntity::setLivingEntityFlags)
                .addTranslator(MetadataType.FLOAT, LivingEntity::setHealth)
                .addTranslator(MetadataType.INT,
                        (livingEntity, entityMetadata) -> livingEntity.getDirtyMetadata().put(EntityDataTypes.EFFECT_COLOR, entityMetadata.getValue()))
                .addTranslator(MetadataType.BOOLEAN,
                        (livingEntity, entityMetadata) -> livingEntity.getDirtyMetadata().put(EntityDataTypes.EFFECT_AMBIENCE, (byte) (((BooleanEntityMetadata) entityMetadata).getPrimitiveValue() ? 1 : 0)))
                .addTranslator(null) // Arrow count
                .addTranslator(null) // Stinger count
                .addTranslator(MetadataType.OPTIONAL_POSITION, LivingEntity::setBedPosition)
                .build();

        ARMOR_STAND = GeyserEntityDefinition.inherited(ArmorStandEntity::new, livingEntityBase)
                .type(EntityType.ARMOR_STAND)
                .height(1.975f).width(0.5f)
                .addTranslator(MetadataType.BYTE, ArmorStandEntity::setArmorStandFlags)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setHeadRotation)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setBodyRotation)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setLeftArmRotation)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setRightArmRotation)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setLeftLegRotation)
                .addTranslator(MetadataType.ROTATION, ArmorStandEntity::setRightLegRotation)
                .build();
        PLAYER = GeyserEntityDefinition.<PlayerEntity>inherited(null, livingEntityBase)
                .type(EntityType.PLAYER)
                .height(1.8f).width(0.6f)
                .offset(1.62f)
                .addTranslator(MetadataType.FLOAT, PlayerEntity::setAbsorptionHearts)
                .addTranslator(null) // Player score
                .addTranslator(MetadataType.BYTE, PlayerEntity::setSkinVisibility)
                .addTranslator(null) // Player main hand
                .addTranslator(MetadataType.NBT_TAG, PlayerEntity::setLeftParrot)
                .addTranslator(MetadataType.NBT_TAG, PlayerEntity::setRightParrot)
                .build();

        GeyserEntityDefinition<MobEntity> mobEntityBase = GeyserEntityDefinition.inherited(MobEntity::new, livingEntityBase)
                .addTranslator(MetadataType.BYTE, MobEntity::setMobFlags)
                .build();

        // Extends mob
        {
            ALLAY = GeyserEntityDefinition.inherited(AllayEntity::new, mobEntityBase)
                    .type(EntityType.ALLAY)
                    .height(0.6f).width(0.35f)
                    .addTranslator(MetadataType.BOOLEAN, AllayEntity::setDancing)
                    .addTranslator(MetadataType.BOOLEAN, AllayEntity::setCanDuplicate)
                    .build();
            BAT = GeyserEntityDefinition.inherited(BatEntity::new, mobEntityBase)
                    .type(EntityType.BAT)
                    .height(0.9f).width(0.5f)
                    .addTranslator(MetadataType.BYTE, BatEntity::setBatFlags)
                    .build();
            BLAZE = GeyserEntityDefinition.inherited(BlazeEntity::new, mobEntityBase)
                    .type(EntityType.BLAZE)
                    .height(1.8f).width(0.6f)
                    .addTranslator(MetadataType.BYTE, BlazeEntity::setBlazeFlags)
                    .build();
            CREEPER = GeyserEntityDefinition.inherited(CreeperEntity::new, mobEntityBase)
                    .type(EntityType.CREEPER)
                    .height(1.7f).width(0.6f)
                    .offset(1.62f)
                    .addTranslator(MetadataType.INT, CreeperEntity::setSwelling)
                    .addTranslator(MetadataType.BOOLEAN, (entity, entityMetadata) -> entity.setFlag(EntityFlag.POWERED, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .addTranslator(MetadataType.BOOLEAN, CreeperEntity::setIgnited)
                    .build();
            DOLPHIN = GeyserEntityDefinition.inherited(DolphinEntity::new, mobEntityBase)
                    .type(EntityType.DOLPHIN)
                    .height(0.6f).width(0.9f)
                    //TODO check
                    .addTranslator(null) // treasure position
                    .addTranslator(null) // "got fish"
                    .addTranslator(null) // "moistness level"
                    .build();
            ENDERMAN = GeyserEntityDefinition.inherited(EndermanEntity::new, mobEntityBase)
                    .type(EntityType.ENDERMAN)
                    .height(2.9f).width(0.6f)
                    .addTranslator(MetadataType.OPTIONAL_BLOCK_STATE, EndermanEntity::setCarriedBlock)
                    .addTranslator(MetadataType.BOOLEAN, EndermanEntity::setScreaming)
                    .addTranslator(MetadataType.BOOLEAN, EndermanEntity::setAngry)
                    .build();
            ENDERMITE = GeyserEntityDefinition.inherited(MonsterEntity::new, mobEntityBase)
                    .type(EntityType.ENDERMITE)
                    .height(0.3f).width(0.4f)
                    .build();
            ENDER_DRAGON = GeyserEntityDefinition.inherited(EnderDragonEntity::new, mobEntityBase)
                    .type(EntityType.ENDER_DRAGON)
                    .addTranslator(MetadataType.INT, EnderDragonEntity::setPhase)
                    .build();
            GHAST = GeyserEntityDefinition.inherited(GhastEntity::new, mobEntityBase)
                    .type(EntityType.GHAST)
                    .heightAndWidth(4.0f)
                    .addTranslator(MetadataType.BOOLEAN, GhastEntity::setGhastAttacking)
                    .build();
            GIANT = GeyserEntityDefinition.inherited(GiantEntity::new, mobEntityBase)
                    .type(EntityType.GIANT)
                    .height(1.8f).width(1.6f)
                    .offset(1.62f)
                    .identifier("minecraft:zombie")
                    .build();
            IRON_GOLEM = GeyserEntityDefinition.inherited(IronGolemEntity::new, mobEntityBase)
                    .type(EntityType.IRON_GOLEM)
                    .height(2.7f).width(1.4f)
                    .addTranslator(null) // "is player created", which doesn't seem to do anything clientside
                    .build();
            PHANTOM = GeyserEntityDefinition.inherited(PhantomEntity::new, mobEntityBase)
                    .type(EntityType.PHANTOM)
                    .height(0.5f).width(0.9f)
                    .offset(0.6f)
                    .addTranslator(MetadataType.INT, PhantomEntity::setPhantomScale)
                    .build();
            SILVERFISH = GeyserEntityDefinition.inherited(MonsterEntity::new, mobEntityBase)
                    .type(EntityType.SILVERFISH)
                    .height(0.3f).width(0.4f)
                    .build();
            SHULKER = GeyserEntityDefinition.inherited(ShulkerEntity::new, mobEntityBase)
                    .type(EntityType.SHULKER)
                    .heightAndWidth(1f)
                    .addTranslator(MetadataType.DIRECTION, ShulkerEntity::setAttachedFace)
                    .addTranslator(MetadataType.BYTE, ShulkerEntity::setShulkerHeight)
                    .addTranslator(MetadataType.BYTE, ShulkerEntity::setShulkerColor)
                    .build();
            SKELETON = GeyserEntityDefinition.inherited(SkeletonEntity::new, mobEntityBase)
                    .type(EntityType.SKELETON)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .addTranslator(MetadataType.BOOLEAN, SkeletonEntity::setConvertingToStray)
                    .build();
            SNOW_GOLEM = GeyserEntityDefinition.inherited(SnowGolemEntity::new, mobEntityBase)
                    .type(EntityType.SNOW_GOLEM)
                    .height(1.9f).width(0.7f)
                    .addTranslator(MetadataType.BYTE, SnowGolemEntity::setSnowGolemFlags)
                    .build();
            SPIDER = GeyserEntityDefinition.inherited(SpiderEntity::new, mobEntityBase)
                    .type(EntityType.SPIDER)
                    .height(0.9f).width(1.4f)
                    .offset(1f)
                    .addTranslator(MetadataType.BYTE, SpiderEntity::setSpiderFlags)
                    .build();
            CAVE_SPIDER = GeyserEntityDefinition.inherited(SpiderEntity::new, SPIDER)
                    .type(EntityType.CAVE_SPIDER)
                    .height(0.5f).width(0.7f)
                    .build();
            SQUID = GeyserEntityDefinition.inherited(SquidEntity::new, mobEntityBase)
                    .type(EntityType.SQUID)
                    .heightAndWidth(0.8f)
                    .build();
            STRAY = GeyserEntityDefinition.inherited(AbstractSkeletonEntity::new, mobEntityBase)
                    .type(EntityType.STRAY)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .build();
            VEX = GeyserEntityDefinition.inherited(VexEntity::new, mobEntityBase)
                    .type(EntityType.VEX)
                    .height(0.8f).width(0.4f)
                    .addTranslator(MetadataType.BYTE, VexEntity::setVexFlags)
                    .build();
            WARDEN = GeyserEntityDefinition.inherited(WardenEntity::new, mobEntityBase)
                    .type(EntityType.WARDEN)
                    .height(2.9f).width(0.9f)
                    .addTranslator(MetadataType.INT, WardenEntity::setAngerLevel)
                    .build();
            WITHER = GeyserEntityDefinition.inherited(WitherEntity::new, mobEntityBase)
                    .type(EntityType.WITHER)
                    .height(3.5f).width(0.9f)
                    .addTranslator(MetadataType.INT, WitherEntity::setTarget1)
                    .addTranslator(MetadataType.INT, WitherEntity::setTarget2)
                    .addTranslator(MetadataType.INT, WitherEntity::setTarget3)
                    .addTranslator(MetadataType.INT, WitherEntity::setInvulnerableTicks)
                    .build();
            WITHER_SKELETON = GeyserEntityDefinition.inherited(AbstractSkeletonEntity::new, mobEntityBase)
                    .type(EntityType.WITHER_SKELETON)
                    .height(2.4f).width(0.7f)
                    .build();
            ZOGLIN = GeyserEntityDefinition.inherited(ZoglinEntity::new, mobEntityBase)
                    .type(EntityType.ZOGLIN)
                    .height(1.4f).width(1.3965f)
                    .addTranslator(MetadataType.BOOLEAN, ZoglinEntity::setBaby)
                    .build();
            ZOMBIE = GeyserEntityDefinition.inherited(ZombieEntity::new, mobEntityBase)
                    .type(EntityType.ZOMBIE)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .addTranslator(MetadataType.BOOLEAN, ZombieEntity::setZombieBaby)
                    .addTranslator(null) // "set special type", doesn't do anything
                    .addTranslator(MetadataType.BOOLEAN, ZombieEntity::setConvertingToDrowned)
                    .build();
            ZOMBIE_VILLAGER = GeyserEntityDefinition.inherited(ZombieVillagerEntity::new, ZOMBIE)
                    .type(EntityType.ZOMBIE_VILLAGER)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .identifier("minecraft:zombie_villager_v2")
                    .addTranslator(MetadataType.BOOLEAN, ZombieVillagerEntity::setTransforming)
                    .addTranslator(MetadataType.VILLAGER_DATA, ZombieVillagerEntity::setZombieVillagerData)
                    .build();
            ZOMBIFIED_PIGLIN = GeyserEntityDefinition.inherited(ZombifiedPiglinEntity::new, ZOMBIE) //TODO test how zombie entity metadata is handled?
                    .type(EntityType.ZOMBIFIED_PIGLIN)
                    .height(1.95f).width(0.6f)
                    .offset(1.62f)
                    .identifier("minecraft:zombie_pigman")
                    .build();

            DROWNED = GeyserEntityDefinition.inherited(ZOMBIE.factory(), ZOMBIE)
                    .type(EntityType.DROWNED)
                    .height(1.95f).width(0.6f)
                    .build();
            HUSK = GeyserEntityDefinition.inherited(ZOMBIE.factory(), ZOMBIE)
                    .type(EntityType.HUSK)
                    .build();

            GUARDIAN = GeyserEntityDefinition.inherited(GuardianEntity::new, mobEntityBase)
                    .type(EntityType.GUARDIAN)
                    .heightAndWidth(0.85f)
                    .addTranslator(null) // Moving //TODO
                    .addTranslator(MetadataType.INT, GuardianEntity::setGuardianTarget)
                    .build();
            ELDER_GUARDIAN = GeyserEntityDefinition.inherited(ElderGuardianEntity::new, GUARDIAN)
                    .type(EntityType.ELDER_GUARDIAN)
                    .heightAndWidth(1.9975f)
                    .build();

            SLIME = GeyserEntityDefinition.inherited(SlimeEntity::new, mobEntityBase)
                    .type(EntityType.SLIME)
                    .heightAndWidth(0.51f)
                    .addTranslator(MetadataType.INT, SlimeEntity::setScale)
                    .build();
            MAGMA_CUBE = GeyserEntityDefinition.inherited(MagmaCubeEntity::new, SLIME)
                    .type(EntityType.MAGMA_CUBE)
                    .build();

            GeyserEntityDefinition<AbstractFishEntity> abstractFishEntityBase = GeyserEntityDefinition.inherited(AbstractFishEntity::new, mobEntityBase)
                    .addTranslator(null) // From bucket
                    .build();
            COD = GeyserEntityDefinition.inherited(abstractFishEntityBase.factory(), abstractFishEntityBase)
                    .type(EntityType.COD)
                    .height(0.25f).width(0.5f)
                    .build();
            PUFFERFISH = GeyserEntityDefinition.inherited(PufferFishEntity::new, abstractFishEntityBase)
                    .type(EntityType.PUFFERFISH)
                    .heightAndWidth(0.7f)
                    .addTranslator(MetadataType.INT, PufferFishEntity::setPufferfishSize)
                    .build();
            SALMON = GeyserEntityDefinition.inherited(abstractFishEntityBase.factory(), abstractFishEntityBase)
                    .type(EntityType.SALMON)
                    .height(0.5f).width(0.7f)
                    .build();
            TADPOLE = GeyserEntityDefinition.inherited(TadpoleEntity::new, abstractFishEntityBase)
                    .type(EntityType.TADPOLE)
                    .height(0.3f).width(0.4f)
                    .build();
            TROPICAL_FISH = GeyserEntityDefinition.inherited(TropicalFishEntity::new, abstractFishEntityBase)
                    .type(EntityType.TROPICAL_FISH)
                    .heightAndWidth(0.6f)
                    .identifier("minecraft:tropicalfish")
                    .addTranslator(MetadataType.INT, TropicalFishEntity::setFishVariant)
                    .build();

            GeyserEntityDefinition<BasePiglinEntity> abstractPiglinEntityBase = GeyserEntityDefinition.inherited(BasePiglinEntity::new, mobEntityBase)
                    .addTranslator(MetadataType.BOOLEAN, BasePiglinEntity::setImmuneToZombification)
                    .build();
            PIGLIN = GeyserEntityDefinition.inherited(PiglinEntity::new, abstractPiglinEntityBase)
                    .type(EntityType.PIGLIN)
                    .height(1.95f).width(0.6f)
                    .addTranslator(MetadataType.BOOLEAN, PiglinEntity::setBaby)
                    .addTranslator(MetadataType.BOOLEAN, PiglinEntity::setChargingCrossbow)
                    .addTranslator(MetadataType.BOOLEAN, PiglinEntity::setDancing)
                    .build();
            PIGLIN_BRUTE = GeyserEntityDefinition.inherited(abstractPiglinEntityBase.factory(), abstractPiglinEntityBase)
                    .type(EntityType.PIGLIN_BRUTE)
                    .height(1.95f).width(0.6f)
                    .build();

            GLOW_SQUID = GeyserEntityDefinition.inherited(GlowSquidEntity::new, SQUID)
                    .type(EntityType.GLOW_SQUID)
                    .addTranslator(null) // Set dark ticks remaining, possible TODO
                    .build();

            GeyserEntityDefinition<RaidParticipantEntity> raidParticipantEntityBase = GeyserEntityDefinition.inherited(RaidParticipantEntity::new, mobEntityBase)
                    .addTranslator(null) // Celebrating //TODO
                    .build();
            GeyserEntityDefinition<SpellcasterIllagerEntity> spellcasterEntityBase = GeyserEntityDefinition.inherited(SpellcasterIllagerEntity::new, raidParticipantEntityBase)
                    .addTranslator(MetadataType.BYTE, SpellcasterIllagerEntity::setSpellType)
                    .build();
            EVOKER = GeyserEntityDefinition.inherited(spellcasterEntityBase.factory(), spellcasterEntityBase)
                    .type(EntityType.EVOKER)
                    .height(1.95f).width(0.6f)
                    .identifier("minecraft:evocation_illager")
                    .build();
            ILLUSIONER = GeyserEntityDefinition.inherited(spellcasterEntityBase.factory(), spellcasterEntityBase)
                    .type(EntityType.ILLUSIONER)
                    .height(1.95f).width(0.6f)
                    .identifier("minecraft:evocation_illager")
                    .build();
            PILLAGER = GeyserEntityDefinition.inherited(PillagerEntity::new, raidParticipantEntityBase)
                    .type(EntityType.PILLAGER)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .addTranslator(null) // Charging; doesn't have an equivalent on Bedrock //TODO check
                    .build();
            RAVAGER = GeyserEntityDefinition.inherited(raidParticipantEntityBase.factory(), raidParticipantEntityBase)
                    .type(EntityType.RAVAGER)
                    .height(1.9f).width(1.2f)
                    .build();
            VINDICATOR = GeyserEntityDefinition.inherited(VindicatorEntity::new, raidParticipantEntityBase)
                    .type(EntityType.VINDICATOR)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .build();
            WITCH = GeyserEntityDefinition.inherited(raidParticipantEntityBase.factory(), raidParticipantEntityBase)
                    .type(EntityType.WITCH)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .addTranslator(null) // Using item
                    .build();
        }

        GeyserEntityDefinition<AgeableEntity> ageableEntityBase = GeyserEntityDefinition.inherited(AgeableEntity::new, mobEntityBase)
                .addTranslator(MetadataType.BOOLEAN, AgeableEntity::setBaby)
                .build();

        // Extends ageable
        {
            AXOLOTL = GeyserEntityDefinition.inherited(AxolotlEntity::new, ageableEntityBase)
                    .type(EntityType.AXOLOTL)
                    .height(0.42f).width(0.7f)
                    .addTranslator(MetadataType.INT, AxolotlEntity::setVariant)
                    .addTranslator(MetadataType.BOOLEAN, AxolotlEntity::setPlayingDead)
                    .addTranslator(null) // From bucket
                    .build();
            BEE = GeyserEntityDefinition.inherited(BeeEntity::new, ageableEntityBase)
                    .type(EntityType.BEE)
                    .heightAndWidth(0.6f)
                    .addTranslator(MetadataType.BYTE, BeeEntity::setBeeFlags)
                    .addTranslator(MetadataType.INT, BeeEntity::setAngerTime)
                    .build();
            CHICKEN = GeyserEntityDefinition.inherited(ChickenEntity::new, ageableEntityBase)
                    .type(EntityType.CHICKEN)
                    .height(0.7f).width(0.4f)
                    .build();
            COW = GeyserEntityDefinition.inherited(CowEntity::new, ageableEntityBase)
                    .type(EntityType.COW)
                    .height(1.4f).width(0.9f)
                    .build();
            FOX = GeyserEntityDefinition.inherited(FoxEntity::new, ageableEntityBase)
                    .type(EntityType.FOX)
                    .height(0.7f).width(0.6f)
                    .addTranslator(MetadataType.INT, FoxEntity::setFoxVariant)
                    .addTranslator(MetadataType.BYTE, FoxEntity::setFoxFlags)
                    .addTranslator(null) // Trusted player 1
                    .addTranslator(null) // Trusted player 2
                    .build();
            FROG = GeyserEntityDefinition.inherited(FrogEntity::new, ageableEntityBase)
                    .type(EntityType.FROG)
                    .heightAndWidth(0.5f)
                    .addTranslator(MetadataType.FROG_VARIANT, FrogEntity::setFrogVariant)
                    .addTranslator(MetadataType.OPTIONAL_VARINT, FrogEntity::setTongueTarget)
                    .build();
            HOGLIN = GeyserEntityDefinition.inherited(HoglinEntity::new, ageableEntityBase)
                    .type(EntityType.HOGLIN)
                    .height(1.4f).width(1.3965f)
                    .addTranslator(MetadataType.BOOLEAN, HoglinEntity::setImmuneToZombification)
                    .build();
            GOAT = GeyserEntityDefinition.inherited(GoatEntity::new, ageableEntityBase)
                    .type(EntityType.GOAT)
                    .height(1.3f).width(0.9f)
                    .addTranslator(MetadataType.BOOLEAN, GoatEntity::setScreamer)
                    .addTranslator(MetadataType.BOOLEAN, GoatEntity::setHasLeftHorn)
                    .addTranslator(MetadataType.BOOLEAN, GoatEntity::setHasRightHorn)
                    .build();
            MOOSHROOM = GeyserEntityDefinition.inherited(MooshroomEntity::new, ageableEntityBase)
                    .type(EntityType.MOOSHROOM)
                    .height(1.4f).width(0.9f)
                    .addTranslator(MetadataType.STRING, MooshroomEntity::setVariant)
                    .build();
            OCELOT = GeyserEntityDefinition.inherited(OcelotEntity::new, ageableEntityBase)
                    .type(EntityType.OCELOT)
                    .height(0.7f).width(0.6f)
                    .addTranslator(MetadataType.BOOLEAN, (ocelotEntity, entityMetadata) -> ocelotEntity.setFlag(EntityFlag.TRUSTING, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .build();
            PANDA = GeyserEntityDefinition.inherited(PandaEntity::new, ageableEntityBase)
                    .type(EntityType.PANDA)
                    .height(1.25f).width(1.125f)
                    .addTranslator(null) // Unhappy counter
                    .addTranslator(null) // Sneeze counter
                    .addTranslator(MetadataType.INT, PandaEntity::setEatingCounter)
                    .addTranslator(MetadataType.BYTE, PandaEntity::setMainGene)
                    .addTranslator(MetadataType.BYTE, PandaEntity::setHiddenGene)
                    .addTranslator(MetadataType.BYTE, PandaEntity::setPandaFlags)
                    .build();
            PIG = GeyserEntityDefinition.inherited(PigEntity::new, ageableEntityBase)
                    .type(EntityType.PIG)
                    .heightAndWidth(0.9f)
                    .addTranslator(MetadataType.BOOLEAN, (pigEntity, entityMetadata) -> pigEntity.setFlag(EntityFlag.SADDLED, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .addTranslator(null) // Boost time
                    .build();
            POLAR_BEAR = GeyserEntityDefinition.inherited(PolarBearEntity::new, ageableEntityBase)
                    .type(EntityType.POLAR_BEAR)
                    .height(1.4f).width(1.3f)
                    .addTranslator(MetadataType.BOOLEAN, (entity, entityMetadata) -> entity.setFlag(EntityFlag.STANDING, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .build();
            RABBIT = GeyserEntityDefinition.inherited(RabbitEntity::new, ageableEntityBase)
                    .type(EntityType.RABBIT)
                    .height(0.5f).width(0.4f)
                    .addTranslator(MetadataType.INT, RabbitEntity::setRabbitVariant)
                    .build();
            SHEEP = GeyserEntityDefinition.inherited(SheepEntity::new, ageableEntityBase)
                    .type(EntityType.SHEEP)
                    .height(1.3f).width(0.9f)
                    .addTranslator(MetadataType.BYTE, SheepEntity::setSheepFlags)
                    .build();
            SNIFFER = GeyserEntityDefinition.inherited(SnifferEntity::new, ageableEntityBase)
                    .type(EntityType.SNIFFER)
                    .height(1.75f).width(1.9f)
                    .addTranslator(MetadataType.SNIFFER_STATE, SnifferEntity::setSnifferState)
                    .addTranslator(null) // Integer, drop seed at tick
                    .build();
            STRIDER = GeyserEntityDefinition.inherited(StriderEntity::new, ageableEntityBase)
                    .type(EntityType.STRIDER)
                    .height(1.7f).width(0.9f)
                    .addTranslator(null) // Boost time
                    .addTranslator(MetadataType.BOOLEAN, StriderEntity::setCold)
                    .addTranslator(MetadataType.BOOLEAN, StriderEntity::setSaddled)
                    .build();
            TURTLE = GeyserEntityDefinition.inherited(TurtleEntity::new, ageableEntityBase)
                    .type(EntityType.TURTLE)
                    .height(0.4f).width(1.2f)
                    .addTranslator(null) // Home position
                    .addTranslator(MetadataType.BOOLEAN, TurtleEntity::setPregnant)
                    .addTranslator(MetadataType.BOOLEAN, TurtleEntity::setLayingEgg)
                    .addTranslator(null) // Travel position
                    .addTranslator(null) // Going home
                    .addTranslator(null) // Travelling
                    .build();

            GeyserEntityDefinition<AbstractMerchantEntity> abstractVillagerEntityBase = GeyserEntityDefinition.inherited(AbstractMerchantEntity::new, ageableEntityBase)
                    .addTranslator(null) // Unhappy ticks
                    .build();
            VILLAGER = GeyserEntityDefinition.inherited(VillagerEntity::new, abstractVillagerEntityBase)
                    .type(EntityType.VILLAGER)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .identifier("minecraft:villager_v2")
                    .addTranslator(MetadataType.VILLAGER_DATA, VillagerEntity::setVillagerData)
                    .build();
            WANDERING_TRADER = GeyserEntityDefinition.inherited(abstractVillagerEntityBase.factory(), abstractVillagerEntityBase)
                    .type(EntityType.WANDERING_TRADER)
                    .height(1.8f).width(0.6f)
                    .offset(1.62f)
                    .build();
        }

        // Horses
        {
            GeyserEntityDefinition<AbstractHorseEntity> abstractHorseEntityBase = GeyserEntityDefinition.inherited(AbstractHorseEntity::new, ageableEntityBase)
                    .addTranslator(MetadataType.BYTE, AbstractHorseEntity::setHorseFlags)
                    .build();
            CAMEL = GeyserEntityDefinition.inherited(CamelEntity::new, abstractHorseEntityBase)
                    .type(EntityType.CAMEL)
                    .height(2.375f).width(1.7f)
                    .addTranslator(MetadataType.BOOLEAN, CamelEntity::setDashing)
                    .addTranslator(null) // Last pose change tick
                    .build();
            HORSE = GeyserEntityDefinition.inherited(HorseEntity::new, abstractHorseEntityBase)
                    .type(EntityType.HORSE)
                    .height(1.6f).width(1.3965f)
                    .addTranslator(MetadataType.INT, HorseEntity::setHorseVariant)
                    .build();
            SKELETON_HORSE = GeyserEntityDefinition.inherited(SkeletonHorseEntity::new, abstractHorseEntityBase)
                    .type(EntityType.SKELETON_HORSE)
                    .height(1.6f).width(1.3965f)
                    .build();
            ZOMBIE_HORSE = GeyserEntityDefinition.inherited(ZombieHorseEntity::new, abstractHorseEntityBase)
                    .type(EntityType.ZOMBIE_HORSE)
                    .height(1.6f).width(1.3965f)
                    .build();
            GeyserEntityDefinition<ChestedHorseEntity> chestedHorseEntityBase = GeyserEntityDefinition.inherited(ChestedHorseEntity::new, abstractHorseEntityBase)
                    .addTranslator(MetadataType.BOOLEAN, (horseEntity, entityMetadata) -> horseEntity.setFlag(EntityFlag.CHESTED, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                    .build();
            DONKEY = GeyserEntityDefinition.inherited(chestedHorseEntityBase.factory(), chestedHorseEntityBase)
                    .type(EntityType.DONKEY)
                    .height(1.6f).width(1.3965f)
                    .build();
            MULE = GeyserEntityDefinition.inherited(chestedHorseEntityBase.factory(), chestedHorseEntityBase)
                    .type(EntityType.MULE)
                    .height(1.6f).width(1.3965f)
                    .build();
            LLAMA = GeyserEntityDefinition.inherited(LlamaEntity::new, chestedHorseEntityBase)
                    .type(EntityType.LLAMA)
                    .height(1.87f).width(0.9f)
                    .addTranslator(MetadataType.INT, (entity, entityMetadata) -> entity.getDirtyMetadata().put(EntityDataTypes.STRENGTH, entityMetadata.getValue()))
                    .addTranslator(MetadataType.INT, LlamaEntity::setCarpetedColor)
                    .addTranslator(MetadataType.INT, (entity, entityMetadata) -> entity.getDirtyMetadata().put(EntityDataTypes.VARIANT, entityMetadata.getValue()))
                    .build();
            TRADER_LLAMA = GeyserEntityDefinition.inherited(TraderLlamaEntity::new, LLAMA)
                    .type(EntityType.TRADER_LLAMA)
                    .identifier("minecraft:llama")
                    .build();
        }

        GeyserEntityDefinition<TameableEntity> tameableEntityBase = GeyserEntityDefinition.inherited(TameableEntity::new, ageableEntityBase)
                .addTranslator(MetadataType.BYTE, TameableEntity::setTameableFlags)
                .addTranslator(MetadataType.OPTIONAL_UUID, TameableEntity::setOwner)
                .build();
        CAT = GeyserEntityDefinition.inherited(CatEntity::new, tameableEntityBase)
                .type(EntityType.CAT)
                .height(0.35f).width(0.3f)
                .addTranslator(MetadataType.CAT_VARIANT, CatEntity::setCatVariant)
                .addTranslator(MetadataType.BOOLEAN, CatEntity::setResting)
                .addTranslator(null) // "resting state one" //TODO
                .addTranslator(MetadataType.INT, CatEntity::setCollarColor)
                .build();
        PARROT = GeyserEntityDefinition.inherited(ParrotEntity::new, tameableEntityBase)
                .type(EntityType.PARROT)
                .height(0.9f).width(0.5f)
                .addTranslator(MetadataType.INT, (parrotEntity, entityMetadata) -> parrotEntity.getDirtyMetadata().put(EntityDataTypes.VARIANT, entityMetadata.getValue())) // Parrot color
                .build();
        WOLF = GeyserEntityDefinition.inherited(WolfEntity::new, tameableEntityBase)
                .type(EntityType.WOLF)
                .height(0.85f).width(0.6f)
                // "Begging" on wiki.vg, "Interested" in Nukkit - the tilt of the head
                .addTranslator(MetadataType.BOOLEAN, (wolfEntity, entityMetadata) -> wolfEntity.setFlag(EntityFlag.INTERESTED, ((BooleanEntityMetadata) entityMetadata).getPrimitiveValue()))
                .addTranslator(MetadataType.INT, WolfEntity::setCollarColor)
                .addTranslator(MetadataType.INT, WolfEntity::setWolfAngerTime)
                .build();

        // As of 1.18 these don't track entity data at all
        ENDER_DRAGON_PART = GeyserEntityDefinition.<EnderDragonPartEntity>builder(null)
                .identifier("minecraft:armor_stand") // Emulated
                .build(false); // Never sent over the network

        Registries.ENTITY_IDENTIFIERS.get().put("minecraft:marker", null); // We don't need an entity definition for this as it is never sent over the network
    }

    public static void init() {
        // no-op
    }

    private EntityDefinitions() {
    }
}
