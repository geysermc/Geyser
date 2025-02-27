/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.translator.protocol.bedrock.entity.player.input;

import org.cloudburstmc.math.GenericMath;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerActionType;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.transaction.ItemUseTransaction;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerActionPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.type.BoatEntity;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.AbstractHorseEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.LlamaEntity;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.vehicle.ClientVehicle;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.protocol.bedrock.BedrockInventoryTransactionTranslator;
import org.geysermc.geyser.util.CooldownUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.InteractAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.level.ServerboundMoveVehiclePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundInteractPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerCommandPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;

import java.util.Set;

@Translator(packet = PlayerAuthInputPacket.class)
public final class BedrockPlayerAuthInputTranslator extends PacketTranslator<PlayerAuthInputPacket> {

    @Override
    public void translate(GeyserSession session, PlayerAuthInputPacket packet) {
        SessionPlayerEntity entity = session.getPlayerEntity();

        boolean wasJumping = session.getInputCache().wasJumping();
        session.getInputCache().processInputs(entity, packet);

        BedrockMovePlayer.translate(session, packet);

        processVehicleInput(session, packet, wasJumping);

        Set<PlayerAuthInputData> inputData = packet.getInputData();
        for (PlayerAuthInputData input : inputData) {
            switch (input) {
                case PERFORM_ITEM_INTERACTION -> processItemUseTransaction(session, packet.getItemUseTransaction());
                case PERFORM_BLOCK_ACTIONS -> BedrockBlockActions.translate(session, packet.getPlayerActions());
                case START_SPRINTING -> {
                    if (!entity.getFlag(EntityFlag.SWIMMING)) {
                        ServerboundPlayerCommandPacket startSprintPacket = new ServerboundPlayerCommandPacket(entity.getEntityId(), PlayerState.START_SPRINTING);
                        session.sendDownstreamGamePacket(startSprintPacket);
                        session.setSprinting(true);
                    }
                }
                case STOP_SPRINTING -> {
                    if (!entity.getFlag(EntityFlag.SWIMMING)) {
                        ServerboundPlayerCommandPacket stopSprintPacket = new ServerboundPlayerCommandPacket(entity.getEntityId(), PlayerState.STOP_SPRINTING);
                        session.sendDownstreamGamePacket(stopSprintPacket);
                    }
                    session.setSprinting(false);
                }
                case START_SWIMMING -> session.setSwimming(true);
                case STOP_SWIMMING -> session.setSwimming(false);
                case START_CRAWLING -> session.setCrawling(true);
                case STOP_CRAWLING -> session.setCrawling(false);
                case START_FLYING -> { // Since 1.20.30
                    if (session.isCanFly()) {
                        if (session.getGameMode() == GameMode.SPECTATOR) {
                            // should already be flying
                            session.sendAdventureSettings();
                            break;
                        }

                        if (session.getPlayerEntity().getFlag(EntityFlag.SWIMMING) && session.getCollisionManager().isPlayerInWater()) {
                            // As of 1.18.1, Java Edition cannot fly while in water, but it can fly while crawling
                            // If this isn't present, swimming on a 1.13.2 server and then attempting to fly will put you into a flying/swimming state that is invalid on JE
                            session.sendAdventureSettings();
                            break;
                        }

                        session.setFlying(true);
                        session.sendDownstreamGamePacket(new ServerboundPlayerAbilitiesPacket(true));
                    } else {
                        // update whether we can fly
                        session.sendAdventureSettings();
                        // stop flying
                        PlayerActionPacket stopFlyingPacket = new PlayerActionPacket();
                        stopFlyingPacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());
                        stopFlyingPacket.setAction(PlayerActionType.STOP_FLYING);
                        stopFlyingPacket.setBlockPosition(Vector3i.ZERO);
                        stopFlyingPacket.setResultPosition(Vector3i.ZERO);
                        stopFlyingPacket.setFace(0);
                        session.sendUpstreamPacket(stopFlyingPacket);
                    }
                }
                case STOP_FLYING -> {
                    session.setFlying(false);
                    session.sendDownstreamGamePacket(new ServerboundPlayerAbilitiesPacket(false));
                }
                case START_GLIDING -> {
                    // Otherwise gliding will not work in creative
                    ServerboundPlayerAbilitiesPacket playerAbilitiesPacket = new ServerboundPlayerAbilitiesPacket(false);
                    session.sendDownstreamGamePacket(playerAbilitiesPacket);
                    sendPlayerGlideToggle(session, entity);
                }
                case STOP_GLIDING -> sendPlayerGlideToggle(session, entity);
                case MISSED_SWING -> {
                    session.setLastAirHitTick(session.getTicks());

                    if (session.getArmAnimationTicks() != 0 && session.getArmAnimationTicks() != 1) {
                        session.sendDownstreamGamePacket(new ServerboundSwingPacket(Hand.MAIN_HAND));
                        session.activateArmAnimationTicking();
                    }

                    // Touch devices expect an animation packet sent back to them
                    if (packet.getInputMode().equals(InputMode.TOUCH)) {
                        AnimatePacket animatePacket = new AnimatePacket();
                        animatePacket.setAction(AnimatePacket.Action.SWING_ARM);
                        animatePacket.setRuntimeEntityId(session.getPlayerEntity().getGeyserId());
                        session.sendUpstreamPacket(animatePacket);
                    }

                    // Java edition sends a cooldown when hitting air.
                    CooldownUtils.sendCooldown(session);
                }
            }
        }
        if (entity.getVehicle() instanceof BoatEntity) {
            boolean up = inputData.contains(PlayerAuthInputData.UP);
            // Yes. These are flipped. It's always been an issue with Geyser. That's what it's like working with this codebase.
            // Hi random stranger. I am six days into updating for 1.21.3. How's it going?
            session.setSteeringLeft(up || inputData.contains(PlayerAuthInputData.PADDLE_RIGHT));
            session.setSteeringRight(up || inputData.contains(PlayerAuthInputData.PADDLE_LEFT));
        }
    }

    private static void sendPlayerGlideToggle(GeyserSession session, Entity entity) {
        ServerboundPlayerCommandPacket glidePacket = new ServerboundPlayerCommandPacket(entity.getEntityId(), PlayerState.START_ELYTRA_FLYING);
        session.sendDownstreamGamePacket(glidePacket);
    }

    private static void processItemUseTransaction(GeyserSession session, ItemUseTransaction transaction) {
        if (transaction.getActionType() == 2) {
            int blockState = session.getGameMode() == GameMode.CREATIVE ?
                session.getGeyser().getWorldManager().getBlockAt(session, transaction.getBlockPosition()) : session.getBreakingBlock();

            session.setLastBlockPlaced(null);
            session.setLastBlockPlacePosition(null);

            // Same deal with vanilla block placing as above.
            if (!session.getWorldBorder().isInsideBorderBoundaries()) {
                BedrockInventoryTransactionTranslator.restoreCorrectBlock(session, transaction.getBlockPosition());
                return;
            }

            Vector3f playerPosition = session.getPlayerEntity().getPosition();
            playerPosition = playerPosition.down(EntityDefinitions.PLAYER.offset() - session.getEyeHeight());

            if (!BedrockInventoryTransactionTranslator.canInteractWithBlock(session, playerPosition, transaction.getBlockPosition())) {
                BedrockInventoryTransactionTranslator.restoreCorrectBlock(session, transaction.getBlockPosition());
                return;
            }

            int sequence = session.getWorldCache().nextPredictionSequence();
            session.getWorldCache().markPositionInSequence(transaction.getBlockPosition());
            // -1 means we don't know what block they're breaking
            if (blockState == -1) {
                blockState = Block.JAVA_AIR_ID;
            }

            LevelEventPacket blockBreakPacket = new LevelEventPacket();
            blockBreakPacket.setType(LevelEvent.PARTICLE_DESTROY_BLOCK);
            blockBreakPacket.setPosition(transaction.getBlockPosition().toFloat());
            blockBreakPacket.setData(session.getBlockMappings().getBedrockBlockId(blockState));
            session.sendUpstreamPacket(blockBreakPacket);
            session.setBreakingBlock(-1);

            Entity itemFrameEntity = ItemFrameEntity.getItemFrameEntity(session, transaction.getBlockPosition());
            if (itemFrameEntity != null) {
                ServerboundInteractPacket attackPacket = new ServerboundInteractPacket(itemFrameEntity.getEntityId(),
                    InteractAction.ATTACK, session.isSneaking());
                session.sendDownstreamGamePacket(attackPacket);
                return;
            }

            PlayerAction action = session.getGameMode() == GameMode.CREATIVE ? PlayerAction.START_DIGGING : PlayerAction.FINISH_DIGGING;
            ServerboundPlayerActionPacket breakPacket = new ServerboundPlayerActionPacket(action, transaction.getBlockPosition(), Direction.VALUES[transaction.getBlockFace()], sequence);
            session.sendDownstreamGamePacket(breakPacket);
        } else {
            GeyserLogger.get().error("Unhandled item use transaction type!");
            if (GeyserLogger.get().isDebug()) {
                GeyserLogger.get().debug(transaction);
            }
        }
    }

    private static void processVehicleInput(GeyserSession session, PlayerAuthInputPacket packet, boolean wasJumping) {
        Entity vehicle = session.getPlayerEntity().getVehicle();
        if (vehicle == null) {
            return;
        }
        if (vehicle instanceof ClientVehicle) {
            session.getPlayerEntity().setVehicleInput(packet.getMotion());
        }

        boolean sendMovement = false;
        if (vehicle instanceof AbstractHorseEntity && !(vehicle instanceof LlamaEntity)) {
            sendMovement = !(vehicle instanceof ClientVehicle);
        } else if (vehicle instanceof BoatEntity) {
            if (vehicle.getPassengers().size() == 1) {
                // The player is the only rider
                sendMovement = true;
            } else {
                // Check if the player is the front rider
                if (session.getPlayerEntity().isRidingInFront()) {
                    sendMovement = true;
                }
            }
        }

        if (vehicle instanceof AbstractHorseEntity && !vehicle.getFlag(EntityFlag.HAS_DASH_COOLDOWN)) {
            // Behavior verified as of Java Edition 1.21.3
            int currentJumpingTicks = session.getInputCache().getJumpingTicks();
            if (currentJumpingTicks < 0) {
                session.getInputCache().setJumpingTicks(++currentJumpingTicks);
                if (currentJumpingTicks == 0) {
                    session.getInputCache().setJumpScale(0);
                }
            }

            boolean holdingJump = packet.getInputData().contains(PlayerAuthInputData.JUMPING);
            if (wasJumping && !holdingJump) {
                // Jump released
                // Yes, I'm fairly certain that entity ID is correct.
                int finalVehicleJumpStrength = GenericMath.floor(session.getInputCache().getJumpScale() * 100f);
                session.sendDownstreamGamePacket(new ServerboundPlayerCommandPacket(session.getPlayerEntity().getEntityId(),
                    PlayerState.START_HORSE_JUMP, finalVehicleJumpStrength));
                session.getInputCache().setJumpingTicks(-10);
                session.getPlayerEntity().setVehicleJumpStrength(finalVehicleJumpStrength);
            } else if (!wasJumping && holdingJump) {
                session.getInputCache().setJumpingTicks(0);
                session.getInputCache().setJumpScale(0);
            } else if (holdingJump) {
                session.getInputCache().setJumpingTicks(++currentJumpingTicks);
                if (currentJumpingTicks < 10) {
                    session.getInputCache().setJumpScale(session.getInputCache().getJumpingTicks() * 0.1F);
                } else {
                    session.getInputCache().setJumpScale(0.8f + 2.0f / (currentJumpingTicks - 9) * 0.1f);
                }
            }
        } else {
            session.getInputCache().setJumpScale(0);
        }

        if (sendMovement) {
            // Can't rely on y-delta being 0, as the value is -0.078 even when standing still
            vehicle.setOnGround(Math.abs(packet.getDelta().getY()) < 0.1);
            Vector3f vehiclePosition = packet.getPosition();
            Vector2f vehicleRotation = packet.getVehicleRotation();
            if (vehicleRotation == null) {
                return; // If the client just got in or out of a vehicle for example.
            }

            if (session.getWorldBorder().isPassingIntoBorderBoundaries(vehiclePosition, false)) {
                Vector3f position = vehicle.getPosition();
                if (vehicle instanceof BoatEntity boat) {
                    // Undo the changes usually applied to the boat
                    boat.moveAbsoluteWithoutAdjustments(position, vehicle.getYaw(), vehicle.isOnGround(), true);
                } else {
                    // This doesn't work if teleported is false
                    vehicle.moveAbsolute(position,
                        vehicle.getYaw(), vehicle.getPitch(), vehicle.getHeadYaw(),
                        vehicle.isOnGround(), true);
                }
                return;
            }

            if (vehicle instanceof BoatEntity && !vehicle.isOnGround()) {
                // Remove some Y position to prevents boats flying up
                vehiclePosition = vehiclePosition.down(vehicle.getDefinition().offset());
            }

            vehicle.setPosition(vehiclePosition);
            ServerboundMoveVehiclePacket moveVehiclePacket = new ServerboundMoveVehiclePacket(
                vehiclePosition.toDouble(),
                vehicleRotation.getY() - 90, vehiclePosition.getX(), // TODO I wonder if this is related to the horse spinning bugs...
                vehicle.isOnGround()
            );
            session.sendDownstreamGamePacket(moveVehiclePacket);
        }
    }
}
