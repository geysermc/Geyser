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

package org.geysermc.geyser.entity.type.player;

import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityLinkData;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.AddPlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetEntityLinkPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.entity.type.living.animal.tameable.ParrotEntity;
import org.geysermc.geyser.scoreboard.Objective;
import org.geysermc.geyser.scoreboard.Score;
import org.geysermc.geyser.scoreboard.Team;
import org.geysermc.geyser.scoreboard.UpdateType;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.mcprotocollib.protocol.codec.NbtComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.chat.numbers.BlankFormat;
import org.geysermc.mcprotocollib.protocol.data.game.chat.numbers.FixedFormat;
import org.geysermc.mcprotocollib.protocol.data.game.chat.numbers.NumberFormat;
import org.geysermc.mcprotocollib.protocol.data.game.chat.numbers.StyledFormat;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.BooleanEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamColor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Getter @Setter
public class PlayerEntity extends LivingEntity implements GeyserPlayerEntity {
    public static final float SNEAKING_POSE_HEIGHT = 1.5f;
    protected static final List<AbilityLayer> BASE_ABILITY_LAYER;

    static {
        AbilityLayer abilityLayer = new AbilityLayer();
        abilityLayer.setLayerType(AbilityLayer.Type.BASE);
        Ability[] abilities = Ability.values();
        Collections.addAll(abilityLayer.getAbilitiesSet(), abilities); // Apparently all the abilities you're working with
        Collections.addAll(abilityLayer.getAbilityValues(), abilities); // Apparently all the abilities the player can work with
        BASE_ABILITY_LAYER = Collections.singletonList(abilityLayer);
    }

    private String username;

    /**
     * The textures property from the GameProfile.
     */
    @Nullable
    private String texturesProperty;

    @Nullable
    private Vector3i bedPosition;

    /**
     * Saves the parrot currently on the player's left shoulder; otherwise null
     */
    private ParrotEntity leftParrot;
    /**
     * Saves the parrot currently on the player's right shoulder; otherwise null
     */
    private ParrotEntity rightParrot;

    public PlayerEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, Vector3f position,
                        Vector3f motion, float yaw, float pitch, float headYaw, String username, @Nullable String texturesProperty) {
        super(session, entityId, geyserId, uuid, EntityDefinitions.PLAYER, position, motion, yaw, pitch, headYaw);

        this.username = username;
        this.nametag = username;
        this.texturesProperty = texturesProperty;
    }

    @Override
    protected void initializeMetadata() {
        super.initializeMetadata();
        // For the OptionalPack, set all bits as invisible by default as this matches Java Edition behavior
        dirtyMetadata.put(EntityDataTypes.MARK_VARIANT, 0xff);
    }

    @Override
    public void spawnEntity() {
        // Check to see if the player should have a belowname counterpart added
        Objective objective = session.getWorldCache().getScoreboard().getObjectiveSlots().get(ScoreboardPosition.BELOW_NAME);
        if (objective != null) {
            setBelowNameText(objective);
        }

        // Update in case this entity has been despawned, then respawned
        this.nametag = this.username;
        // The name can't be updated later (the entity metadata for it is ignored), so we need to check for this now
        updateDisplayName(session.getWorldCache().getScoreboard().getTeamFor(username));

        AddPlayerPacket addPlayerPacket = new AddPlayerPacket();
        addPlayerPacket.setUuid(uuid);
        addPlayerPacket.setUsername(username);
        addPlayerPacket.setRuntimeEntityId(geyserId);
        addPlayerPacket.setUniqueEntityId(geyserId);
        addPlayerPacket.setPosition(position.sub(0, definition.offset(), 0));
        addPlayerPacket.setRotation(getBedrockRotation());
        addPlayerPacket.setMotion(motion);
        addPlayerPacket.setHand(hand);
        addPlayerPacket.getAdventureSettings().setCommandPermission(CommandPermission.ANY);
        addPlayerPacket.getAdventureSettings().setPlayerPermission(PlayerPermission.MEMBER);
        addPlayerPacket.setDeviceId("");
        addPlayerPacket.setPlatformChatId("");
        addPlayerPacket.setGameType(GameType.SURVIVAL); //TODO
        addPlayerPacket.setAbilityLayers(BASE_ABILITY_LAYER); // Recommended to be added since 1.19.10, but only needed here for permissions viewing
        addPlayerPacket.getMetadata().putFlags(flags);

        // Since 1.20.60, the nametag does not show properly if this is not set :/
        // The nametag does disappear properly when the player is invisible though.
        dirtyMetadata.put(EntityDataTypes.NAMETAG_ALWAYS_SHOW, (byte) 1);
        dirtyMetadata.apply(addPlayerPacket.getMetadata());

        setFlagsDirty(false);

        valid = true;
        session.sendUpstreamPacket(addPlayerPacket);
    }

    @Override
    public void despawnEntity() {
        super.despawnEntity();

        // Since we re-use player entities: Clear flags, held item, etc
        this.resetMetadata();
        this.hand = ItemData.AIR;
        this.offhand = ItemData.AIR;
        this.boots = ItemData.AIR;
        this.leggings = ItemData.AIR;
        this.chestplate = ItemData.AIR;
        this.helmet = ItemData.AIR;
    }

    public void resetMetadata() {
        // Reset all metadata to their default values
        // This is used when a player respawns
        this.flags.clear();
        this.initializeMetadata();

        // Explicitly reset all metadata not handled by initializeMetadata
        setParrot(null, true);
        setParrot(null, false);
    }

    public void sendPlayer() {
        if (session.getEntityCache().getPlayerEntity(uuid) == null)
            return;

        if (session.getEntityCache().getEntityByGeyserId(geyserId) == null) {
            session.getEntityCache().spawnEntity(this);
        } else {
            spawnEntity();
        }
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        setPosition(position);
        setYaw(yaw);
        setPitch(pitch);
        setHeadYaw(headYaw);

        setOnGround(isOnGround);

        MovePlayerPacket movePlayerPacket = new MovePlayerPacket();
        movePlayerPacket.setRuntimeEntityId(geyserId);
        movePlayerPacket.setPosition(this.position);
        movePlayerPacket.setRotation(getBedrockRotation());
        movePlayerPacket.setOnGround(isOnGround);
        movePlayerPacket.setMode(teleported ? MovePlayerPacket.Mode.TELEPORT : MovePlayerPacket.Mode.NORMAL);

        if (teleported) {
            movePlayerPacket.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
        }

        session.sendUpstreamPacket(movePlayerPacket);

        if (teleported) {
            // As of 1.19.0, head yaw seems to be ignored during teleports.
            updateHeadLookRotation(headYaw);
        }

        if (leftParrot != null) {
            leftParrot.moveAbsolute(position, yaw, pitch, headYaw, true, teleported);
        }
        if (rightParrot != null) {
            rightParrot.moveAbsolute(position, yaw, pitch, headYaw, true, teleported);
        }
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        setYaw(yaw);
        setPitch(pitch);
        setHeadYaw(headYaw);
        this.position = Vector3f.from(position.getX() + relX, position.getY() + relY, position.getZ() + relZ);

        setOnGround(isOnGround);

        MovePlayerPacket movePlayerPacket = new MovePlayerPacket();
        movePlayerPacket.setRuntimeEntityId(geyserId);
        movePlayerPacket.setPosition(position);
        movePlayerPacket.setRotation(getBedrockRotation());
        movePlayerPacket.setOnGround(isOnGround);
        movePlayerPacket.setMode(MovePlayerPacket.Mode.NORMAL);
        // If the player is moved while sleeping, we have to adjust their y, so it appears
        // correctly on Bedrock. This fixes GSit's lay.
        if (getFlag(EntityFlag.SLEEPING)) {
            if (bedPosition != null && (bedPosition.getY() == 0 || bedPosition.distanceSquared(position.toInt()) > 4)) {
                // Force the player movement by using a teleport
                movePlayerPacket.setPosition(Vector3f.from(position.getX(), position.getY() - definition.offset() + 0.2f, position.getZ()));
                movePlayerPacket.setMode(MovePlayerPacket.Mode.TELEPORT);
                movePlayerPacket.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
            }
        }
        session.sendUpstreamPacket(movePlayerPacket);
        if (leftParrot != null) {
            leftParrot.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, true);
        }
        if (rightParrot != null) {
            rightParrot.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, true);
        }
    }

    public void updateRotation(float yaw, float pitch, float headYaw, boolean isOnGround) {
        moveRelative(0, 0, 0, yaw, pitch, headYaw, isOnGround);
    }

    @Override
    public void setPosition(Vector3f position) {
        if (this.bedPosition != null) {
            // As of Bedrock 1.21.22 and Fabric 1.21.1
            // Messes with Bedrock if we send this to the client itself, though.
            super.setPosition(position.up(0.2f));
        } else {
            super.setPosition(position.add(0, definition.offset(), 0));
        }
    }

    @Override
    public @Nullable Vector3i setBedPosition(EntityMetadata<Optional<Vector3i>, ?> entityMetadata) {
        bedPosition = super.setBedPosition(entityMetadata);
        if (bedPosition != null) {
            // Required to sync position of entity to bed
            // Fixes https://github.com/GeyserMC/Geyser/issues/3595 on vanilla 1.19.3 servers - did not happen on Paper
            this.setPosition(bedPosition.toFloat());

            // TODO evaluate if needed
            int bed = session.getGeyser().getWorldManager().getBlockAt(session, bedPosition);
            // Bed has to be updated, or else player is floating in the air
            ChunkUtils.updateBlock(session, bed, bedPosition);

            // Indicate that the player should enter the sleep cycle
            // Has to be a byte or it does not work
            // (Bed position is what actually triggers sleep - "pose" is only optional)
            dirtyMetadata.put(EntityDataTypes.PLAYER_FLAGS, (byte) 2);
        } else {
            // Player is no longer sleeping
            dirtyMetadata.put(EntityDataTypes.PLAYER_FLAGS, (byte) 0);
            return null;
        }
        return bedPosition;
    }

    public void setAbsorptionHearts(FloatEntityMetadata entityMetadata) {
        // Extra hearts - is not metadata but an attribute on Bedrock
        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(geyserId);
        // Setting to a higher maximum since plugins/datapacks can probably extend the Bedrock soft limit
        attributesPacket.setAttributes(Collections.singletonList(
                GeyserAttributeType.ABSORPTION.getAttribute(entityMetadata.getPrimitiveValue())));
        session.sendUpstreamPacket(attributesPacket);
    }

    public void setSkinVisibility(ByteEntityMetadata entityMetadata) {
        // OptionalPack usage for toggling skin bits
        // In Java Edition, a bit being set means that part should be enabled
        // However, to ensure that the pack still works on other servers, we invert the bit so all values by default
        // are true (0).
        dirtyMetadata.put(EntityDataTypes.MARK_VARIANT, ~entityMetadata.getPrimitiveValue() & 0xff);
    }

    public void setLeftParrot(EntityMetadata<NbtMap, ?> entityMetadata) {
        setParrot(entityMetadata.getValue(), true);
    }

    public void setRightParrot(EntityMetadata<NbtMap, ?> entityMetadata) {
        setParrot(entityMetadata.getValue(), false);
    }

    /**
     * Sets the parrot occupying the shoulder. Bedrock Edition requires a full entity whereas Java Edition just
     * spawns it from the NBT data provided
     */
    protected void setParrot(NbtMap tag, boolean isLeft) {
        if (tag != null && !tag.isEmpty()) {
            if ((isLeft && leftParrot != null) || (!isLeft && rightParrot != null)) {
                // No need to update a parrot's data when it already exists
                return;
            }
            // The parrot is a separate entity in Bedrock, but part of the player entity in Java //TODO is a UUID provided in NBT?
            ParrotEntity parrot = new ParrotEntity(session, 0, session.getEntityCache().getNextEntityId().incrementAndGet(),
                    null, EntityDefinitions.PARROT, position, motion, getYaw(), getPitch(), getHeadYaw());
            parrot.spawnEntity();
            parrot.getDirtyMetadata().put(EntityDataTypes.VARIANT, (Integer) tag.get("Variant"));
            // Different position whether the parrot is left or right
            float offset = isLeft ? 0.4f : -0.4f;
            parrot.getDirtyMetadata().put(EntityDataTypes.SEAT_OFFSET, Vector3f.from(offset, -0.22, -0.1));
            parrot.getDirtyMetadata().put(EntityDataTypes.SEAT_LOCK_RIDER_ROTATION, true);
            parrot.updateBedrockMetadata();
            SetEntityLinkPacket linkPacket = new SetEntityLinkPacket();
            EntityLinkData.Type type = isLeft ? EntityLinkData.Type.RIDER : EntityLinkData.Type.PASSENGER;
            linkPacket.setEntityLink(new EntityLinkData(geyserId, parrot.getGeyserId(), type, false, false));
            // Delay, or else spawned-in players won't get the link
            // TODO: Find a better solution.
            session.scheduleInEventLoop(() -> session.sendUpstreamPacket(linkPacket), 500, TimeUnit.MILLISECONDS);
            if (isLeft) {
                leftParrot = parrot;
            } else {
                rightParrot = parrot;
            }
        } else {
            Entity parrot = isLeft ? leftParrot : rightParrot;
            if (parrot != null) {
                parrot.despawnEntity();
                if (isLeft) {
                    leftParrot = null;
                } else {
                    rightParrot = null;
                }
            }
        }
    }

    @Override
    public void setDisplayName(EntityMetadata<Optional<Component>, ?> entityMetadata) {
        // Doesn't do anything for players
    }

    //todo this will become common entity logic once UUID support is implemented for them
    public void updateDisplayName(@Nullable Team team) {
        boolean needsUpdate;
        if (team != null) {
            String newDisplayName;
            if (team.isVisibleFor(session.getPlayerEntity().getUsername())) {
                TeamColor color = team.getColor();
                String chatColor = MessageTranslator.toChatColor(color);
                // We have to emulate what modern Java text already does for us and add the color to each section
                String prefix = team.getCurrentData().getPrefix();
                String suffix = team.getCurrentData().getSuffix();
                newDisplayName = chatColor + prefix + chatColor + this.username + chatColor + suffix;
            } else {
                // The name is not visible to the session player; clear name
                newDisplayName = "";
            }
            needsUpdate = !newDisplayName.equals(this.nametag);
            this.nametag = newDisplayName;
        } else {
            // The name has reset, if it was previously something else
            needsUpdate = !this.nametag.equals(this.username);
            this.nametag = this.username;
        }

        if (needsUpdate) {
            dirtyMetadata.put(EntityDataTypes.NAME, this.nametag);
        }
    }

    @Override
    public void setDisplayNameVisible(BooleanEntityMetadata entityMetadata) {
        // Doesn't do anything for players
    }

    @Override
    protected void setDimensions(Pose pose) {
        float height;
        float width;
        switch (pose) {
            case SNEAKING -> {
                height = SNEAKING_POSE_HEIGHT;
                width = definition.width();
            }
            case FALL_FLYING, SPIN_ATTACK, SWIMMING -> {
                height = 0.6f;
                width = definition.width();
            }
            case DYING -> {
                height = 0.2f;
                width = 0.2f;
            }
            default -> {
                super.setDimensions(pose);
                return;
            }
        }
        setBoundingBoxWidth(width);
        setBoundingBoxHeight(height);
    }

    public void setBelowNameText(Objective objective) {
        if (objective != null && objective.getUpdateType() != UpdateType.REMOVE) {
            Score score = objective.getScores().get(username);
            String numberString;
            NumberFormat numberFormat;
            int amount;
            if (score != null) {
                amount = score.getScore();
                numberFormat = score.getNumberFormat();
                if (numberFormat == null) {
                    numberFormat = objective.getNumberFormat();
                }
            } else {
                amount = 0;
                numberFormat = objective.getNumberFormat();
            }

            if (numberFormat instanceof BlankFormat) {
                numberString = "";
            } else if (numberFormat instanceof FixedFormat fixedFormat) {
                numberString = MessageTranslator.convertMessage(fixedFormat.getValue());
            } else if (numberFormat instanceof StyledFormat styledFormat) {
                NbtMapBuilder styledAmount = styledFormat.getStyle().toBuilder();
                styledAmount.putString("text", String.valueOf(amount));

                numberString = MessageTranslator.convertJsonMessage(
                        NbtComponentSerializer.tagComponentToJson(styledAmount.build()).toString(), session.locale());
            } else {
                numberString = String.valueOf(amount);
            }

            String displayString = numberString + " " + ChatColor.RESET + objective.getDisplayName();

            if (valid) {
                // Already spawned - we still need to run the rest of this code because the spawn packet will be
                // providing the information
                SetEntityDataPacket packet = new SetEntityDataPacket();
                packet.setRuntimeEntityId(geyserId);
                packet.getMetadata().put(EntityDataTypes.SCORE, displayString);
                session.sendUpstreamPacket(packet);
            } else {
                // Not spawned yet, store score value in dirtyMetadata to be picked up by #spawnEntity
                dirtyMetadata.put(EntityDataTypes.SCORE, displayString);
            }
        } else {
            if (valid) {
                SetEntityDataPacket packet = new SetEntityDataPacket();
                packet.setRuntimeEntityId(geyserId);
                packet.getMetadata().put(EntityDataTypes.SCORE, "");
                session.sendUpstreamPacket(packet);
            } else {
                // Not spawned yet, store score value in dirtyMetadata to be picked up by #spawnEntity
                dirtyMetadata.put(EntityDataTypes.SCORE, "");
            }
        }

    }

    /**
     * @return the UUID that should be used when dealing with Bedrock's tab list.
     */
    public UUID getTabListUuid() {
        return getUuid();
    }

    @Override
    public Vector3f position() {
        return this.position.clone();
    }
}
