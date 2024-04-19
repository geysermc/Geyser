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

package org.geysermc.geyser.entity.type.living.merchant;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.VillagerData;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityDataTypes;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import org.geysermc.geyser.entity.GeyserEntityDefinition;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.type.BlockMapping;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Optional;
import java.util.UUID;

public class VillagerEntity extends AbstractMerchantEntity {
    /**
     * A map of Java profession IDs to Bedrock IDs
     */
    private static final int[] VILLAGER_PROFESSIONS = new int[15];
    /**
     * A map of all Java region IDs (plains, savanna...) to Bedrock
     */
    private static final int[] VILLAGER_REGIONS = new int[7];

    static {
        // Java villager profession IDs -> Bedrock
        VILLAGER_PROFESSIONS[0] = 0;
        VILLAGER_PROFESSIONS[1] = 8;
        VILLAGER_PROFESSIONS[2] = 11;
        VILLAGER_PROFESSIONS[3] = 6;
        VILLAGER_PROFESSIONS[4] = 7;
        VILLAGER_PROFESSIONS[5] = 1;
        VILLAGER_PROFESSIONS[6] = 2;
        VILLAGER_PROFESSIONS[7] = 4;
        VILLAGER_PROFESSIONS[8] = 12;
        VILLAGER_PROFESSIONS[9] = 5;
        VILLAGER_PROFESSIONS[10] = 13;
        VILLAGER_PROFESSIONS[11] = 14;
        VILLAGER_PROFESSIONS[12] = 3;
        VILLAGER_PROFESSIONS[13] = 10;
        VILLAGER_PROFESSIONS[14] = 9;

        VILLAGER_REGIONS[0] = 1;
        VILLAGER_REGIONS[1] = 2;
        VILLAGER_REGIONS[2] = 0;
        VILLAGER_REGIONS[3] = 3;
        VILLAGER_REGIONS[4] = 4;
        VILLAGER_REGIONS[5] = 5;
        VILLAGER_REGIONS[6] = 6;
    }

    @Nullable
    private Vector3i bedPosition;
    /**
     * Used in the interactive tag manager
     */
    @Getter
    private boolean canTradeWith;

    public VillagerEntity(GeyserSession session, int entityId, long geyserId, UUID uuid, GeyserEntityDefinition<?> definition, Vector3f position, Vector3f motion, float yaw, float pitch, float headYaw) {
        super(session, entityId, geyserId, uuid, definition, position, motion, yaw, pitch, headYaw);
    }

    public void setVillagerData(EntityMetadata<VillagerData, ?> entityMetadata) {
        VillagerData villagerData = entityMetadata.getValue();
        // Profession
        int profession = getBedrockProfession(villagerData.getProfession());
        canTradeWith = profession != 14 && profession != 0; // Not a notwit and not professionless
        dirtyMetadata.put(EntityDataTypes.VARIANT, profession);
        //metadata.put(EntityDataTypes.SKIN_ID, villagerData.getType()); Looks like this is modified but for any reason?
        // Region
        dirtyMetadata.put(EntityDataTypes.MARK_VARIANT, getBedrockRegion(villagerData.getType()));
        // Trade tier - different indexing in Bedrock
        dirtyMetadata.put(EntityDataTypes.TRADE_TIER, villagerData.getLevel() - 1);
    }

    @Override
    public @Nullable Vector3i setBedPosition(EntityMetadata<Optional<Vector3i>, ?> entityMetadata) {
        return bedPosition = super.setBedPosition(entityMetadata);
    }

    @Override
    public void moveRelative(double relX, double relY, double relZ, float yaw, float pitch, float headYaw, boolean isOnGround) {
        // The bed block position, if it exists
        if (!getFlag(EntityFlag.SLEEPING) || bedPosition == null) {
            // No need to worry about extra processing to compensate for sleeping
            super.moveRelative(relX, relY, relZ, yaw, pitch, headYaw, isOnGround);
            return;
        }
        
        // The bed block
        int blockId = session.getGeyser().getWorldManager().getBlockAt(session, bedPosition);
        String fullIdentifier = BlockRegistries.JAVA_BLOCKS.getOrDefault(blockId, BlockMapping.DEFAULT).getJavaIdentifier();

        // Set the correct position offset and rotation when sleeping
        int bedRotation = 0;
        float xOffset = 0;
        float zOffset = 0;
        if (fullIdentifier.contains("facing=south")) {
            // bed is facing south
            bedRotation = 180;
            zOffset = -.5f;
        } else if (fullIdentifier.contains("facing=east")) {
            // bed is facing east
            bedRotation = 90;
            xOffset = -.5f;
        } else if (fullIdentifier.contains("facing=west")) {
            // bed is facing west
            bedRotation = 270;
            xOffset = .5f;
        } else if (fullIdentifier.contains("facing=north")) {
            // rotation does not change because north is 0
            zOffset = .5f;
        }

        setYaw(yaw);
        setPitch(pitch);
        setHeadYaw(headYaw);
        setOnGround(isOnGround);
        this.position = Vector3f.from(position.getX() + relX, position.getY() + relY, position.getZ() + relZ);

        MoveEntityAbsolutePacket moveEntityPacket = new MoveEntityAbsolutePacket();
        moveEntityPacket.setRuntimeEntityId(geyserId);
        moveEntityPacket.setRotation(Vector3f.from(0, 0, bedRotation));
        moveEntityPacket.setPosition(Vector3f.from(position.getX() + xOffset, position.getY(), position.getZ() + zOffset));
        moveEntityPacket.setOnGround(isOnGround);
        moveEntityPacket.setTeleported(false);
        session.sendUpstreamPacket(moveEntityPacket);
    }

    public static int getBedrockProfession(int javaProfession) {
        return javaProfession >= 0 && javaProfession < VILLAGER_PROFESSIONS.length ? VILLAGER_PROFESSIONS[javaProfession] : 0;
    }

    public static int getBedrockRegion(int javaRegion) {
        return javaRegion >= 0 && javaRegion < VILLAGER_REGIONS.length ? VILLAGER_REGIONS[javaRegion] : 0;
    }
}
