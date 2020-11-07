/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.entity.living.merchant;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.VillagerData;
import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.entity.EntityData;
import com.nukkitx.protocol.bedrock.data.entity.EntityFlag;
import com.nukkitx.protocol.bedrock.packet.MoveEntityAbsolutePacket;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.geysermc.connector.entity.type.EntityType;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.world.block.BlockTranslator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VillagerEntity extends AbstractMerchantEntity {

    private static final Int2IntMap VILLAGER_VARIANTS = new Int2IntOpenHashMap();
    private static final Int2IntMap VILLAGER_REGIONS = new Int2IntOpenHashMap();

    static {
        // Java villager profession IDs -> Bedrock
        VILLAGER_VARIANTS.put(0, 0);
        VILLAGER_VARIANTS.put(1, 8);
        VILLAGER_VARIANTS.put(2, 11);
        VILLAGER_VARIANTS.put(3, 6);
        VILLAGER_VARIANTS.put(4, 7);
        VILLAGER_VARIANTS.put(5, 1);
        VILLAGER_VARIANTS.put(6, 2);
        VILLAGER_VARIANTS.put(7, 4);
        VILLAGER_VARIANTS.put(8, 12);
        VILLAGER_VARIANTS.put(9, 5);
        VILLAGER_VARIANTS.put(10, 13);
        VILLAGER_VARIANTS.put(11, 14);
        VILLAGER_VARIANTS.put(12, 3);
        VILLAGER_VARIANTS.put(13, 10);
        VILLAGER_VARIANTS.put(14, 9);

        VILLAGER_REGIONS.put(0, 1);
        VILLAGER_REGIONS.put(1, 2);
        VILLAGER_REGIONS.put(2, 0);
        VILLAGER_REGIONS.put(3, 3);
        VILLAGER_REGIONS.put(4, 4);
        VILLAGER_REGIONS.put(5, 5);
        VILLAGER_REGIONS.put(6, 6);
    }

    public VillagerEntity(long entityId, long geyserId, EntityType entityType, Vector3f position, Vector3f motion, Vector3f rotation) {
        super(entityId, geyserId, entityType, position, motion, rotation);
    }

    @Override
    public void updateBedrockMetadata(EntityMetadata entityMetadata, GeyserSession session) {
        if (entityMetadata.getId() == 17) {
            VillagerData villagerData = (VillagerData) entityMetadata.getValue();
            // Profession
            metadata.put(EntityData.VARIANT, VILLAGER_VARIANTS.get(villagerData.getProfession()));
            //metadata.put(EntityData.SKIN_ID, villagerData.getType()); Looks like this is modified but for any reason?
            // Region
            metadata.put(EntityData.MARK_VARIANT, VILLAGER_REGIONS.get(villagerData.getType()));
            // Trade tier - different indexing in Bedrock
            metadata.put(EntityData.TRADE_TIER, villagerData.getLevel() - 1);
        }
        super.updateBedrockMetadata(entityMetadata, session);
    }
    
    @Override
    public void moveRelative(GeyserSession session, double relX, double relY, double relZ, Vector3f rotation, boolean isOnGround) {
        if (!metadata.getFlags().getFlag(EntityFlag.SLEEPING)) {
            // No need to worry about extra processing to compensate for sleeping
            super.moveRelative(session, relX, relY, relZ, rotation, isOnGround);
            return;
        }
        int z = 0;
        int bedId = 0;
        float bedPositionSubtractorW = 0;
        float bedPositionSubtractorN = 0;
        if (session.getConnector().getConfig().isCacheChunks()) {
            Position bedLocation = new Position(position.getFloorX(), position.getFloorY(), position.getFloorZ());
            bedId = session.getConnector().getWorldManager().getBlockAt(session, bedLocation);
        }
        String bedRotationZ = BlockTranslator.getJavaIdBlockMap().inverse().get(bedId);
        setRotation(rotation);
        setOnGround(isOnGround);
        this.position = Vector3f.from(position.getX() + relX, position.getY() + relY, position.getZ() + relZ);

        MoveEntityAbsolutePacket moveEntityPacket = new MoveEntityAbsolutePacket();
        moveEntityPacket.setRuntimeEntityId(geyserId);
        // Sets Villager position and rotation when sleeping
        // String Setup
        Pattern r = Pattern.compile("facing=([a-z]+)");
        Matcher m = r.matcher(bedRotationZ);
        if (m.find()) {
            switch (m.group(0)) {
                case "facing=south":
                    //bed is facing south
                    z = 180;
                    bedPositionSubtractorW = -.5f;
                    break;
                case "facing=east":
                    //bed is facing east
                    z = 90;
                    bedPositionSubtractorW = -.5f;
                    break;
                case "facing=west":
                    //bed is facing west
                    z = 270;
                    bedPositionSubtractorW = .5f;
                    break;
                case "facing=north":
                    //rotation does not change because north is 0
                    bedPositionSubtractorN = .5f;
                    break;
            }
        }
        moveEntityPacket.setRotation(Vector3f.from(0, 0, z));
        moveEntityPacket.setPosition(Vector3f.from(position.getX() + bedPositionSubtractorW, position.getY(), position.getZ() + bedPositionSubtractorN));
        moveEntityPacket.setOnGround(isOnGround);
        moveEntityPacket.setTeleported(false);
        session.sendUpstreamPacket(moveEntityPacket);

        if (relX != 0 || relY != 0 || relZ != 0) {
            checkPlayerCollision(session);
        }
    }
}
