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

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.AttributeData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.AttributeUtils;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.Attribute;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.ByteEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.type.FloatEntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The entity class specifically for a {@link GeyserSession}'s player.
 */
public class SessionPlayerEntity extends PlayerEntity {
    /**
     * Used to fix some inconsistencies, especially in respawning.
     */
    @Getter
    protected final Map<GeyserAttributeType, AttributeData> attributes = new Object2ObjectOpenHashMap<>();
    /**
     * Java-only attribute
     */
    @Getter
    private double blockInteractionRange = GeyserAttributeType.BLOCK_INTERACTION_RANGE.getDefaultValue();
    /**
     * Used in PlayerInputTranslator for movement checks.
     */
    @Getter
    private boolean isRidingInFront;
    /**
     * Used when emulating client-side vehicles
     */
    @Getter
    private Vector2f vehicleInput = Vector2f.ZERO;
    /**
     * Used when emulating client-side vehicles
     */
    @Getter
    private int vehicleJumpStrength;

    private int lastAirSupply = getMaxAir();

    /**
     * Determines if our position is currently out-of-sync with the Java server
     * due to our workaround for the void floor
     * <p>
     * Must be reset when dying, switching worlds, or being teleported out of the void
     */
    @Getter @Setter
    private boolean voidPositionDesynched;

    public SessionPlayerEntity(GeyserSession session) {
        super(session, -1, 1, null, Vector3f.ZERO, Vector3f.ZERO, 0, 0, 0, null, null);

        valid = true;
    }

    @Override
    protected void setClientSideSilent() {
        // Do nothing, since we want the session player to hear their own footstep sounds for example.
    }

    @Override
    public void spawnEntity() {
        // Already logged in
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        if (voidPositionDesynched) {
            if (!isBelowVoidFloor()) {
                voidPositionDesynched = false; // No need to fix our offset; we've been moved
            }
        }
        super.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
        session.getCollisionManager().updatePlayerBoundingBox(this.position.down(definition.offset()));
    }

    @Override
    public void moveAbsolute(Vector3f position, float yaw, float pitch, float headYaw, boolean isOnGround, boolean teleported) {
        if (voidPositionDesynched) {
            if (!isBelowVoidFloor()) {
                voidPositionDesynched = false; // No need to fix our offset; we've been moved
            }
        }
        super.moveAbsolute(position, yaw, pitch, headYaw, isOnGround, teleported);
    }

    @Override
    public void setPosition(Vector3f position) {
        if (valid) { // Don't update during session init
            session.getCollisionManager().updatePlayerBoundingBox(position);
        }
        this.position = position.add(0, definition.offset(), 0);
    }

    /**
     * Set the player's position without applying an offset or moving the bounding box
     * This is used in BedrockMovePlayerTranslator which receives the player's position
     * with the offset pre-applied
     *
     * @param position the new position of the Bedrock player
     */
    public void setPositionManual(Vector3f position) {
        this.position = position;
    }

    /**
     * Sending any updated flags (sprinting, onFire, etc.) to the client while in spectator is not needed
     * Also "fixes" <a href="https://github.com/GeyserMC/Geyser/issues/3318">issue 3318</a>
     */
    @Override
    public void setFlags(ByteEntityMetadata entityMetadata) {
        // TODO: proper fix, BDS somehow does it? https://paste.gg/p/anonymous/3adfb7612f1540be80fa03a2281f93dc (BDS 1.20.13)
        if (!this.session.getGameMode().equals(GameMode.SPECTATOR)) {
            super.setFlags(entityMetadata);
        }
    }

    /**
     * Since 1.19.40, the client must be re-informed of its bounding box on respawn
     * See <a href="https://github.com/GeyserMC/Geyser/issues/3370">issue 3370</a>
     */
    public void updateBoundingBox() {
        dirtyMetadata.put(EntityDataTypes.HEIGHT, getBoundingBoxHeight());
        dirtyMetadata.put(EntityDataTypes.WIDTH, getBoundingBoxWidth());
        updateBedrockMetadata();
    }

    @Override
    public boolean setBoundingBoxHeight(float height) {
        if (super.setBoundingBoxHeight(height)) {
            if (valid) { // Don't update during session init
                session.getCollisionManager().updatePlayerBoundingBox();
            }
            return true;
        }
        return false;
    }

    @Override
    public void setPose(Pose pose) {
        super.setPose(pose);
        session.setPose(pose);
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    public float getHealth() {
        return this.health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    @Override
    protected void setAirSupply(int amount) {
        // Seemingly required to be sent as of Bedrock 1.21. Otherwise, bubbles will appear as empty
        // Also, this changes how the air bubble graphics/sounds are presented. Breathing on means sound effects and
        // the bubbles visually pop
        setFlag(EntityFlag.BREATHING, amount >= this.lastAirSupply);
        this.lastAirSupply = amount;

        if (amount == getMaxAir() && GameProtocol.isPre1_21_0(session)) {
            super.setAirSupply(0); // Hide the bubble counter from the UI for the player
        } else {
            super.setAirSupply(amount);
        }
    }

    @Override
    public void setRiderSeatPosition(Vector3f position) {
        super.setRiderSeatPosition(position);
        this.isRidingInFront = position != null && position.getX() > 0;
    }

    @Override
    public AttributeData createHealthAttribute() {
        // Max health must be divisible by two in bedrock
        if ((maxHealth % 2) == 1) {
            maxHealth += 1;
        }
        return super.createHealthAttribute();
    }

    @Override
    protected boolean hasShield(boolean offhand) {
        // Must be overridden to point to the player's inventory cache
        if (offhand) {
            return session.getPlayerInventory().getOffhand().asItem() == Items.SHIELD;
        } else {
            return session.getPlayerInventory().getItemInHand().asItem() == Items.SHIELD;
        }
    }

    @Override
    protected void updateAttribute(Attribute javaAttribute, List<AttributeData> newAttributes) {
        if (javaAttribute.getType() == AttributeType.Builtin.GENERIC_ATTACK_SPEED) {
            session.setAttackSpeed(AttributeUtils.calculateValue(javaAttribute));
        } else if (javaAttribute.getType() == AttributeType.Builtin.PLAYER_BLOCK_INTERACTION_RANGE) {
            this.blockInteractionRange = AttributeUtils.calculateValue(javaAttribute);
        } else {
            super.updateAttribute(javaAttribute, newAttributes);
        }
    }

    @Override
    protected AttributeData calculateAttribute(Attribute javaAttribute, GeyserAttributeType type) {
        AttributeData attributeData = super.calculateAttribute(javaAttribute, type);
        this.attributes.put(type, attributeData);
        return attributeData;
    }

    public void setLastDeathPosition(@Nullable GlobalPos pos) {
        if (pos != null) {
            dirtyMetadata.put(EntityDataTypes.PLAYER_LAST_DEATH_POS, pos.getPosition());
            dirtyMetadata.put(EntityDataTypes.PLAYER_LAST_DEATH_DIMENSION, DimensionUtils.javaToBedrock(pos.getDimension().asString()));
            dirtyMetadata.put(EntityDataTypes.PLAYER_HAS_DIED, true);
        } else {
            dirtyMetadata.put(EntityDataTypes.PLAYER_HAS_DIED, false);
        }

        // We're either respawning or switching worlds, either way, we are no longer desynched
        this.setVoidPositionDesynched(false);
    }

    @Override
    public UUID getTabListUuid() {
        return session.getAuthData().uuid();
    }

    @Override
    public void setAbsorptionHearts(FloatEntityMetadata entityMetadata) {
        // The bedrock client can glitch when sending a health and absorption attribute in the same tick
        // This can happen when switching servers. Resending the absorption attribute fixes the issue
        attributes.put(GeyserAttributeType.ABSORPTION, GeyserAttributeType.ABSORPTION.getAttribute(entityMetadata.getPrimitiveValue()));
        super.setAbsorptionHearts(entityMetadata);
    }

    @Override
    public void resetMetadata() {
        super.resetMetadata();

        // Reset air
        this.resetAir();

        // Absorption is metadata in java edition
        attributes.remove(GeyserAttributeType.ABSORPTION);
        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(geyserId);
        attributesPacket.setAttributes(Collections.singletonList(
                GeyserAttributeType.ABSORPTION.getAttribute(0f)));
        session.sendUpstreamPacket(attributesPacket);

        dirtyMetadata.put(EntityDataTypes.EFFECT_COLOR, 0);
        dirtyMetadata.put(EntityDataTypes.EFFECT_AMBIENCE, (byte) 0);
        dirtyMetadata.put(EntityDataTypes.FREEZING_EFFECT_STRENGTH, 0f);

        silent = false;
    }

    public void resetAttributes() {
        attributes.clear();
        maxHealth = GeyserAttributeType.MAX_HEALTH.getDefaultValue();
        blockInteractionRange = GeyserAttributeType.BLOCK_INTERACTION_RANGE.getDefaultValue();

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(geyserId);
        attributesPacket.setAttributes(Collections.singletonList(
                GeyserAttributeType.MOVEMENT_SPEED.getAttribute()));
        session.sendUpstreamPacket(attributesPacket);
    }

    public void resetAir() {
        this.setAirSupply(getMaxAir());
    }

    public void setVehicleInput(Vector2f vehicleInput) {
        this.vehicleInput = Vector2f.from(
                MathUtils.clamp(vehicleInput.getX(), -1.0f, 1.0f),
                MathUtils.clamp(vehicleInput.getY(), -1.0f, 1.0f)
        );
    }

    public void setVehicleJumpStrength(int vehicleJumpStrength) {
        this.vehicleJumpStrength = MathUtils.constrain(vehicleJumpStrength, 0, 100);
    }

    private boolean isBelowVoidFloor() {
        return position.getY() < voidFloorPosition();
    }

    public int voidFloorPosition() {
        // The void floor is offset about 40 blocks below the bottom of the world
        BedrockDimension bedrockDimension = session.getBedrockDimension();
        return bedrockDimension.minY() - 40;
    }

    /**
     * This method handles teleporting the player below or above the Bedrock void floor.
     * The Java server should never see this desync as we adjust the position that we send to it
     *
     * @param up in which direction to teleport - true to resync our position, or false to be
     *           teleported below the void floor.
     */
    public void teleportVoidFloorFix(boolean up) {
        // Safety to avoid double teleports
        if ((voidPositionDesynched && !up) || (!voidPositionDesynched && up)) {
            return;
        }

        // Work around there being a floor at the bottom of the world and teleport the player below it
        // Moving from below to above the void floor works fine
        Vector3f newPosition = this.getPosition();
        if (up) {
            newPosition = newPosition.up(4f);
            voidPositionDesynched = false;
        } else {
            newPosition = newPosition.down(4f);
            voidPositionDesynched = true;
        }

        this.setPositionManual(newPosition);
        MovePlayerPacket movePlayerPacket = new MovePlayerPacket();
        movePlayerPacket.setRuntimeEntityId(geyserId);
        movePlayerPacket.setPosition(newPosition);
        movePlayerPacket.setRotation(getBedrockRotation());
        movePlayerPacket.setMode(MovePlayerPacket.Mode.TELEPORT);
        movePlayerPacket.setTeleportationCause(MovePlayerPacket.TeleportationCause.BEHAVIOR);
        session.sendUpstreamPacketImmediately(movePlayerPacket);
    }
}
