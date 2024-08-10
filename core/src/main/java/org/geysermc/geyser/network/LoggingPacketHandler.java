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

package org.geysermc.geyser.network;

import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.session.GeyserSession;

/**
 * Bare bones implementation of BedrockPacketHandler suitable for extension.
 * <p>
 * Logs and ignores all packets presented. Allows subclasses to override/implement only
 * packets of interest and limit boilerplate code.
 */
public class LoggingPacketHandler implements BedrockPacketHandler {
    protected final GeyserImpl geyser;
    protected final GeyserSession session;
    protected final PacketCooldownManager cooldownHandler;

    LoggingPacketHandler(GeyserImpl geyser, GeyserSession session) {
        this.geyser = geyser;
        this.session = session;
        this.cooldownHandler = new PacketCooldownManager(session);
    }

    PacketSignal defaultHandler(BedrockPacket packet) {
        this.cooldownHandler.handle(packet);
        geyser.getLogger().debug("Handled packet: " + packet.getClass().getSimpleName());
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnimatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockEntityDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockPickRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BookEditPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheBlobStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheMissResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientCacheStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientToServerHandshakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraPresetsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraInstructionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandBlockUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerClosePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CraftingEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EntityEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EntityPickRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InteractPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventoryContentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventorySlotPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(InventoryTransactionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemFrameDropItemPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LabTablePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LecternUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelEventGenericPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MapInfoRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobArmorEquipmentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobEquipmentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ModalFormResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MoveEntityAbsolutePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkStackLatencyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PhotoTransferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerActionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerHotbarPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerInputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerSkinPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PurchaseReceiptPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RequestChunkRadiusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RequestPermissionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackChunkRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RiderJumpPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerSettingsRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDefaultGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetPlayerGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SubChunkRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SubClientLoginPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TextPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddBehaviorTreePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddHangingEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddItemEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddPaintingPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AddPlayerPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AvailableCommandsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BlockEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BossEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ChangeDimensionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ChunkRadiusUpdatedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ClientboundMapItemDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CommandOutputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerOpenPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ContainerSetDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CraftingDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(DisconnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ExplodePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelChunkPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(GameRulesChangedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(GuiDataPickItemPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(HurtArmorPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AutomationClientConnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MapCreateLockedCopyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MobEffectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ModalFormRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MoveEntityDeltaPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NpcRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(OnScreenTextureAnimationPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlaySoundPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayStatusPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RemoveEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RemoveObjectivePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackChunkDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackDataInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePacksInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ResourcePackStackPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(RespawnPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerSettingsResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ServerToClientHandshakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetCommandsEnabledPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDifficultyPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetDisplayObjectivePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityDataPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityLinkPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetEntityMotionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetHealthPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetLastHurtByPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetPlayerInventoryOptionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetScoreboardIdentityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetScorePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetSpawnPositionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetTimePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SetTitlePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowCreditsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowProfilePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ShowStoreOfferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SimpleEventPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SpawnExperienceOrbPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StartGamePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StopSoundPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureBlockUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureTemplateDataRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(StructureTemplateDataResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TakeItemEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TransferPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateAttributesPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockPropertiesPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateBlockSyncedPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateEquipPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateSoftEnumPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdateTradePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AvailableEntityIdentifiersPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(BiomeDefinitionListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEvent2Packet packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkChunkPublisherUpdatePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SpawnParticleEffectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(VideoStreamConnectPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EmotePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TickSyncPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnvilDamagePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(NetworkSettingsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerAuthInputPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(SettingsCommandPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EducationSettingsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CompletedUsingItemPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(MultiplayerSettingsPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16 new packets

    @Override
    public PacketSignal handle(DebugInfoPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(EmoteListPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CodeBuilderPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CreativeContentPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemStackRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(LevelSoundEvent1Packet packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemStackResponsePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerArmorDamagePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerEnchantOptionsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(UpdatePlayerGameTypePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PacketViolationWarningPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PositionTrackingDBClientRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PositionTrackingDBServerBroadcastPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16.100 new packets

    @Override
    public PacketSignal handle(MotionPredictionHintsPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(AnimateEntityPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CameraShakePacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(PlayerFogPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(CorrectPlayerMovePredictionPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(ItemComponentPacket packet) {
        return defaultHandler(packet);
    }

    // 1.16.200 new packet

    @Override
    public PacketSignal handle(FilterTextPacket packet) {
        return defaultHandler(packet);
    }

    // 1.19.0 new packet

    @Override
    public PacketSignal handle(RequestAbilityPacket packet) {
        return defaultHandler(packet);
    }

    // 1.19.30 new packet

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        return defaultHandler(packet);
    }

    // todo: fix arrangement

    @Override
    public PacketSignal handle(ToggleCrafterSlotRequestPacket packet) {
        return defaultHandler(packet);
    }

    @Override
    public PacketSignal handle(TrimDataPacket packet) {
        return defaultHandler(packet);
    }
}