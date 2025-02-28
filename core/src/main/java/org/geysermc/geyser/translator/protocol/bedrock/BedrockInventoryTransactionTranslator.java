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

package org.geysermc.geyser.translator.protocol.bedrock;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMaps;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryActionData;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventorySource;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.InventoryTransactionType;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.LegacySetItemSlotData;
import org.cloudburstmc.protocol.bedrock.packet.ContainerOpenPacket;
import org.cloudburstmc.protocol.bedrock.packet.InventoryTransactionPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateBlockPacket;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.inventory.click.Click;
import org.geysermc.geyser.inventory.item.GeyserInstrument;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.item.type.BoatItem;
import org.geysermc.geyser.item.type.Item;
import org.geysermc.geyser.item.type.SpawnEggItem;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.property.Properties;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.level.block.type.BlockState;
import org.geysermc.geyser.level.block.type.CauldronBlock;
import org.geysermc.geyser.level.block.type.SkullBlock;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.skin.FakeHeadProvider;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.item.ItemTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.BlockUtils;
import org.geysermc.geyser.util.CooldownUtils;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.geyser.util.InteractionResult;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.SoundUtils;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Instrument;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundContainerClickPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * BedrockInventoryTransactionTranslator handles most interactions between the client and the world,
 * or the client and their inventory.
 */
@Translator(packet = InventoryTransactionPacket.class)
public class BedrockInventoryTransactionTranslator extends PacketTranslator<InventoryTransactionPacket> {

    @Override
    public void translate(GeyserSession session, InventoryTransactionPacket packet) {
        if (packet.getTransactionType() == InventoryTransactionType.NORMAL && packet.getActions().size() == 3) {
            InventoryActionData containerAction = packet.getActions().get(0);
            if (containerAction.getSource().getType() == InventorySource.Type.CONTAINER &&
                    session.getPlayerInventory().getHeldItemSlot() == containerAction.getSlot() &&
                    containerAction.getFromItem().getDefinition() == session.getItemMappings().getStoredItems().writableBook().getBedrockDefinition()) {
                // Ignore InventoryTransactions related to editing books as that is handled in BedrockBookEditTranslator
                return;
            }
        }
        // Send book updates before opening inventories
        session.getBookEditCache().checkForSend();

        switch (packet.getTransactionType()) {
            case NORMAL:
                if (packet.getActions().size() == 2) {
                    InventoryActionData worldAction = packet.getActions().get(0);
                    InventoryActionData containerAction = packet.getActions().get(1);
                    if (worldAction.getSource().getType() == InventorySource.Type.WORLD_INTERACTION
                            && worldAction.getSource().getFlag() == InventorySource.Flag.DROP_ITEM) {
                        boolean dropAll = worldAction.getToItem().getCount() > 1;

                        if (session.getPlayerInventory().getHeldItemSlot() != containerAction.getSlot()) {
                            // Dropping an item that you don't have selected isn't supported in Java, but we can workaround it with an inventory hack
                            PlayerInventory inventory = session.getPlayerInventory();
                            int hotbarSlot = inventory.getOffsetForHotbar(containerAction.getSlot());
                            Click clickType = dropAll ? Click.DROP_ALL : Click.DROP_ONE;
                            Int2ObjectMap<ItemStack> changedItem;
                            if (dropAll) {
                                inventory.setItem(hotbarSlot, GeyserItemStack.EMPTY, session);
                                changedItem = Int2ObjectMaps.singleton(hotbarSlot, null);
                            } else {
                                GeyserItemStack itemStack = inventory.getItem(hotbarSlot);
                                if (itemStack.isEmpty()) {
                                    return;
                                }
                                itemStack.sub(1);
                                changedItem = Int2ObjectMaps.singleton(hotbarSlot, itemStack.getItemStack());
                            }
                            ServerboundContainerClickPacket dropPacket = new ServerboundContainerClickPacket(
                                    inventory.getJavaId(), inventory.getStateId(), hotbarSlot, clickType.actionType, clickType.action,
                                    inventory.getCursor().getItemStack(), changedItem);
                            session.sendDownstreamGamePacket(dropPacket);
                            return;
                        }
                        if (session.getPlayerInventory().getItemInHand().isEmpty()) {
                            return;
                        }

                        ServerboundPlayerActionPacket dropPacket = new ServerboundPlayerActionPacket(
                                dropAll ? PlayerAction.DROP_ITEM_STACK : PlayerAction.DROP_ITEM,
                                Vector3i.ZERO,
                                Direction.DOWN,
                                0
                        );
                        session.sendDownstreamGamePacket(dropPacket);

                        if (dropAll) {
                            session.getPlayerInventory().setItemInHand(GeyserItemStack.EMPTY);
                        } else {
                            session.getPlayerInventory().getItemInHand().sub(1);
                        }
                    }
                }
                break;
            case INVENTORY_MISMATCH:
                break;
            case ITEM_USE:
                switch (packet.getActionType()) {
                    case 0 -> {
                        final Vector3i packetBlockPosition = packet.getBlockPosition();
                        Vector3i blockPos = BlockUtils.getBlockPosition(packetBlockPosition, packet.getBlockFace());

                        if (session.getGeyser().config().disableBedrockScaffolding()) {
                            float yaw = session.getPlayerEntity().getYaw();
                            boolean isGodBridging = switch (packet.getBlockFace()) {
                                case 2 -> yaw <= -135f || yaw > 135f;
                                case 3 -> yaw <= 45f && yaw > -45f;
                                case 4 -> yaw > 45f && yaw <= 135f;
                                case 5 -> yaw <= -45f && yaw > -135f;
                                default -> false;
                            };
                            if (isGodBridging) {
                                restoreCorrectBlock(session, blockPos);
                                return;
                            }
                        }

                        // Check if this is a double placement due to an extended collision block
                        if (!session.getBlockMappings().getExtendedCollisionBoxes().isEmpty()) {
                            Vector3i belowBlockPos = null;
                            switch (packet.getBlockFace()) {
                                case 1 -> belowBlockPos = blockPos.add(0, -2, 0);
                                case 2 -> belowBlockPos = blockPos.add(0, -1, 1);
                                case 3 -> belowBlockPos = blockPos.add(0, -1, -1);
                                case 4 -> belowBlockPos = blockPos.add(1, -1, 0);
                                case 5 -> belowBlockPos = blockPos.add(-1, -1, 0);
                            }

                            if (belowBlockPos != null) {
                                int belowBlock = session.getGeyser().getWorldManager().getBlockAt(session, belowBlockPos);
                                BlockDefinition extendedCollisionDefinition = session.getBlockMappings().getExtendedCollisionBoxes().get(belowBlock);
                                if (extendedCollisionDefinition != null && (System.currentTimeMillis() - session.getLastInteractionTime()) < 200) {
                                    restoreCorrectBlock(session, blockPos);
                                    return;
                                }
                            }
                        }

                        // Check to make sure the client isn't spamming interaction
                        // Based on Nukkit 1.0, with changes to ensure holding down still works
                        boolean hasAlreadyClicked = System.currentTimeMillis() - session.getLastInteractionTime() < 110.0 &&
                                packetBlockPosition.distanceSquared(session.getLastInteractionBlockPosition()) < 0.00001;
                        session.setLastInteractionBlockPosition(packetBlockPosition);
                        session.setLastInteractionPlayerPosition(session.getPlayerEntity().getPosition());
                        if (hasAlreadyClicked) {
                            break;
                        } else {
                            // Only update the interaction time if it's valid - that way holding down still works.
                            session.setLastInteractionTime(System.currentTimeMillis());
                        }

                        if (isIncorrectHeldItem(session, packet)) {
                            restoreCorrectBlock(session, blockPos);
                            return;
                        }

                        // Bedrock sends block interact code for a Java entity so we send entity code back to Java
                        if (session.getBlockMappings().isItemFrame(packet.getBlockDefinition())) {
                            Entity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, packet.getBlockPosition());
                            if (itemFrameEntity != null) {
                                processEntityInteraction(session, packet, itemFrameEntity);
                                break;
                            }
                        }

                        /*
                        Checks to ensure that the range will be accepted by the server.
                        "Not in range" doesn't refer to how far a vanilla client goes (that's a whole other mess),
                        but how much a server will accept from the client maximum
                         */
                        // Blocks cannot be placed or destroyed outside of the world border
                        if (!session.getWorldBorder().isInsideBorderBoundaries()) {
                            restoreCorrectBlock(session, blockPos);
                            return;
                        }

                        // As of 1.21, Paper does not have any additional range checks that would inconvenience normal players.
                        Vector3f playerPosition = session.getPlayerEntity().getPosition();
                        playerPosition = playerPosition.down(EntityDefinitions.PLAYER.offset() - session.getEyeHeight());

                        if (!canInteractWithBlock(session, playerPosition, packetBlockPosition)) {
                            restoreCorrectBlock(session, blockPos);
                            return;
                        }

                        double clickPositionFullX = (double) packetBlockPosition.getX() + (double) packet.getClickPosition().getX();
                        double clickPositionFullY = (double) packetBlockPosition.getY() + (double) packet.getClickPosition().getY();
                        double clickPositionFullZ = (double) packetBlockPosition.getZ() + (double) packet.getClickPosition().getZ();

                        Vector3f blockCenter = Vector3f.from(packetBlockPosition.getX() + 0.5f, packetBlockPosition.getY() + 0.5f, packetBlockPosition.getZ() + 0.5f);

                        double clickDistanceX = clickPositionFullX - blockCenter.getX();
                        double clickDistanceY = clickPositionFullY - blockCenter.getY();
                        double clickDistanceZ = clickPositionFullZ - blockCenter.getZ();
                        if (!(Math.abs(clickDistanceX) < 1.0000001D && Math.abs(clickDistanceY) < 1.0000001D && Math.abs(clickDistanceZ) < 1.0000001D)) {
                            restoreCorrectBlock(session, blockPos);
                            return;
                        }

                        /*
                        Block place checks end - client is good to go
                         */

                        if (packet.getItemInHand() != null && session.getItemMappings().getMapping(packet.getItemInHand()).getJavaItem() instanceof SpawnEggItem) {
                            BlockState blockState = session.getGeyser().getWorldManager().blockAt(session, packet.getBlockPosition());
                            if (blockState.is(Blocks.WATER) && blockState.getValue(Properties.LEVEL) == 0) {
                                // Otherwise causes multiple mobs to spawn - just send a use item packet
                                useItem(session, packet, blockState.javaId());
                                break;
                            }
                        }

                        // Storing the block position allows inconsistencies in block place checking from post-1.19 - pre-1.20.5 to be resolved.
                        int sequence = session.getWorldCache().nextPredictionSequence();
                        session.getWorldCache().markPositionInSequence(blockPos);
                        ServerboundUseItemOnPacket blockPacket = new ServerboundUseItemOnPacket(
                                packet.getBlockPosition(),
                                Direction.VALUES[packet.getBlockFace()],
                                Hand.MAIN_HAND,
                                packet.getClickPosition().getX(), packet.getClickPosition().getY(), packet.getClickPosition().getZ(),
                                false,
                                false,
                                sequence);
                        session.sendDownstreamGamePacket(blockPacket);

                        Item item = session.getPlayerInventory().getItemInHand().asItem();
                        if (packet.getItemInHand() != null) {
                            ItemDefinition definition = packet.getItemInHand().getDefinition();
                            BlockState blockState = session.getGeyser().getWorldManager().blockAt(session, packet.getBlockPosition());
                            // Otherwise boats will not be able to be placed in survival and buckets, lily pads, frogspawn, and glass bottles won't work on mobile
                            if (item instanceof BoatItem || item == Items.LILY_PAD || item == Items.FROGSPAWN) {
                                useItem(session, packet, blockState.javaId());
                            } else if (item == Items.GLASS_BOTTLE) {
                                Block block = blockState.block();
                                if (!session.isSneaking() && block instanceof CauldronBlock && block != Blocks.WATER_CAULDRON) {
                                    // ServerboundUseItemPacket is not sent for water cauldrons and glass bottles
                                    return;
                                }
                                useItem(session, packet, blockState.javaId());
                            } else if (session.getItemMappings().getBuckets().contains(definition)) {
                                // Don't send ServerboundUseItemPacket for powder snow buckets
                                if (definition != session.getItemMappings().getStoredItems().powderSnowBucket().getBedrockDefinition()) {
                                    if (!session.isSneaking() && blockState.block() instanceof CauldronBlock) {
                                        // ServerboundUseItemPacket is not sent for cauldrons and buckets
                                        return;
                                    }
                                    session.setPlacedBucket(useItem(session, packet, blockState.javaId()));
                                } else {
                                    session.setPlacedBucket(true);
                                }
                            }
                        }

                        if (packet.getActions().isEmpty()) {
                            if (session.getOpPermissionLevel() >= 2 && session.getGameMode() == GameMode.CREATIVE) {
                                // Otherwise insufficient permissions
                                if (session.getBlockMappings().getJigsawStates().contains(packet.getBlockDefinition())) {
                                    ContainerOpenPacket openPacket = new ContainerOpenPacket();
                                    openPacket.setBlockPosition(packet.getBlockPosition());
                                    openPacket.setId((byte) 1);
                                    openPacket.setType(ContainerType.JIGSAW_EDITOR);
                                    openPacket.setUniqueEntityId(-1);
                                    session.sendUpstreamPacket(openPacket);
                                } else if (session.getBlockMappings().getStructureBlockStates().containsValue(packet.getBlockDefinition())) {
                                    ContainerOpenPacket openPacket = new ContainerOpenPacket();
                                    openPacket.setBlockPosition(packet.getBlockPosition());
                                    openPacket.setId((byte) 1);
                                    openPacket.setType(ContainerType.STRUCTURE_EDITOR);
                                    openPacket.setUniqueEntityId(-1);
                                    session.sendUpstreamPacket(openPacket);
                                }
                            }
                        }
                        if (item instanceof BlockItem blockItem) {
                            session.setLastBlockPlacePosition(blockPos);
                            session.setLastBlockPlaced(blockItem);
                        }
                        session.setInteracting(true);
                    }
                    case 1 -> {
                        if (isIncorrectHeldItem(session, packet)) {
                            InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, session.getPlayerInventory(), session.getPlayerInventory().getOffsetForHotbar(packet.getHotbarSlot()));
                            break;
                        }

                        // Handled when sneaking
                        if (session.getPlayerInventory().getItemInHand().asItem() == Items.SHIELD) {
                            break;
                        }

                        // Handled in ITEM_USE if the item is not milk
                        if (packet.getItemInHand() != null) {
                            if (session.getItemMappings().getBuckets().contains(packet.getItemInHand().getDefinition()) &&
                                    packet.getItemInHand().getDefinition() != session.getItemMappings().getStoredItems().milkBucket().getBedrockDefinition()) {
                                // Handled in case 0 if the item is not milk
                                break;
                            } else if (session.getItemMappings().getMapping(packet.getItemInHand()).getJavaItem() instanceof SpawnEggItem) {
                                // Handled in case 0
                                break;
                            } else if (packet.getItemInHand().getDefinition() == session.getItemMappings().getStoredItems().glassBottle().getBedrockDefinition()) {
                                // Handled in case 0
                                break;
                            } else if (packet.getItemInHand().getDefinition() == session.getItemMappings().getStoredItems().writtenBook().getBedrockDefinition()) {
                                session.setCurrentBook(packet.getItemInHand());
                            } else if (session.getPlayerInventory().getItemInHand().asItem() == Items.GOAT_HORN) {
                                // Temporary workaround while we don't have full item/block use tracking.
                                if (!session.getWorldCache().hasCooldown(session.getPlayerInventory().getItemInHand())) {
                                    Holder<Instrument> holder = session.getPlayerInventory()
                                        .getItemInHand()
                                        .getComponent(DataComponentTypes.INSTRUMENT);
                                    if (holder != null) {
                                        GeyserInstrument instrument = GeyserInstrument.fromHolder(session, holder);
                                        if (instrument.bedrockInstrument() != null) {
                                            // BDS uses a LevelSoundEvent2Packet, but that doesn't work here... (as of 1.21.20)
                                            LevelSoundEventPacket soundPacket = new LevelSoundEventPacket();
                                            soundPacket.setSound(SoundEvent.valueOf("GOAT_CALL_" + instrument.bedrockInstrument().ordinal()));
                                            soundPacket.setPosition(session.getPlayerEntity().getPosition());
                                            soundPacket.setIdentifier("minecraft:player");
                                            soundPacket.setExtraData(-1);
                                            session.sendUpstreamPacket(soundPacket);
                                        } else {
                                            PlaySoundPacket playSoundPacket = new PlaySoundPacket();
                                            playSoundPacket.setPosition(session.getPlayerEntity().position());
                                            playSoundPacket.setSound(SoundUtils.translatePlaySound(instrument.soundEvent()));
                                            playSoundPacket.setPitch(1.0F);
                                            playSoundPacket.setVolume(instrument.range() / 16.0F);
                                            session.sendUpstreamPacket(playSoundPacket);
                                        }
                                    }
                                }
                            }
                        }

                        session.useItem(Hand.MAIN_HAND);

                        session.getBundleCache().awaitRelease();

                        List<LegacySetItemSlotData> legacySlots = packet.getLegacySlots();
                        if (packet.getActions().size() == 1 && !legacySlots.isEmpty()) {
                            InventoryActionData actionData = packet.getActions().get(0);
                            LegacySetItemSlotData slotData = legacySlots.get(0);
                            if (slotData.getContainerId() == 6 && !actionData.getFromItem().isNull()) {
                                // The player is trying to swap out an armor piece that already has an item in it
                                // 1.19.4 brings this natively, but we need this specific case for custom head rendering to work
                                int bedrockHotbarSlot = packet.getHotbarSlot();
                                Click click = InventoryUtils.getClickForHotbarSwap(bedrockHotbarSlot);
                                if (click != null && slotData.getSlots().length != 0) {
                                    Inventory playerInventory = session.getPlayerInventory();
                                    // Bedrock sends us the index of the slot in the armor container; armor in Java
                                    // Edition is offset by 5 in the player inventory
                                    int armorSlot = slotData.getSlots()[0] + 5;
                                    if (armorSlot == 5) {
                                        GeyserItemStack armorSlotItem = playerInventory.getItem(armorSlot);
                                        if (armorSlotItem.asItem() == Items.PLAYER_HEAD) {
                                            FakeHeadProvider.restoreOriginalSkin(session, session.getPlayerEntity());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case ITEM_RELEASE:
                if (packet.getActionType() == 0) {
                    session.releaseItem();
                    session.getBundleCache().markRelease();
                }
                break;
            case ITEM_USE_ON_ENTITY:
                Entity entity = session.getEntityCache().getEntityByGeyserId(packet.getRuntimeEntityId());
                if (entity == null)
                    return;

                //https://wiki.vg/Protocol#Interact_Entity
                switch (packet.getActionType()) {
                    case 0 -> processEntityInteraction(session, packet, entity); // Interact
                    case 1 -> { // Attack
                        if (session.isHandsBusy()) {
                            // See Minecraft#startAttack and LocalPlayer#isHandsBusy
                            return;
                        }

                        int entityId;
                        if (entity.getDefinition() == EntityDefinitions.ENDER_DRAGON) {
                            // Redirects the attack to its body entity, this only happens when
                            // attacking the underbelly of the ender dragon
                            entityId = entity.getEntityId() + 3;
                        } else {
                            entityId = entity.getEntityId();
                        }
                        ServerboundInteractPacket attackPacket = new ServerboundInteractPacket(entityId,
                                InteractAction.ATTACK, session.isSneaking());
                        session.sendDownstreamGamePacket(attackPacket);

                        // Since 1.19.10, LevelSoundEventPackets are no longer sent by the client when attacking entities
                        CooldownUtils.sendCooldown(session);
                    }
                }
                break;
        }
    }

    private void processEntityInteraction(GeyserSession session, InventoryTransactionPacket packet, Entity entity) {
        Vector3f entityPosition = entity.getPosition();
        if (!session.getWorldBorder().isInsideBorderBoundaries(entityPosition)) {
            // No transaction is able to go through (as of Java Edition 1.18.1)
            return;
        }

        Vector3f clickPosition = packet.getClickPosition().sub(entityPosition);
        boolean isSpectator = session.getGameMode() == GameMode.SPECTATOR;
        for (Hand hand : EntityUtils.HANDS) {
            session.sendDownstreamGamePacket(new ServerboundInteractPacket(entity.getEntityId(),
                    InteractAction.INTERACT_AT, clickPosition.getX(), clickPosition.getY(), clickPosition.getZ(),
                    hand, session.isSneaking()));

            InteractionResult result;
            if (isSpectator) {
                result = InteractionResult.PASS;
            } else {
                result = entity.interactAt(hand);
            }

            if (!result.consumesAction()) {
                session.sendDownstreamGamePacket(new ServerboundInteractPacket(entity.getEntityId(),
                        InteractAction.INTERACT, hand, session.isSneaking()));
                if (!isSpectator) {
                    result = entity.interact(hand);
                }
            }

            if (result.consumesAction()) {
                if (result.shouldSwing() && hand == Hand.OFF_HAND) {
                    // Currently, Bedrock will send us the arm swing packet in most cases. But it won't for offhand.
                    session.sendDownstreamGamePacket(new ServerboundSwingPacket(hand));
                    // Note here to look into sending the animation packet back to Bedrock
                }
                return;
            }
        }
    }

    public static boolean canInteractWithBlock(GeyserSession session, Vector3f playerPosition, Vector3i packetBlockPosition) {
        // ViaVersion sends this 1.20.5+ attribute also, so older servers will have correct range checks.
        double blockInteractionRange = session.getPlayerEntity().getBlockInteractionRange();

        // Mojmap Player#canInteractWithBlock
        double additionalRangeCheck = blockInteractionRange + 1.0d;

        // AABB.<init>(BlockPos)
        float minX = packetBlockPosition.getX();
        float minY = packetBlockPosition.getY();
        float minZ = packetBlockPosition.getZ();
        float maxX = packetBlockPosition.getX() + 1;
        float maxY = packetBlockPosition.getY() + 1;
        float maxZ = packetBlockPosition.getZ() + 1;

        // AABB#distanceToSqr
        float diffX = Math.max(Math.max(minX - playerPosition.getX(), playerPosition.getX() - maxX), 0);
        float diffY = Math.max(Math.max(minY - playerPosition.getY(), playerPosition.getY() - maxY), 0);
        float diffZ = Math.max(Math.max(minZ - playerPosition.getZ(), playerPosition.getZ() - maxZ), 0);
        return ((diffX * diffX) + (diffY * diffY) + (diffZ * diffZ)) < (additionalRangeCheck * additionalRangeCheck);
    }

    /**
     * Restore the correct block state from the server without updating the chunk cache.
     *
     * @param session the session of the Bedrock client
     * @param blockPos the block position to restore
     */
    public static void restoreCorrectBlock(GeyserSession session, Vector3i blockPos) {
        BlockState javaBlockState = session.getGeyser().getWorldManager().blockAt(session, blockPos);
        BlockDefinition bedrockBlock = session.getBlockMappings().getBedrockBlock(javaBlockState);

        if (javaBlockState.block() instanceof SkullBlock skullBlock && skullBlock.skullType() == SkullBlock.Type.PLAYER) {
            // The changed block was a player skull so check if a custom block was defined for this skull
            SkullCache.Skull skull = session.getSkullCache().getSkulls().get(blockPos);
            if (skull != null && skull.getBlockDefinition() != null) {
                bedrockBlock = skull.getBlockDefinition();
            }
        }

        UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
        updateBlockPacket.setDataLayer(0);
        updateBlockPacket.setBlockPosition(blockPos);
        updateBlockPacket.setDefinition(bedrockBlock);
        updateBlockPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(updateBlockPacket);

        UpdateBlockPacket updateWaterPacket = new UpdateBlockPacket();
        updateWaterPacket.setDataLayer(1);
        updateWaterPacket.setBlockPosition(blockPos);
        updateWaterPacket.setDefinition(BlockRegistries.WATERLOGGED.get().get(javaBlockState.javaId()) ? session.getBlockMappings().getBedrockWater() : session.getBlockMappings().getBedrockAir());
        updateWaterPacket.getFlags().addAll(UpdateBlockPacket.FLAG_ALL_PRIORITY);
        session.sendUpstreamPacket(updateWaterPacket);

        // Reset the item in hand to prevent "missing" blocks
        InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, session.getPlayerInventory(), session.getPlayerInventory().getHeldItemSlot()); // TODO test
    }

    private boolean isIncorrectHeldItem(GeyserSession session, InventoryTransactionPacket packet) {
        int javaSlot = session.getPlayerInventory().getOffsetForHotbar(packet.getHotbarSlot());
        ItemDefinition expectedItem = ItemTranslator.getBedrockItemDefinition(session, session.getPlayerInventory().getItem(javaSlot));
        ItemDefinition heldItemId = packet.getItemInHand() == null ? ItemData.AIR.getDefinition() : packet.getItemInHand().getDefinition();

        if (!expectedItem.equals(heldItemId)) {
            session.getGeyser().getLogger().debug(session.bedrockUsername() + "'s held item has desynced! Expected: " + expectedItem + " Received: " + heldItemId);
            session.getGeyser().getLogger().debug("Packet: " + packet);
            return true;
        }

        return false;
    }

    private boolean useItem(GeyserSession session, InventoryTransactionPacket packet, int blockState) {
        // Update the player's inventory to remove any items added by the client itself
        Inventory playerInventory = session.getPlayerInventory();
        int heldItemSlot = playerInventory.getOffsetForHotbar(packet.getHotbarSlot());
        InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, playerInventory, heldItemSlot);
        GeyserItemStack itemStack = playerInventory.getItem(heldItemSlot);
        if (itemStack.getAmount() > 1) {
            if (itemStack.asItem() == Items.BUCKET || itemStack.asItem() == Items.GLASS_BOTTLE) {
                // Using a stack of buckets or glass bottles will result in an item being added to the first empty slot.
                // We need to revert the item in case the interaction fails. The order goes from left to right in the
                // hotbar. Then left to right and top to bottom in the inventory.
                for (int i = 0; i < 36; i++) {
                    int slot = i;
                    if (i < 9) {
                        slot = playerInventory.getOffsetForHotbar(slot);
                    }
                    if (playerInventory.getItem(slot).isEmpty()) {
                        InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR.updateSlot(session, playerInventory, slot);
                        break;
                    }
                }
            }
        }
        // Check if the player is interacting with a block
        if (!session.isSneaking()) {
            if (BlockRegistries.INTERACTIVE.get().get(blockState)) {
                return false;
            }

            boolean mayBuild = session.getGameMode() == GameMode.SURVIVAL || session.getGameMode() == GameMode.CREATIVE;
            if (mayBuild && BlockRegistries.INTERACTIVE_MAY_BUILD.get().get(blockState)) {
                return false;
            }
        }

        Vector3f target = packet.getBlockPosition().toFloat().add(packet.getClickPosition());
        lookAt(session, target);

        session.useItem(Hand.MAIN_HAND);
        return true;
    }

    /**
     * Determine the rotation necessary to activate this transaction.
     * <p>
     * The position between the intended click position and the player can be determined with two triangles.
     * First, we compute the difference of the X and Z coordinates:
     * <p>
     * Player position (0, 0)
     * |
     * |
     * |
     * |_____________ Intended target (-3, 2)
     * <p>
     * We then use the Pythagorean Theorem to find the direct line (hypotenuse) on the XZ plane. Finding the angle of the
     * triangle from there, closest to the player, gives us our yaw rotation value
     * Then doing the same using the new XZ distance and Y difference, we can find the direct line of sight from the
     * player to the intended target, and the pitch rotation value. We can then send the necessary packets to update
     * the player's rotation.
     *
     * @param session the Geyser Session
     * @param target the position to look at
     */
    private void lookAt(GeyserSession session, Vector3f target) {
        // Use the bounding box's position since we need the player's position seen by the Java server
        Vector3d playerPosition = session.getCollisionManager().getPlayerBoundingBox().getBottomCenter();
        float xDiff = (float) (target.getX() - playerPosition.getX());
        float yDiff = (float) (target.getY() - (playerPosition.getY() + session.getEyeHeight()));
        float zDiff = (float) (target.getZ() - playerPosition.getZ());

        // First triangle on the XZ plane
        float yaw = (float) -Math.toDegrees(Math.atan2(xDiff, zDiff));
        // Second triangle on the Y axis using the hypotenuse of the first triangle as a side
        double xzHypot = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, xzHypot));

        SessionPlayerEntity entity = session.getPlayerEntity();
        ServerboundMovePlayerPosRotPacket returnPacket = new ServerboundMovePlayerPosRotPacket(entity.isOnGround(), session.getInputCache().lastHorizontalCollision(),
            playerPosition.getX(), playerPosition.getY(), playerPosition.getZ(), entity.getYaw(), entity.getPitch());
        // This matches Java edition behavior
        ServerboundMovePlayerPosRotPacket movementPacket = new ServerboundMovePlayerPosRotPacket(entity.isOnGround(), session.getInputCache().lastHorizontalCollision(),
            playerPosition.getX(), playerPosition.getY(), playerPosition.getZ(), yaw, pitch);
        session.sendDownstreamGamePacket(movementPacket);

        if (session.getLookBackScheduledFuture() != null) {
            session.getLookBackScheduledFuture().cancel(false);
        }
        if (Math.abs(entity.getYaw() - yaw) > 1f || Math.abs(entity.getPitch() - pitch) > 1f) {
            session.setLookBackScheduledFuture(session.scheduleInEventLoop(() -> {
                Vector3d newPlayerPosition = session.getCollisionManager().getPlayerBoundingBox().getBottomCenter();
                if (!newPlayerPosition.equals(playerPosition) || entity.getYaw() != returnPacket.getYaw() || entity.getPitch() != returnPacket.getPitch()) {
                    // The player moved/rotated so there is no need to change their rotation back
                    return;
                }
                session.sendDownstreamGamePacket(returnPacket);
            }, 150, TimeUnit.MILLISECONDS));
        }
    }
}
