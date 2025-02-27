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

package org.geysermc.geyser.translator.protocol.java.entity;

import org.cloudburstmc.protocol.bedrock.data.ParticleType;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerId;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEvent2Packet;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityMotionPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.EvokerFangsEntity;
import org.geysermc.geyser.entity.type.FishingHookEntity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.living.animal.ArmadilloEntity;
import org.geysermc.geyser.entity.type.living.monster.CreakingEntity;
import org.geysermc.geyser.entity.type.living.monster.WardenEntity;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundEntityEventPacket;

import java.util.Collections;
import java.util.concurrent.ThreadLocalRandom;

@Translator(packet = ClientboundEntityEventPacket.class)
public class JavaEntityEventTranslator extends PacketTranslator<ClientboundEntityEventPacket> {

    @Override
    public void translate(GeyserSession session, ClientboundEntityEventPacket packet) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());
        if (entity == null)
            return;

        EntityEventPacket entityEventPacket = new EntityEventPacket();
        entityEventPacket.setRuntimeEntityId(entity.getGeyserId());
        switch (packet.getEvent()) {
            case PLAYER_ENABLE_REDUCED_DEBUG:
                session.setReducedDebugInfo(true);
                return;
            case PLAYER_DISABLE_REDUCED_DEBUG:
                session.setReducedDebugInfo(false);
                return;
            case PLAYER_OP_PERMISSION_LEVEL_0:
                session.setOpPermissionLevel(0);
                session.sendAdventureSettings();
                return;
            case PLAYER_OP_PERMISSION_LEVEL_1:
                session.setOpPermissionLevel(1);
                session.sendAdventureSettings();
                return;
            case PLAYER_OP_PERMISSION_LEVEL_2:
                session.setOpPermissionLevel(2);
                session.sendAdventureSettings();
                return;
            case PLAYER_OP_PERMISSION_LEVEL_3:
                session.setOpPermissionLevel(3);
                session.sendAdventureSettings();
                return;
            case PLAYER_OP_PERMISSION_LEVEL_4:
                session.setOpPermissionLevel(4);
                session.sendAdventureSettings();
                return;

            case LIVING_DEATH:
                entityEventPacket.setType(EntityEventType.DEATH);
                if (entity.getDefinition() == EntityDefinitions.EGG) {
                    LevelEventPacket particlePacket = new LevelEventPacket();
                    particlePacket.setType(ParticleType.ICON_CRACK);
                    particlePacket.setData(session.getItemMappings().getStoredItems().egg().getBedrockDefinition().getRuntimeId() << 16);
                    particlePacket.setPosition(entity.getPosition());
                    for (int i = 0; i < 6; i++) {
                        session.sendUpstreamPacket(particlePacket);
                    }
                } else if (entity.getDefinition() == EntityDefinitions.SNOWBALL) {
                    LevelEventPacket particlePacket = new LevelEventPacket();
                    particlePacket.setType(ParticleType.SNOWBALL_POOF);
                    particlePacket.setPosition(entity.getPosition());
                    for (int i = 0; i < 8; i++) {
                        session.sendUpstreamPacket(particlePacket);
                    }
                }
                break;
            case WOLF_SHAKE_WATER:
                entityEventPacket.setType(EntityEventType.SHAKE_WETNESS);
                break;
            case PLAYER_FINISH_USING_ITEM:
                entityEventPacket.setType(EntityEventType.USE_ITEM);
                break;
            case FISHING_HOOK_PULL_PLAYER:
                // Player is pulled from a fishing rod
                // The physics of this are clientside on Java
                FishingHookEntity fishingHook = (FishingHookEntity) entity;
                if (fishingHook.getBedrockTargetId() == session.getPlayerEntity().getGeyserId()) {
                    Entity hookOwner = session.getEntityCache().getEntityByGeyserId(fishingHook.getBedrockOwnerId());
                    if (hookOwner != null) {
                        // https://minecraft.wiki/w/Fishing_Rod#Hooking_mobs_and_other_entities
                        SetEntityMotionPacket motionPacket = new SetEntityMotionPacket();
                        motionPacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());
                        motionPacket.setMotion(hookOwner.getPosition().sub(session.getPlayerEntity().getPosition()).mul(0.1f));
                        session.sendUpstreamPacket(motionPacket);
                    }
                }
                return;
            case TAMEABLE_TAMING_FAILED:
                entityEventPacket.setType(EntityEventType.TAME_FAILED);
                break;
            case TAMEABLE_TAMING_SUCCEEDED:
                entityEventPacket.setType(EntityEventType.TAME_SUCCEEDED);
                break;
            case ZOMBIE_VILLAGER_CURE: // Played when a zombie bites the golden apple
                LevelSoundEvent2Packet soundPacket = new LevelSoundEvent2Packet();
                soundPacket.setSound(SoundEvent.REMEDY);
                soundPacket.setPosition(entity.getPosition());
                soundPacket.setExtraData(-1);
                soundPacket.setIdentifier("");
                soundPacket.setRelativeVolumeDisabled(false);
                session.sendUpstreamPacket(soundPacket);
                return;
            case VILLAGER_MATE:
            case ANIMAL_EMIT_HEARTS:
                entityEventPacket.setType(EntityEventType.LOVE_PARTICLES);
                break;
            case FIREWORK_EXPLODE:
                entityEventPacket.setType(EntityEventType.FIREWORK_EXPLODE);
                break;
            case WITCH_EMIT_PARTICLES:
                entityEventPacket.setType(EntityEventType.WITCH_HAT_MAGIC); //TODO: CHECK
                break;
            case TOTEM_OF_UNDYING_MAKE_SOUND:
                // Bedrock will not play the spinning animation without the item in the hand o.o
                // Fixes https://github.com/GeyserMC/Geyser/issues/2446
                boolean totemItemWorkaround = !session.getPlayerInventory().eitherHandMatchesItem(Items.TOTEM_OF_UNDYING);
                if (totemItemWorkaround) {
                    InventoryContentPacket offhandPacket = new InventoryContentPacket();
                    offhandPacket.setContainerId(ContainerId.OFFHAND);
                    offhandPacket.setContents(Collections.singletonList(InventoryUtils.getTotemOfUndying().apply(session.getUpstream().getProtocolVersion())));
                    session.sendUpstreamPacket(offhandPacket);
                }

                entityEventPacket.setType(EntityEventType.CONSUME_TOTEM);

                PlaySoundPacket playSoundPacket = new PlaySoundPacket();
                playSoundPacket.setSound("random.totem");
                playSoundPacket.setPosition(entity.getPosition());
                playSoundPacket.setVolume(1.0F);
                playSoundPacket.setPitch(1.0F + (ThreadLocalRandom.current().nextFloat() * 0.1F) - 0.05F);
                session.sendUpstreamPacket(playSoundPacket);

                // Sent here early to ensure we have the totem in our hand
                session.sendUpstreamPacket(entityEventPacket);

                if (totemItemWorkaround) {
                    // Reset the item again
                    InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, session.getPlayerInventory(), 45);
                }

                return;
            case SHEEP_GRAZE_OR_TNT_CART_EXPLODE:
                if (entity.getDefinition() == EntityDefinitions.SHEEP) {
                    entityEventPacket.setType(EntityEventType.EAT_GRASS);
                } else {
                    entityEventPacket.setType(EntityEventType.PRIME_TNT_MINECART);
                }
                break;
            case IRON_GOLEM_HOLD_POPPY:
                entityEventPacket.setType(EntityEventType.GOLEM_FLOWER_OFFER);
                break;
            case VILLAGER_ANGRY:
                entityEventPacket.setType(EntityEventType.VILLAGER_ANGRY);
                break;
            case VILLAGER_HAPPY:
                entityEventPacket.setType(EntityEventType.VILLAGER_HAPPY);
                break;
            case VILLAGER_SWEAT:
                LevelEventPacket levelEventPacket = new LevelEventPacket();
                levelEventPacket.setType(ParticleType.WATER_SPLASH);
                levelEventPacket.setPosition(entity.getPosition().up(entity.getDefinition().height()));
                session.sendUpstreamPacket(levelEventPacket);
                return;
            case IRON_GOLEM_EMPTY_HAND:
                entityEventPacket.setType(EntityEventType.GOLEM_FLOWER_WITHDRAW);
                break;
            case ATTACK:
                if (entity.getDefinition() == EntityDefinitions.IRON_GOLEM || entity.getDefinition() == EntityDefinitions.EVOKER_FANGS
                        || entity.getDefinition() == EntityDefinitions.WARDEN) {
                    entityEventPacket.setType(EntityEventType.ATTACK_START);
                    if (entity.getDefinition() == EntityDefinitions.EVOKER_FANGS) {
                        ((EvokerFangsEntity) entity).setAttackStarted();
                    }
                }
                break;
            case RABBIT_JUMP_OR_MINECART_SPAWNER_DELAY_RESET:
                if (entity.getDefinition() == EntityDefinitions.RABBIT) {
                    // This doesn't match vanilla Bedrock behavior but I'm unsure how to make it better
                    // I assume part of the problem is that Bedrock uses a duration and Java just says the rabbit is jumping
                    SetEntityDataPacket dataPacket = new SetEntityDataPacket();
                    dataPacket.getMetadata().put(EntityDataTypes.JUMP_DURATION, (byte) 3);
                    dataPacket.setRuntimeEntityId(entity.getGeyserId());
                    session.sendUpstreamPacket(dataPacket);
                    return;
                }
                break;
            case LIVING_EQUIPMENT_BREAK_HEAD:
            case LIVING_EQUIPMENT_BREAK_CHEST:
            case LIVING_EQUIPMENT_BREAK_LEGS:
            case LIVING_EQUIPMENT_BREAK_FEET:
            case LIVING_EQUIPMENT_BREAK_MAIN_HAND:
            case LIVING_EQUIPMENT_BREAK_OFF_HAND:
                LevelSoundEvent2Packet equipmentBreakPacket = new LevelSoundEvent2Packet();
                equipmentBreakPacket.setSound(SoundEvent.BREAK);
                equipmentBreakPacket.setPosition(entity.getPosition());
                equipmentBreakPacket.setExtraData(-1);
                equipmentBreakPacket.setIdentifier("");
                session.sendUpstreamPacket(equipmentBreakPacket);
                return;
            case PLAYER_SWAP_SAME_ITEM: // Not just used for players
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.switchHands();

                    livingEntity.updateMainHand(session);
                    livingEntity.updateOffHand(session);
                } else {
                    GeyserLogger.get().debug("Got status message to swap hands for a non-living entity.");
                }
                return;
            case GOAT_LOWERING_HEAD:
                if (entity.getDefinition() == EntityDefinitions.GOAT) {
                    entityEventPacket.setType(EntityEventType.ATTACK_START);
                }
                break;
            case GOAT_STOP_LOWERING_HEAD:
                if (entity.getDefinition() == EntityDefinitions.GOAT) {
                    entityEventPacket.setType(EntityEventType.ATTACK_STOP);
                }
                break;
            case MAKE_POOF_PARTICLES:
                if (entity instanceof LivingEntity) {
                    // Note that this event usually makes noise, but because we set all entities as silent on the
                    // client end this isn't an issue.
                    entityEventPacket.setType(EntityEventType.DEATH_SMOKE_CLOUD);
                }
                break;
            case WARDEN_RECEIVE_SIGNAL:
                if (entity.getDefinition() == EntityDefinitions.WARDEN) {
                    entityEventPacket.setType(EntityEventType.VIBRATION_DETECTED);
                }
                break;
            case WARDEN_SONIC_BOOM:
                if (entity instanceof WardenEntity wardenEntity) {
                    wardenEntity.onSonicBoom();
                }
                break;
            case ARMADILLO_PEEKING:
                if (entity instanceof ArmadilloEntity armadilloEntity) {
                    armadilloEntity.onPeeking();
                }
                break;
            case SHAKE:
                if (entity instanceof CreakingEntity creakingEntity) {
                    creakingEntity.createParticleBeam();
                }
                break;
            case SQUID_RESET_ROTATION:
                // unused, but spams a bit
                break;
            default:
                GeyserLogger.get().debug("unhandled entity event: " + packet);
        }

        if (entityEventPacket.getType() != null) {
            session.sendUpstreamPacket(entityEventPacket);
        }
    }
}
