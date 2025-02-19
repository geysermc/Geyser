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

package org.geysermc.geyser.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.raphimc.minecraftauth.responsehandler.exception.MinecraftRequestException;
import net.raphimc.minecraftauth.step.java.StepMCProfile;
import net.raphimc.minecraftauth.step.java.StepMCToken;
import net.raphimc.minecraftauth.step.java.session.StepFullJavaSession;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.value.qual.IntRange;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector2i;
import org.cloudburstmc.math.vector.Vector3d;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.math.vector.Vector3i;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.Ability;
import org.cloudburstmc.protocol.bedrock.data.AbilityLayer;
import org.cloudburstmc.protocol.bedrock.data.AuthoritativeMovementMode;
import org.cloudburstmc.protocol.bedrock.data.ChatRestrictionLevel;
import org.cloudburstmc.protocol.bedrock.data.ExperimentData;
import org.cloudburstmc.protocol.bedrock.data.GamePublishSetting;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.GameType;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.PlayerPermission;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.data.SpawnBiomeType;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType;
import org.cloudburstmc.protocol.bedrock.data.definitions.DimensionDefinition;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.data.inventory.crafting.recipe.CraftingRecipeData;
import org.cloudburstmc.protocol.bedrock.packet.AvailableEntityIdentifiersPacket;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.BiomeDefinitionListPacket;
import org.cloudburstmc.protocol.bedrock.packet.CameraPresetsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ChunkRadiusUpdatedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ClientboundCloseFormPacket;
import org.cloudburstmc.protocol.bedrock.packet.CreativeContentPacket;
import org.cloudburstmc.protocol.bedrock.packet.DimensionDataPacket;
import org.cloudburstmc.protocol.bedrock.packet.EmoteListPacket;
import org.cloudburstmc.protocol.bedrock.packet.GameRulesChangedPacket;
import org.cloudburstmc.protocol.bedrock.packet.ItemComponentPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEvent2Packet;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.SetTimePacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.cloudburstmc.protocol.bedrock.packet.SyncEntityPropertyPacket;
import org.cloudburstmc.protocol.bedrock.packet.TextPacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAbilitiesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAdventureSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateAttributesPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateClientInputLocksPacket;
import org.cloudburstmc.protocol.bedrock.packet.UpdateSoftEnumPacket;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.bedrock.camera.CameraData;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.EntityData;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.bedrock.SessionDisconnectEvent;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.EmoteOffhandWorkaroundOption;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.GeyserEntityData;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.entity.vehicle.ClientVehicle;
import org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler;
import org.geysermc.geyser.erosion.ErosionCancellationException;
import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.event.type.SessionDisconnectEventImpl;
import org.geysermc.geyser.impl.camera.CameraDefinitions;
import org.geysermc.geyser.impl.camera.GeyserCameraData;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserSmithingRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserStonecutterData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.item.type.BlockItem;
import org.geysermc.geyser.level.BedrockDimension;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.physics.CollisionManager;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.cache.AdvancementsCache;
import org.geysermc.geyser.session.cache.BookEditCache;
import org.geysermc.geyser.session.cache.BundleCache;
import org.geysermc.geyser.session.cache.ChunkCache;
import org.geysermc.geyser.session.cache.EntityCache;
import org.geysermc.geyser.session.cache.EntityEffectCache;
import org.geysermc.geyser.session.cache.FormCache;
import org.geysermc.geyser.session.cache.InputCache;
import org.geysermc.geyser.session.cache.LodestoneCache;
import org.geysermc.geyser.session.cache.PistonCache;
import org.geysermc.geyser.session.cache.PreferencesCache;
import org.geysermc.geyser.session.cache.RegistryCache;
import org.geysermc.geyser.session.cache.SkullCache;
import org.geysermc.geyser.session.cache.StructureBlockCache;
import org.geysermc.geyser.session.cache.TagCache;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.geysermc.geyser.session.cache.WorldBorder;
import org.geysermc.geyser.session.cache.WorldCache;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.geysermc.geyser.util.MathUtils;
import org.geysermc.geyser.util.MinecraftAuthLogger;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.network.BuiltinFlags;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.network.session.ClientNetworkSession;
import org.geysermc.mcprotocollib.protocol.ClientListener;
import org.geysermc.mcprotocollib.protocol.MinecraftConstants;
import org.geysermc.mcprotocollib.protocol.MinecraftProtocol;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.Pose;
import org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.HandPreference;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerAction;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ChatVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.setting.ParticleStatus;
import org.geysermc.mcprotocollib.protocol.data.game.setting.SkinPart;
import org.geysermc.mcprotocollib.protocol.data.game.statistic.CustomStatistic;
import org.geysermc.mcprotocollib.protocol.data.game.statistic.Statistic;
import org.geysermc.mcprotocollib.protocol.data.handshake.HandshakeIntent;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundClientInformationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientTickEndPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundCustomQueryAnswerPacket;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class GeyserSession implements GeyserConnection, GeyserCommandSource {

    private static final Gson GSON = new Gson();

    private final GeyserImpl geyser;
    private final UpstreamSession upstream;
    private DownstreamSession downstream;
    /**
     * The loop where all packets and ticking is processed to prevent concurrency issues.
     * If this is manually called, ensure that any exceptions are properly handled.
     */
    private final EventLoop tickEventLoop;
    @Setter
    private AuthData authData;
    private BedrockClientData clientData;
    /**
     * Used for Floodgate skin uploading
     */
    @Setter
    private List<String> certChainData;

    @NonNull
    @Setter
    private volatile AbstractGeyserboundPacketHandler erosionHandler;

    @Accessors(fluent = true)
    @Setter
    private RemoteServer remoteServer;

    private final SessionPlayerEntity playerEntity;

    private final AdvancementsCache advancementsCache;
    private final BookEditCache bookEditCache;
    private final BundleCache bundleCache;
    private final ChunkCache chunkCache;
    private final EntityCache entityCache;
    private final EntityEffectCache effectCache;
    private final FormCache formCache;
    private final InputCache inputCache;
    private final LodestoneCache lodestoneCache;
    private final PistonCache pistonCache;
    private final PreferencesCache preferencesCache;
    private final RegistryCache registryCache;
    private final SkullCache skullCache;
    private final StructureBlockCache structureBlockCache;
    private final TagCache tagCache;
    private final WorldCache worldCache;

    @Setter
    private TeleportCache unconfirmedTeleport;

    private final WorldBorder worldBorder;
    /**
     * Whether simulated fog has been sent to the client or not.
     */
    private boolean isInWorldBorderWarningArea = false;

    private final PlayerInventory playerInventory;
    @Setter
    private @Nullable Inventory openInventory;
    @Setter
    private boolean closingInventory;

    @Setter
    private @NonNull InventoryTranslator inventoryTranslator = InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR;

    /**
     * Use {@link #getNextItemNetId()} instead for consistency
     */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger itemNetId = new AtomicInteger(2);

    @Setter
    private ScheduledFuture<?> containerOutputFuture;

    /**
     * Stores session collision
     */
    private final CollisionManager collisionManager;

    /**
     * Stores the block mappings for this specific version.
     */
    @Setter
    private BlockMappings blockMappings;

    /**
     * Stores the item translations for this specific version.
     */
    @Setter
    private ItemMappings itemMappings;

    /**
     * A map of Vector3i positions to Java entities.
     * Used for translating Bedrock block actions to Java entity actions.
     */
    private final Map<Vector3i, ItemFrameEntity> itemFrameCache = new Object2ObjectOpenHashMap<>();

    /**
     * A list of all players that have a player head on with a custom texture.
     * Our workaround for these players is to give them a custom skin and geometry to emulate wearing a custom skull.
     */
    private final Set<UUID> playerWithCustomHeads = new ObjectOpenHashSet<>();

    @Setter
    private boolean droppingLecternBook;

    @Setter
    private Vector2i lastChunkPosition = null;
    @Setter
    private int clientRenderDistance = -1;
    private int serverRenderDistance = -1;

    // Exposed for GeyserConnect usage
    protected boolean sentSpawnPacket;

    boolean loggedIn;
    boolean loggingIn;

    @Setter
    private boolean spawned;
    /**
     * Accessed on the initial Java and Bedrock packet processing threads
     */
    private volatile boolean closed;

    private GameMode gameMode = GameMode.SURVIVAL;

    /**
     * Keeps track of the world name for respawning.
     */
    @Setter
    private Key worldName = null;
    /**
     * As of Java 1.19.3, the client only uses these for commands.
     */
    @Setter
    private String[] levels;

    private boolean sneaking;

    /**
     * Stores the Java pose that the server and/or Geyser believes the player currently has.
     */
    @Setter
    private Pose pose = Pose.STANDING;

    @Setter
    private boolean sprinting;

    /**
     * The overworld dimension which Bedrock Edition uses.
     */
    private BedrockDimension bedrockOverworldDimension = BedrockDimension.OVERWORLD;
    /**
     * The dimension of the player.
     * As all entities are in the same world, this can be safely applied to all other entities.
     */
    @MonotonicNonNull
    @Setter
    private JavaDimension dimensionType = null;
    /**
     * Which dimension Bedrock understands themselves to be in.
     * This should only be set after the ChangeDimensionPacket is sent, or
     * right before the StartGamePacket is sent.
     */
    @Setter
    private BedrockDimension bedrockDimension = this.bedrockOverworldDimension;

    /**
     * Stores the blockstate of the block being currently broken.
     */
    @Setter
    private int breakingBlock;

    /**
     * Stores the block break position of the currently broken block.
     */
    @Setter
    private Vector3i blockBreakPosition;

    @Setter
    private Vector3i lastBlockPlacePosition;

    @Setter
    private BlockItem lastBlockPlaced;

    @Setter
    private boolean interacting;

    /**
     * Stores the last position of the block the player interacted with. This can either be a block that the client
     * placed or an existing block the player interacted with (for example, a chest). <br>
     * Initialized as (0, 0, 0) so it is always not-null.
     */
    @Setter
    private Vector3i lastInteractionBlockPosition = Vector3i.ZERO;

    /**
     * Stores the position of the player the last time they interacted.
     * Used to verify that the player did not move since their last interaction. <br>
     * Initialized as (0, 0, 0) so it is always not-null.
     */
    @Setter
    private Vector3f lastInteractionPlayerPosition = Vector3f.ZERO;

    /**
     * The entity that the client is currently looking at.
     */
    @Setter
    private Entity mouseoverEntity;

    /**
     * Stores all Java recipes by ID, and matches them to all possible Bedrock recipe identifiers.
     */
    private final Int2ObjectMap<List<String>> javaToBedrockRecipeIds;

    private final Int2ObjectMap<GeyserRecipe> craftingRecipes;
    @Setter
    private Pair<CraftingRecipeData, GeyserRecipe> lastCreatedRecipe = null; // TODO try to prevent sending duplicate recipes
    private final AtomicInteger lastRecipeNetId;

    /**
     * Saves a list of all stonecutter recipes, for use in a stonecutter inventory.
     * The key is the Bedrock recipe net ID; the values are their respective output and button ID.
     */
    @Setter
    private Int2ObjectMap<GeyserStonecutterData> stonecutterRecipes;
    private final List<GeyserSmithingRecipe> smithingRecipes = new ArrayList<>();

    /**
     * Whether to work around 1.13's different behavior in villager trading menus.
     */
    @Setter
    private boolean emulatePost1_13Logic = true;
    /**
     * Starting in 1.17, Java servers expect the <code>carriedItem</code> parameter of the serverbound click container
     * packet to be the current contents of the mouse after the transaction has been done. 1.16 expects the clicked slot
     * contents before any transaction is done. With the current ViaVersion structure, if we do not send what 1.16 expects
     * and send multiple click container packets, then successive transactions will be rejected.
     */
    @Setter
    private boolean emulatePost1_16Logic = true;
    @Setter
    private boolean emulatePost1_18Logic = true;

    /**
     * Whether to emulate pre-1.20 smithing table behavior.
     * Adapts ViaVersion's furnace UI to one Bedrock can use.
     * See {@link org.geysermc.geyser.translator.inventory.OldSmithingTableTranslator}.
     */
    @Setter
    private boolean oldSmithingTable = false;

    /**
     * Whether to use the minecart_improvements experiment
     */
    @Setter
    private boolean isUsingExperimentalMinecartLogic = false;

    /**
     * The current attack speed of the player. Used for sending proper cooldown timings.
     * Setting a default fixes cooldowns not showing up on a fresh world.
     */
    @Setter
    private double attackSpeed = 4.0d;
    /**
     * The time of the last hit. Used to gauge how long the cooldown is taking.
     * This is a session variable in order to prevent more scheduled threads than necessary.
     */
    @Setter
    private long lastHitTime;

    /**
     * Saves if the client is steering left on a boat.
     */
    @Setter
    private boolean steeringLeft;
    /**
     * Saves if the client is steering right on a boat.
     */
    @Setter
    private boolean steeringRight;

    /**
     * Store the last time the player interacted. Used to fix a right-click spam bug.
     * See <a href="https://github.com/GeyserMC/Geyser/issues/503">this</a> for context.
     */
    @Setter
    private long lastInteractionTime;

    /**
     * Stores when the player started to break a block. Used to allow correct break time for custom blocks.
     */
    @Setter
    private long blockBreakStartTime;

    /**
     * Stores whether the player intended to place a bucket.
     */
    @Setter
    private boolean placedBucket;

    /**
     * Stores whether the Java server requested the player inventory to be closed.
     * Used to prevent our hacky player inventory closing workaround in {@link org.geysermc.geyser.translator.inventory.PlayerInventoryTranslator#closeInventory(GeyserSession, Inventory)}
     * to run when the closing is initated by the Bedrock client.
     */
    @Setter
    private boolean serverRequestedClosePlayerInventory;

    /**
     * Counts how many ticks have occurred since an arm animation started.
     * -1 means there is no active arm swing; -2 means an arm swing will start in a tick.
     */
    private int armAnimationTicks = -1;

    /**
     * Controls whether the daylight cycle gamerule has been sent to the client, so the sun/moon remain motionless.
     */
    private boolean daylightCycle = true;

    private boolean reducedDebugInfo = false;

    /**
     * The op permission level set by the server
     */
    @Setter
    private int opPermissionLevel = 0;

    /**
     * If the current player can fly
     */
    @Setter
    private boolean canFly = false;

    /**
     * If the current player is flying
     */
    private boolean flying = false;

    @Setter
    private boolean instabuild = false;

    @Setter
    private float flySpeed;
    @Setter
    private float walkSpeed;

    /**
     * Caches current rain strength.
     * Value between 0 and 1.
     */
    private float rainStrength = 0.0f;

    /**
     * Caches current thunder strength.
     * Value between 0 and 1.
     */
    private float thunderStrength = 0.0f;

    /**
     * Stores a map of all statistics sent from the server.
     * The server only sends new statistics back to us, so in order to show all statistics we need to cache existing ones.
     */
    private final Object2IntMap<Statistic> statistics = new Object2IntOpenHashMap<>(0);

    /**
     * Whether we're expecting statistics to be sent back to us.
     */
    @Setter
    private boolean waitingForStatistics = false;

    private final Set<UUID> emotes;

    /**
     * Whether advanced tooltips will be added to the player's items.
     */
    @Setter
    private boolean advancedTooltips = false;

    /**
     * The thread that will run every game tick.
     */
    private ScheduledFuture<?> tickThread = null;

    /**
     * The number of ticks that have elapsed since the start of this session
     */
    private int ticks;

    /**
     * The world time in ticks according to the server
     * <p>
     * Note: The TickingStatePacket is currently ignored.
     */
    @Setter
    private long worldTicks;

    /**
     * Used to return the player to their original rotation after using an item in BedrockInventoryTransactionTranslator
     */
    @Setter
    private ScheduledFuture<?> lookBackScheduledFuture = null;

    /**
     * Used to return players back to their vehicles if the server doesn't want them unmounting.
     */
    @Setter
    private ScheduledFuture<?> mountVehicleScheduledFuture = null;

    /**
     * A cache of IDs from ClientboundKeepAlivePackets that have been sent to the Bedrock client, but haven't been returned to the server.
     * Only used if {@link GeyserConfiguration#isForwardPlayerPing()} is enabled.
     */
    private final Queue<Long> keepAliveCache = new ConcurrentLinkedQueue<>();

    /**
     * Stores the book that is currently being read. Used in {@link org.geysermc.geyser.translator.protocol.java.inventory.JavaOpenBookTranslator}
     */
    @Setter
    private @Nullable ItemData currentBook = null;

    /**
     * Stores cookies sent by the Java server.
     */
    @Setter
    private Map<String, byte[]> cookies = new Object2ObjectOpenHashMap<>();

    private final GeyserCameraData cameraData;

    private final GeyserEntityData entityData;

    @Getter(AccessLevel.MODULE)
    private MinecraftProtocol protocol;

    private int nanosecondsPerTick = 50000000;
    private float millisecondsPerTick = 50.0f;
    private boolean tickingFrozen = false;
    /**
     * The amount of ticks requested by the server that the game should proceed with, even if the game tick loop is frozen.
     */
    @Setter
    private int stepTicks = 0;


    public GeyserSession(GeyserImpl geyser, BedrockServerSession bedrockServerSession, EventLoop tickEventLoop) {
        this.geyser = geyser;
        this.upstream = new UpstreamSession(bedrockServerSession);
        this.tickEventLoop = tickEventLoop;

        this.erosionHandler = new GeyserboundHandshakePacketHandler(this);

        this.advancementsCache = new AdvancementsCache(this);
        this.bookEditCache = new BookEditCache(this);
        this.bundleCache = new BundleCache(this);
        this.chunkCache = new ChunkCache(this);
        this.entityCache = new EntityCache(this);
        this.effectCache = new EntityEffectCache();
        this.formCache = new FormCache(this);
        this.inputCache = new InputCache(this);
        this.lodestoneCache = new LodestoneCache();
        this.pistonCache = new PistonCache(this);
        this.preferencesCache = new PreferencesCache(this);
        this.registryCache = new RegistryCache(this);
        this.skullCache = new SkullCache(this);
        this.structureBlockCache = new StructureBlockCache();
        this.tagCache = new TagCache(this);
        this.worldCache = new WorldCache(this);
        this.cameraData = new GeyserCameraData(this);
        this.entityData = new GeyserEntityData(this);

        this.worldBorder = new WorldBorder(this);

        this.collisionManager = new CollisionManager(this);

        this.playerEntity = new SessionPlayerEntity(this);
        collisionManager.updatePlayerBoundingBox(this.playerEntity.getPosition());

        this.playerInventory = new PlayerInventory();
        this.openInventory = null;
        this.craftingRecipes = new Int2ObjectOpenHashMap<>();
        this.javaToBedrockRecipeIds = new Int2ObjectOpenHashMap<>();
        this.lastRecipeNetId = new AtomicInteger(InventoryUtils.LAST_RECIPE_NET_ID + 1);

        this.spawned = false;
        this.loggedIn = false;

        if (geyser.getConfig().getEmoteOffhandWorkaround() != EmoteOffhandWorkaroundOption.NO_EMOTES) {
            this.emotes = new HashSet<>();
            geyser.getSessionManager().getSessions().values().forEach(player -> this.emotes.addAll(player.getEmotes()));
        } else {
            this.emotes = null;
        }

        this.remoteServer = geyser.defaultRemoteServer();
    }

    /**
     * Send all necessary packets to load Bedrock into the server
     */
    public void connect() {
        // Note: this.dimensionType may be null here if the player is connecting from online mode
        int minY = BedrockDimension.OVERWORLD.minY();
        int maxY = BedrockDimension.OVERWORLD.maxY();
        for (JavaDimension javaDimension : this.registryCache.dimensions().values()) {
            if (javaDimension.bedrockId() == BedrockDimension.OVERWORLD_ID) {
                minY = Math.min(minY, javaDimension.minY());
                maxY = Math.max(maxY, javaDimension.maxY());
            }
        }
        minY = Math.max(minY, -512);
        maxY = Math.min(maxY, 512);

        if (minY < BedrockDimension.OVERWORLD.minY() || maxY > BedrockDimension.OVERWORLD.maxY()) {
            final boolean isInOverworld = this.bedrockDimension == this.bedrockOverworldDimension;
            this.bedrockOverworldDimension = new BedrockDimension(minY, maxY - minY, true, BedrockDimension.OVERWORLD_ID);
            if (isInOverworld) {
                this.bedrockDimension = this.bedrockOverworldDimension;
            }
            geyser.getLogger().debug("Extending overworld dimension to " + minY + " - " + maxY);

            DimensionDataPacket dimensionDataPacket = new DimensionDataPacket();
            dimensionDataPacket.getDefinitions().add(new DimensionDefinition("minecraft:overworld", maxY, minY, 5 /* Void */));
            upstream.sendPacket(dimensionDataPacket);
        }

        startGame();
        sentSpawnPacket = true;
        syncEntityProperties();

        if (GameProtocol.isPreCreativeInventoryRewrite(this.protocolVersion())) {
            ItemComponentPacket componentPacket = new ItemComponentPacket();
            componentPacket.getItems().addAll(itemMappings.getComponentItemData());
            upstream.sendPacket(componentPacket);
        } else {
            ItemComponentPacket componentPacket = new ItemComponentPacket();
            componentPacket.getItems().addAll(itemMappings.getItemDefinitions().values());
            upstream.sendPacket(componentPacket);
        }

        ChunkUtils.sendEmptyChunks(this, playerEntity.getPosition().toInt(), 0, false);

        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setDefinitions(Registries.BIOMES_NBT.get());
        upstream.sendPacket(biomeDefinitionListPacket);

        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers(Registries.BEDROCK_ENTITY_IDENTIFIERS.get());
        upstream.sendPacket(entityPacket);

        CameraPresetsPacket cameraPresetsPacket = new CameraPresetsPacket();
        cameraPresetsPacket.getPresets().addAll(CameraDefinitions.CAMERA_PRESETS);
        upstream.sendPacket(cameraPresetsPacket);

        CreativeContentPacket creativePacket = new CreativeContentPacket();
        creativePacket.getContents().addAll(this.itemMappings.getCreativeItems());
        creativePacket.getGroups().addAll(this.itemMappings.getCreativeItemGroups());
        upstream.sendPacket(creativePacket);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        upstream.sendPacket(playStatusPacket);

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(getPlayerEntity().getGeyserId());
        // Default move speed
        // Bedrock clients move very fast by default until they get an attribute packet correcting the speed
        attributesPacket.setAttributes(Collections.singletonList(
            GeyserAttributeType.MOVEMENT_SPEED.getAttribute()));
        upstream.sendPacket(attributesPacket);

        GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
        // Only allow the server to send health information
        // Setting this to false allows natural regeneration to work false but doesn't break it being true
        gamerulePacket.getGameRules().add(new GameRuleData<>("naturalregeneration", false));
        // Don't let the client modify the inventory on death
        // Setting this to true allows keep inventory to work if enabled but doesn't break functionality being false
        gamerulePacket.getGameRules().add(new GameRuleData<>("keepinventory", true));
        // Ensure client doesn't try and do anything funky; the server handles this for us
        gamerulePacket.getGameRules().add(new GameRuleData<>("spawnradius", 0));
        // Recipe unlocking
        gamerulePacket.getGameRules().add(new GameRuleData<>("recipesunlock", true));
        upstream.sendPacket(gamerulePacket);
    }

    public void authenticate(String username) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", username));
            return;
        }

        loggingIn = true;
        // Always replace spaces with underscores to avoid illegal nicknames, e.g. with GeyserConnect
        protocol = new MinecraftProtocol(username.replace(' ', '_'));

        try {
            connectDownstream();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public void authenticateWithAuthChain(String authChain) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", getAuthData().name()));
            return;
        }

        loggingIn = true;

        CompletableFuture.supplyAsync(() -> {
            StepFullJavaSession step = PendingMicrosoftAuthentication.AUTH_FLOW.apply(true, 30);
            StepFullJavaSession.FullJavaSession response;
            try {
                response = step.refresh(MinecraftAuthLogger.INSTANCE, PendingMicrosoftAuthentication.AUTH_CLIENT, step.fromJson(GSON.fromJson(authChain, JsonObject.class)));
            } catch (Exception e) {
                geyser.getLogger().error("Error while attempting to use auth chain for " + bedrockUsername() + "!", e);
                return Boolean.FALSE;
            }

            StepMCProfile.MCProfile mcProfile = response.getMcProfile();
            StepMCToken.MCToken mcToken = mcProfile.getMcToken();

            protocol = new MinecraftProtocol(
                new GameProfile(mcProfile.getId(), mcProfile.getName()),
                mcToken.getAccessToken()
            );
            geyser.saveAuthChain(bedrockUsername(), GSON.toJson(step.toJson(response)));
            return Boolean.TRUE;
        }).whenComplete((successful, ex) -> {
            if (this.closed) {
                return;
            }
            if (successful == Boolean.FALSE) {
                // The player is waiting for a spawn packet, so let's spawn them in now to show them forms
                connect();
                // Will be cached for after login
                LoginEncryptionUtils.buildAndShowTokenExpiredWindow(this);
                return;
            }

            try {
                connectDownstream();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void authenticateWithMicrosoftCode() {
        authenticateWithMicrosoftCode(false);
    }

    /**
     * Present a form window to the user asking to log in with another web browser
     */
    public void authenticateWithMicrosoftCode(boolean offlineAccess) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", getAuthData().name()));
            return;
        }

        loggingIn = true;

        // This just looks cool
        SetTimePacket packet = new SetTimePacket();
        packet.setTime(16000);
        sendUpstreamPacket(packet);

        final PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getOrCreateTask(
            getAuthData().xuid()
        );
        if (task.getAuthentication() != null && task.getAuthentication().isDone()) {
            onMicrosoftLoginComplete(task);
        } else {
            task.resetRunningFlow();
            task.performLoginAttempt(offlineAccess, code -> {
                if (!closed) {
                    LoginEncryptionUtils.buildAndShowMicrosoftCodeWindow(this, code);
                }
            }).handle((r, e) -> onMicrosoftLoginComplete(task));
        }
    }

    /**
     * If successful, also begins connecting to the Java server.
     */
    public boolean onMicrosoftLoginComplete(PendingMicrosoftAuthentication.AuthenticationTask task) {
        if (closed) {
            return false;
        }
        task.cleanup(); // player is online -> remove pending authentication immediately
        return task.getAuthentication().handle((result, ex) -> {
            if (ex != null) {
                geyser.getLogger().error("Failed to log in with Microsoft code!", ex);
                if (ex instanceof CompletionException ce
                    && ce.getCause() instanceof MinecraftRequestException mre
                    && mre.getResponse().getStatusCode() == 404) {
                    // Player is trying to join with a Microsoft account that doesn't have Java Edition purchased
                    disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", locale()));
                } else {
                    disconnect(ex.toString());
                }
                return false;
            }

            StepMCProfile.MCProfile mcProfile = result.session().getMcProfile();
            StepMCToken.MCToken mcToken = mcProfile.getMcToken();

            this.protocol = new MinecraftProtocol(
                new GameProfile(mcProfile.getId(), mcProfile.getName()),
                mcToken.getAccessToken()
            );

            try {
                connectDownstream();
            } catch (Throwable t) {
                t.printStackTrace();
                return false;
            }

            // Save our auth chain for later use
            geyser.saveAuthChain(bedrockUsername(), GSON.toJson(result.step().toJson(result.session())));
            return true;
        }).getNow(false);
    }

    /**
     * After getting whatever credentials needed, we attempt to join the Java server.
     */
    private void connectDownstream() {
        SessionLoginEvent loginEvent = new SessionLoginEvent(this, remoteServer, new Object2ObjectOpenHashMap<>());
        GeyserImpl.getInstance().eventBus().fire(loginEvent);
        if (loginEvent.isCancelled()) {
            String disconnectReason = loginEvent.disconnectReason() == null ?
                BedrockDisconnectReasons.DISCONNECTED : loginEvent.disconnectReason();
            disconnect(disconnectReason);
            return;
        }

        this.cookies = loginEvent.cookies();
        this.remoteServer = loginEvent.remoteServer();

        // Start ticking
        tickThread = tickEventLoop.scheduleAtFixedRate(this::tick, nanosecondsPerTick, nanosecondsPerTick, TimeUnit.NANOSECONDS);

        ClientSession downstream;
        if (geyser.getBootstrap().getSocketAddress() != null) {
            // We're going to connect through the JVM and not through TCP
            downstream = new LocalSession(geyser.getBootstrap().getSocketAddress(),
                upstream.getAddress().getAddress().getHostAddress(),
                this.protocol, this.tickEventLoop);
            downstream.setFlag(MinecraftConstants.CLIENT_HOST, this.remoteServer.address());
            downstream.setFlag(MinecraftConstants.CLIENT_PORT, this.remoteServer.port());
            this.downstream = new DownstreamSession(downstream);
        } else {
            downstream = new ClientNetworkSession(new InetSocketAddress(this.remoteServer.address(), this.remoteServer.port()), this.protocol, tickEventLoop, null, null);
            this.downstream = new DownstreamSession(downstream);

            boolean resolveSrv = false;
            try {
                resolveSrv = this.remoteServer.resolveSrv();
            } catch (AbstractMethodError | NoSuchMethodError ignored) {
                // Ignore if the method doesn't exist
                // This will happen with extensions using old APIs
            }
            this.downstream.getSession().setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, resolveSrv);
        }

        // Disable automatic creation of a new TcpClientSession when transferring - we don't use that functionality.
        this.downstream.getSession().setFlag(MinecraftConstants.FOLLOW_TRANSFERS, false);

        if (geyser.getConfig().getRemote().isUseProxyProtocol()) {
            downstream.setFlag(BuiltinFlags.CLIENT_PROXIED_ADDRESS, upstream.getAddress());
        }
        if (geyser.getConfig().isForwardPlayerPing()) {
            // Let Geyser handle sending the keep alive
            downstream.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
        }
        // We'll handle this since we have the registry data on hand
        downstream.setFlag(MinecraftConstants.SEND_BLANK_KNOWN_PACKS_RESPONSE, false);

        // We manually add the default listener to ensure the order of listeners.
        protocol.setUseDefaultListeners(false);

        // MCPL listener comes first to handle protocol state switching before Geyser translates packets
        downstream.addListener(new ClientListener(HandshakeIntent.LOGIN));
        // Geyser adapter second to ensure translating packets in the correct states
        downstream.addListener(new GeyserSessionAdapter(this));

        downstream.setFlag(BuiltinFlags.CLIENT_TRANSFERRING, loginEvent.transferring());
        downstream.connect(false);

        if (!daylightCycle) {
            setDaylightCycle(true);
        }
    }

    public void disconnect(String reason) {
        disconnect(Component.text(reason));
    }

    public void disconnect(Component reason) {
        if (!closed) {
            loggedIn = false;

            SessionDisconnectEvent disconnectEvent = new SessionDisconnectEventImpl(this, reason);
            if (authData != null && clientData != null) { // can occur if player disconnects before Bedrock auth finishes
                // Fire SessionDisconnectEvent
                geyser.getEventBus().fire(disconnectEvent);
            }

            // Disconnect downstream if necessary
            if (downstream != null) {
                // No need to disconnect if already closed
                if (!downstream.isClosed()) {
                    downstream.disconnect(reason);
                }
            } else {
                // Downstream's disconnect will fire an event that prints a log message
                // Otherwise, we print a message here
                String address = geyser.getConfig().isLogPlayerIpAddresses() ? upstream.getAddress().getAddress().toString() : "<IP address withheld>";
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.disconnect", address, MessageTranslator.convertMessage(reason)));
            }

            // Disconnect upstream if necessary
            if (!upstream.isClosed()) {
                upstream.disconnect(disconnectEvent.disconnectReason());
            }

            // Remove from session manager
            geyser.getSessionManager().removeSession(this);
            if (authData != null) {
                PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(authData.xuid());
                if (task != null) {
                    task.resetRunningFlow();
                }
            }
        }

        if (tickThread != null) {
            tickThread.cancel(false);
        }

        // Mark session as closed before cancelling erosion futures
        closed = true;
        erosionHandler.close();
    }

    /**
     * Moves task to the session event loop if already not in it. Otherwise, the task is automatically ran.
     */
    public void ensureInEventLoop(Runnable runnable) {
        if (tickEventLoop.inEventLoop()) {
            executeRunnable(runnable);
            return;
        }

        executeInEventLoop(runnable);
    }

    /**
     * Executes a task and prints a stack trace if an error occurs.
     */
    public void executeInEventLoop(Runnable runnable) {
        tickEventLoop.execute(() -> executeRunnable(runnable));
    }

    /**
     * Schedules a task and prints a stack trace if an error occurs.
     * <p>
     * The task will not run if the session is closed.
     */
    public ScheduledFuture<?> scheduleInEventLoop(Runnable runnable, long duration, TimeUnit timeUnit) {
        return tickEventLoop.schedule(() -> {
            executeRunnable(() -> {
                if (!closed) {
                    runnable.run();
                }
            });
        }, duration, timeUnit);
    }

    public void updateTickingState(float tickRate, boolean frozen) {
        tickThread.cancel(false);

        this.tickingFrozen = frozen;

        tickRate = MathUtils.clamp(tickRate, 1.0f, 10000.0f);

        millisecondsPerTick = 1000.0f / tickRate;

        nanosecondsPerTick = MathUtils.ceil(1000000000.0f / tickRate);
        tickThread = tickEventLoop.scheduleAtFixedRate(this::tick, nanosecondsPerTick, nanosecondsPerTick, TimeUnit.NANOSECONDS);
    }

    private void executeRunnable(Runnable runnable) {
        try {
            runnable.run();
        } catch (ErosionCancellationException e) {
            geyser.getLogger().debug("Caught ErosionCancellationException");
        } catch (Throwable e) {
            geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
        }

    }

    /**
     * Called every Minecraft tick.
     */
    protected void tick() {
        try {
            pistonCache.tick();

            if (worldBorder.isResizing()) {
                worldBorder.resize();
            }

            boolean shouldShowFog = !worldBorder.isWithinWarningBoundaries();
            if (shouldShowFog || worldBorder.isCloseToBorderBoundaries()) {
                // Show particles representing where the world border is
                worldBorder.drawWall();
                // Set the mood
                if (shouldShowFog && !isInWorldBorderWarningArea) {
                    isInWorldBorderWarningArea = true;
                    camera().sendFog("minecraft:fog_crimson_forest");
                }
            }
            if (!shouldShowFog && isInWorldBorderWarningArea) {
                // Clear fog as we are outside the world border now
                camera().removeFog("minecraft:fog_crimson_forest");
                isInWorldBorderWarningArea = false;
            }

            boolean gameShouldUpdate = !tickingFrozen || stepTicks > 0;
            if (stepTicks > 0) {
                --stepTicks;
            }

            Entity vehicle = playerEntity.getVehicle();
            if (vehicle instanceof ClientVehicle clientVehicle && vehicle.isValid()) {
                clientVehicle.getVehicleComponent().tickVehicle();
            }

            for (Tickable entity : entityCache.getTickableEntities()) {
                entity.drawTick();
                if (gameShouldUpdate) {
                    entity.tick();
                }
            }

            if (armAnimationTicks >= 0) {
                // As of 1.18.2 Java Edition, it appears that the swing time is dynamically updated depending on the
                // player's effect status, but the animation can cut short if the duration suddenly decreases
                // (from suddenly no longer having mining fatigue, for example)
                // This math is referenced from Java Edition 1.18.2
                int swingTotalDuration;
                int hasteLevel = Math.max(effectCache.getHaste(), effectCache.getConduitPower());
                if (hasteLevel > 0) {
                    swingTotalDuration = 6 - hasteLevel;
                } else {
                    int miningFatigueLevel = effectCache.getMiningFatigue();
                    if (miningFatigueLevel > 0) {
                        swingTotalDuration = 6 + miningFatigueLevel * 2;
                    } else {
                        swingTotalDuration = 6;
                    }
                }
                if (++armAnimationTicks >= swingTotalDuration) {
                    if (sneaking) {
                        // Attempt to re-activate blocking as our swing animation is up
                        if (attemptToBlock()) {
                            playerEntity.updateBedrockMetadata();
                        }
                    }
                    armAnimationTicks = -1;
                }
            }

            this.bundleCache.tick();

            if (spawned && protocol.getOutboundState() == ProtocolState.GAME) {
                // Could move this to the PlayerAuthInput translator, in the event the player lags
                // but this will work once we implement matching Java custom tick cycles
                sendDownstreamGamePacket(ServerboundClientTickEndPacket.INSTANCE);
            }
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        ticks++;
        worldTicks++;
    }

    public void setAuthenticationData(AuthData authData) {
        this.authData = authData;
    }

    public void startSneaking() {
        // Toggle the shield, if there is no ongoing arm animation
        // This matches Bedrock Edition behavior as of 1.18.12
        if (armAnimationTicks < 0) {
            attemptToBlock();
        }

        setSneaking(true);
    }

    public void stopSneaking() {
        disableBlocking();

        setSneaking(false);
    }

    private void setSneaking(boolean sneaking) {
        this.sneaking = sneaking;

        // Update pose and bounding box on our end
        if (!flying) {
            // The pose and bounding box should not be updated if the player is flying
            setSneakingPose(sneaking);
        }
        collisionManager.updateScaffoldingFlags(false);

        playerEntity.updateBedrockMetadata();

        if (mouseoverEntity != null) {
            // Horses, etc can change their property depending on if you're sneaking
            mouseoverEntity.updateInteractiveTag();
        }
    }

    private void setSneakingPose(boolean sneaking) {
        if (this.pose == Pose.SNEAKING && !sneaking) {
            this.pose = Pose.STANDING;
            playerEntity.setBoundingBoxHeight(playerEntity.getDefinition().height());
        } else if (sneaking) {
            this.pose = Pose.SNEAKING;
            playerEntity.setBoundingBoxHeight(1.5f);
        }
        playerEntity.setFlag(EntityFlag.SNEAKING, sneaking);
    }

    public void setSwimming(boolean swimming) {
        if (!swimming && playerEntity.getFlag(EntityFlag.CRAWLING)) {
            // Do not update bounding box.
            playerEntity.setFlag(EntityFlag.SWIMMING, false);
            playerEntity.updateBedrockMetadata();
            return;
        }
        toggleSwimmingPose(swimming, EntityFlag.SWIMMING);
    }

    public void setCrawling(boolean crawling) {
        toggleSwimmingPose(crawling, EntityFlag.CRAWLING);
    }

    private void toggleSwimmingPose(boolean crawling, EntityFlag flag) {
        if (crawling) {
            this.pose = Pose.SWIMMING;
            playerEntity.setBoundingBoxHeight(0.6f);
        } else {
            this.pose = Pose.STANDING;
            playerEntity.setBoundingBoxHeight(playerEntity.getDefinition().height());
        }
        playerEntity.setFlag(flag, crawling);
        playerEntity.updateBedrockMetadata();
    }

    public void setFlying(boolean flying) {
        this.flying = flying;

        if (sneaking) {
            // update bounding box as it is not reduced when flying
            setSneakingPose(!flying);
            playerEntity.updateBedrockMetadata();
        }
    }

    public void setGameMode(GameMode newGamemode) {
        boolean currentlySpectator = this.gameMode == GameMode.SPECTATOR;
        this.gameMode = newGamemode;
        this.cameraData.handleGameModeChange(currentlySpectator, newGamemode);
    }

    public void setClientData(BedrockClientData data) {
        this.clientData = data;
        this.inputCache.setInputMode(
            org.cloudburstmc.protocol.bedrock.data.InputMode.values()[data.getCurrentInputMode().ordinal()]);
    }

    /**
     * Convenience method to reduce amount of duplicate code. Sends ServerboundUseItemPacket.
     */
    public void useItem(Hand hand) {
        sendDownstreamGamePacket(new ServerboundUseItemPacket(
            hand, worldCache.nextPredictionSequence(), playerEntity.getYaw(), playerEntity.getPitch()));
    }

    public void releaseItem() {
        // Followed to the Minecraft Protocol specification outlined at wiki.vg
        ServerboundPlayerActionPacket releaseItemPacket = new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM, Vector3i.ZERO,
            Direction.DOWN, 0);
        sendDownstreamGamePacket(releaseItemPacket);
    }

    /**
     * Checks to see if a shield is in either hand to activate blocking. If so, it sets the Bedrock client to display
     * blocking and sends a packet to the Java server.
     */
    private boolean attemptToBlock() {
        if (playerInventory.getItemInHand().asItem() == Items.SHIELD) {
            useItem(Hand.MAIN_HAND);
        } else if (playerInventory.getOffhand().asItem() == Items.SHIELD) {
            useItem(Hand.OFF_HAND);
        } else {
            // No blocking
            return false;
        }

        playerEntity.setFlag(EntityFlag.BLOCKING, true);
        // Metadata should be updated later
        return true;
    }

    /**
     * Starts ticking the amount of time that the Bedrock client has been swinging their arm, and disables blocking if
     * blocking.
     */
    public void activateArmAnimationTicking() {
        armAnimationTicks = 0;
        if (disableBlocking()) {
            playerEntity.updateBedrockMetadata();
        }
    }

    /**
     * For <a href="https://github.com/GeyserMC/Geyser/issues/2113">issue 2113</a> and combating arm ticking activating being delayed in
     * BedrockAnimateTranslator.
     */
    public void armSwingPending() {
        if (armAnimationTicks == -1) {
            armAnimationTicks = -2;
        }
    }

    /**
     * Indicates to the client to stop blocking and tells the Java server the same.
     */
    private boolean disableBlocking() {
        if (playerEntity.getFlag(EntityFlag.BLOCKING)) {
            ServerboundPlayerActionPacket releaseItemPacket = new ServerboundPlayerActionPacket(PlayerAction.RELEASE_USE_ITEM,
                Vector3i.ZERO, Direction.DOWN, 0);
            sendDownstreamGamePacket(releaseItemPacket);
            playerEntity.setFlag(EntityFlag.BLOCKING, false);
            return true;
        }
        return false;
    }

    public void requestOffhandSwap() {
        ServerboundPlayerActionPacket swapHandsPacket = new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO,
            Direction.DOWN, 0);
        sendDownstreamGamePacket(swapHandsPacket);
    }

    @Override
    public String name() {
        return playerEntity != null ? javaUsername() : bedrockUsername();
    }

    @Override
    public void sendMessage(@NonNull String message) {
        TextPacket textPacket = new TextPacket();
        textPacket.setPlatformChatId("");
        textPacket.setSourceName("");
        textPacket.setXuid("");
        textPacket.setType(TextPacket.Type.CHAT);
        textPacket.setNeedsTranslation(false);
        textPacket.setMessage(message);

        upstream.sendPacket(textPacket);
    }

    @Override
    public boolean isConsole() {
        return false;
    }

    @Override
    public UUID playerUuid() {
        return javaUuid(); // CommandSource allows nullable
    }

    @Override
    public GeyserSession connection() {
        return this;
    }

    @Override
    public String locale() {
        return clientData != null ? clientData.getLanguageCode() : GeyserLocale.getDefaultLocale();
    }

    @Override
    public boolean hasPermission(String permission) {
        // for Geyser-Standalone, standalone's permission system will handle it.
        // for server platforms, the session will be mapped to a server command sender, and the server's api will be used.
        return geyser.commandRegistry().hasPermission(this, permission);
    }

    /**
     * Sends a chat message to the Java server.
     */
    public void sendChat(String message) {
        sendDownstreamGamePacket(new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet()));
    }

    /**
     * Sends a command to the Java server.
     */
    public void sendCommand(String command) {
        sendDownstreamGamePacket(new ServerboundChatCommandSignedPacket(command, Instant.now().toEpochMilli(), 0L, Collections.emptyList(), 0, new BitSet()));
    }

    public void setServerRenderDistance(int renderDistance) {
        // Ensure render distance is not above 96 as sending a larger value at any point crashes mobile clients and 96 is the max of any bedrock platform
        renderDistance = Math.min(renderDistance, 96);
        this.serverRenderDistance = renderDistance;

        ChunkRadiusUpdatedPacket chunkRadiusUpdatedPacket = new ChunkRadiusUpdatedPacket();
        chunkRadiusUpdatedPacket.setRadius(renderDistance);
        upstream.sendPacket(chunkRadiusUpdatedPacket);
    }

    public InetSocketAddress getSocketAddress() {
        return this.upstream.getAddress();
    }

    @Override
    public boolean sendForm(@NonNull Form form) {
        formCache.showForm(form);
        return true;
    }

    @Override
    public boolean sendForm(@NonNull FormBuilder<?, ?, ?> formBuilder) {
        formCache.showForm(formBuilder.build());
        return true;
    }

    /**
     * @deprecated since Cumulus version 1.1, and will be removed when Cumulus 2.0 releases. Please use the new forms instead.
     */
    @Deprecated
    public void sendForm(org.geysermc.cumulus.Form<?> form) {
        sendForm(form.newForm());
    }

    /**
     * @deprecated since Cumulus version 1.1, and will be removed when Cumulus 2.0 releases. Please use the new forms instead.
     */
    @Deprecated
    public void sendForm(org.geysermc.cumulus.util.FormBuilder<?, ?> formBuilder) {
        sendForm(formBuilder.build());
    }

    private void startGame() {
        this.upstream.getCodecHelper().setItemDefinitions(this.itemMappings);
        this.upstream.getCodecHelper().setBlockDefinitions(this.blockMappings);
        this.upstream.getCodecHelper().setCameraPresetDefinitions(CameraDefinitions.CAMERA_DEFINITIONS);

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(playerEntity.getGeyserId());
        startGamePacket.setRuntimeEntityId(playerEntity.getGeyserId());
        startGamePacket.setPlayerGameType(EntityUtils.toBedrockGamemode(gameMode));
        startGamePacket.setPlayerPosition(Vector3f.from(0, 69, 0));
        startGamePacket.setRotation(Vector2f.from(1, 1));

        startGamePacket.setSeed(-1L);
        startGamePacket.setDimensionId(bedrockDimension.bedrockId());
        startGamePacket.setGeneratorId(1);
        startGamePacket.setLevelGameType(GameType.SURVIVAL);
        startGamePacket.setDifficulty(1);
        startGamePacket.setDefaultSpawn(Vector3i.ZERO);
        startGamePacket.setAchievementsDisabled(!geyser.getConfig().isXboxAchievementsEnabled());
        startGamePacket.setCurrentTick(-1);
        startGamePacket.setEduEditionOffers(0);
        startGamePacket.setEduFeaturesEnabled(false);
        startGamePacket.setRainLevel(0);
        startGamePacket.setLightningLevel(0);
        startGamePacket.setMultiplayerGame(true);
        startGamePacket.setBroadcastingToLan(true);
        startGamePacket.setPlatformBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setXblBroadcastMode(GamePublishSetting.PUBLIC);
        startGamePacket.setCommandsEnabled(!geyser.getConfig().isXboxAchievementsEnabled());
        startGamePacket.setTexturePacksRequired(false);
        startGamePacket.setBonusChestEnabled(false);
        startGamePacket.setStartingWithMap(false);
        startGamePacket.setTrustingPlayers(true);
        startGamePacket.setDefaultPlayerPermission(PlayerPermission.MEMBER);
        startGamePacket.setServerChunkTickRange(4);
        startGamePacket.setBehaviorPackLocked(false);
        startGamePacket.setResourcePackLocked(false);
        startGamePacket.setFromLockedWorldTemplate(false);
        startGamePacket.setUsingMsaGamertagsOnly(false);
        startGamePacket.setFromWorldTemplate(false);
        startGamePacket.setWorldTemplateOptionLocked(false);
        startGamePacket.setSpawnBiomeType(SpawnBiomeType.DEFAULT);
        startGamePacket.setCustomBiomeName("");
        startGamePacket.setEducationProductionId("");
        startGamePacket.setForceExperimentalGameplay(OptionalBoolean.empty());

        String serverName = geyser.getConfig().getBedrock().serverName();
        startGamePacket.setLevelId(serverName);
        startGamePacket.setLevelName(serverName);

        startGamePacket.setPremiumWorldTemplateId("00000000-0000-0000-0000-000000000000");
        // startGamePacket.setCurrentTick(0);
        startGamePacket.setEnchantmentSeed(0);
        startGamePacket.setMultiplayerCorrelationId("");

        startGamePacket.getItemDefinitions().addAll(this.itemMappings.getItemDefinitions().values());

        // Needed for custom block mappings and custom skulls system
        startGamePacket.getBlockProperties().addAll(this.blockMappings.getBlockProperties());

        // See https://learn.microsoft.com/en-us/minecraft/creator/documents/experimentalfeaturestoggle for info on each experiment
        // data_driven_items (Holiday Creator Features) is needed for blocks and items
        startGamePacket.getExperiments().add(new ExperimentData("data_driven_items", true));
        // Needed for block properties for states
        startGamePacket.getExperiments().add(new ExperimentData("upcoming_creator_features", true));
        // Needed for certain molang queries used in blocks and items
        startGamePacket.getExperiments().add(new ExperimentData("experimental_molang_features", true));

        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine(""); // Do we want to fill this in?

        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());

        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);

        startGamePacket.setAuthoritativeMovementMode(AuthoritativeMovementMode.SERVER);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(true);

        startGamePacket.setServerId("");
        startGamePacket.setWorldId("");
        startGamePacket.setScenarioId("");

        upstream.sendPacket(startGamePacket);
    }

    private void syncEntityProperties() {
        for (NbtMap nbtMap : Registries.BEDROCK_ENTITY_PROPERTIES.get()) {
            SyncEntityPropertyPacket syncEntityPropertyPacket = new SyncEntityPropertyPacket();
            syncEntityPropertyPacket.setData(nbtMap);
            upstream.sendPacket(syncEntityPropertyPacket);
        }
    }

    /**
     * @return the next Bedrock item network ID to use for a new item
     */
    public int getNextItemNetId() {
        return itemNetId.getAndIncrement();
    }

    public void confirmTeleport(Vector3d position) {
        if (unconfirmedTeleport == null) {
            return;
        }

        if (unconfirmedTeleport.canConfirm(position)) {
            unconfirmedTeleport = null;
            return;
        }

        // Resend the teleport every few packets until Bedrock responds
        unconfirmedTeleport.incrementUnconfirmedFor();
        if (unconfirmedTeleport.shouldResend()) {
            unconfirmedTeleport.resetUnconfirmedFor();
            geyser.getLogger().debug("Resending teleport " + unconfirmedTeleport.getTeleportConfirmId());
            getPlayerEntity().moveAbsolute(Vector3f.from(unconfirmedTeleport.getX(), unconfirmedTeleport.getY(), unconfirmedTeleport.getZ()),
                unconfirmedTeleport.getYaw(), unconfirmedTeleport.getPitch(), playerEntity.isOnGround(), true);
        }
    }

    /**
     * Queue a packet to be sent to player.
     *
     * @param packet the bedrock packet from the NukkitX protocol lib
     */
    public void sendUpstreamPacket(BedrockPacket packet) {
        upstream.sendPacket(packet);
    }

    /**
     * Send a packet immediately to the player.
     *
     * @param packet the bedrock packet from the NukkitX protocol lib
     */
    public void sendUpstreamPacketImmediately(BedrockPacket packet) {
        upstream.sendPacketImmediately(packet);
    }

    /**
     * Send a packet to the remote server if in the game state.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamGamePacket(Packet packet) {
        sendDownstreamPacket(packet, ProtocolState.GAME);
    }

    /**
     * Send a packet to the remote server if in the login state.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamLoginPacket(Packet packet) {
        sendDownstreamPacket(packet, ProtocolState.LOGIN);
    }

    /**
     * Send a packet to the remote server if in the specified state.
     *
     * @param packet the java edition packet from MCProtocolLib
     * @param intendedState the state the client should be in
     */
    public void sendDownstreamPacket(Packet packet, ProtocolState intendedState) {
        // protocol can be null when we're not yet logged in (online auth)
        if (protocol == null) {
            if (geyser.getConfig().isDebugMode()) {
                geyser.getLogger().debug("Tried to send downstream packet with no downstream session!");
                Thread.dumpStack();
            }
            return;
        }

        if (protocol.getOutboundState() != intendedState) {
            geyser.getLogger().debug("Tried to send " + packet.getClass().getSimpleName() + " packet while not in " + intendedState.name() + " outbound state. Current state: " + protocol.getOutboundState().name());
            return;
        }

        sendDownstreamPacket(packet);
    }

    /**
     * Send a packet to the remote server.
     *
     * @param packet the java edition packet from MCProtocolLib
     */
    public void sendDownstreamPacket(Packet packet) {
        if (!closed && this.downstream != null) {
            Channel channel = this.downstream.getSession().getChannel();
            if (channel == null) {
                // Channel is only null before the connection has initialized
                geyser.getLogger().warning("Tried to send a packet to the Java server too early!");
                if (geyser.getConfig().isDebugMode()) {
                    Thread.dumpStack();
                }
                return;
            }

            EventLoop eventLoop = channel.eventLoop();
            if (eventLoop.inEventLoop()) {
                sendDownstreamPacket0(packet);
            } else {
                eventLoop.execute(() -> sendDownstreamPacket0(packet));
            }
        }
    }

    private void sendDownstreamPacket0(Packet packet) {
        ProtocolState state = protocol.getOutboundState();
        if (state == ProtocolState.GAME || state == ProtocolState.CONFIGURATION || packet.getClass() == ServerboundCustomQueryAnswerPacket.class) {
            downstream.sendPacket(packet);
        } else {
            geyser.getLogger().debug("Tried to send downstream packet " + packet.getClass().getSimpleName() + " before connected to the server");
        }
    }

    /**
     * Update the cached value for the reduced debug info gamerule.
     * If enabled, also hides the player's coordinates.
     *
     * @param value The new value for reducedDebugInfo
     */
    public void setReducedDebugInfo(boolean value) {
        reducedDebugInfo = value;
        // Set the showCoordinates data. This is done because updateShowCoordinates() uses this gamerule as a variable.
        preferencesCache.updateShowCoordinates();
    }

    /**
     * Changes the daylight cycle gamerule on the client
     * This is used in the login screen along-side normal usage
     *
     * @param doCycle If the cycle should continue
     */
    public void setDaylightCycle(boolean doCycle) {
        sendGameRule("dodaylightcycle", doCycle);
        // Save the value so we don't have to constantly send a daylight cycle gamerule update
        this.daylightCycle = doCycle;
    }

    /**
     * Send a gamerule value to the client
     *
     * @param gameRule The gamerule to send
     * @param value The value of the gamerule
     */
    public void sendGameRule(String gameRule, Object value) {
        GameRulesChangedPacket gameRulesChangedPacket = new GameRulesChangedPacket();
        gameRulesChangedPacket.getGameRules().add(new GameRuleData<>(gameRule, value));
        upstream.sendPacket(gameRulesChangedPacket);
    }

    private static final Ability[] USED_ABILITIES = Ability.values();

    /**
     * Send an AdventureSettingsPacket to the client with the latest flags
     */
    public void sendAdventureSettings() {
        long bedrockId = playerEntity.getGeyserId();
        // Set command permission if OP permission level is high enough
        // This allows mobile players access to a GUI for doing commands. The commands there do not change above OPERATOR
        // and all commands there are accessible with OP permission level 2
        CommandPermission commandPermission = opPermissionLevel >= 2 ? CommandPermission.GAME_DIRECTORS : CommandPermission.ANY;
        // Required to make command blocks destroyable
        PlayerPermission playerPermission = opPermissionLevel >= 2 ? PlayerPermission.OPERATOR : PlayerPermission.MEMBER;

        // Update the noClip and worldImmutable values based on the current gamemode
        boolean spectator = gameMode == GameMode.SPECTATOR;
        boolean worldImmutable = gameMode == GameMode.ADVENTURE || spectator;

        UpdateAdventureSettingsPacket adventureSettingsPacket = new UpdateAdventureSettingsPacket();
        adventureSettingsPacket.setNoMvP(false);
        adventureSettingsPacket.setNoPvM(false);
        adventureSettingsPacket.setImmutableWorld(worldImmutable);
        adventureSettingsPacket.setShowNameTags(false);
        adventureSettingsPacket.setAutoJump(true);
        sendUpstreamPacket(adventureSettingsPacket);

        UpdateAbilitiesPacket updateAbilitiesPacket = new UpdateAbilitiesPacket();
        updateAbilitiesPacket.setUniqueEntityId(bedrockId);
        updateAbilitiesPacket.setCommandPermission(commandPermission);
        updateAbilitiesPacket.setPlayerPermission(playerPermission);

        AbilityLayer abilityLayer = new AbilityLayer();
        Set<Ability> abilities = abilityLayer.getAbilityValues();
        if (canFly) {
            abilities.add(Ability.MAY_FLY);
        }

        // Default stuff we have to fill in
        abilities.add(Ability.BUILD);
        abilities.add(Ability.MINE);
        // Needed so you can drop items
        abilities.add(Ability.DOORS_AND_SWITCHES);
        // Required for lecterns to work (likely started around 1.19.10; confirmed on 1.19.70)
        abilities.add(Ability.OPEN_CONTAINERS);
        if (gameMode == GameMode.CREATIVE) {
            // Needed so the client doesn't attempt to take away items
            abilities.add(Ability.INSTABUILD);
        }

        if (commandPermission == CommandPermission.GAME_DIRECTORS) {
            // Fixes a bug? since 1.19.11 where the player can change their gamemode in Bedrock settings and
            // a packet is not sent to the server.
            // https://github.com/GeyserMC/Geyser/issues/3191
            abilities.add(Ability.OPERATOR_COMMANDS);
        }

        if (flying || spectator) {
            if (spectator && !flying) {
                // We're "flying locked" in this gamemode
                flying = true;
                ServerboundPlayerAbilitiesPacket abilitiesPacket = new ServerboundPlayerAbilitiesPacket(true);
                sendDownstreamGamePacket(abilitiesPacket);
            }
            abilities.add(Ability.FLYING);
        }

        if (spectator) {
            AbilityLayer spectatorLayer = new AbilityLayer();
            spectatorLayer.setLayerType(AbilityLayer.Type.SPECTATOR);
            // Setting all abilitySet causes the zoom issue... BDS only sends these, so ig we will too
            Set<Ability> abilitySet = spectatorLayer.getAbilitiesSet();
            abilitySet.add(Ability.BUILD);
            abilitySet.add(Ability.MINE);
            abilitySet.add(Ability.DOORS_AND_SWITCHES);
            abilitySet.add(Ability.OPEN_CONTAINERS);
            abilitySet.add(Ability.ATTACK_PLAYERS);
            abilitySet.add(Ability.ATTACK_MOBS);
            abilitySet.add(Ability.INVULNERABLE);
            abilitySet.add(Ability.FLYING);
            abilitySet.add(Ability.MAY_FLY);
            abilitySet.add(Ability.INSTABUILD);
            abilitySet.add(Ability.NO_CLIP);

            Set<Ability> abilityValues = spectatorLayer.getAbilityValues();
            abilityValues.add(Ability.INVULNERABLE);
            abilityValues.add(Ability.FLYING);
            abilityValues.add(Ability.NO_CLIP);

            updateAbilitiesPacket.getAbilityLayers().add(spectatorLayer);
        }

        abilityLayer.setLayerType(AbilityLayer.Type.BASE);
        abilityLayer.setFlySpeed(flySpeed);
        // https://github.com/GeyserMC/Geyser/issues/3139 as of 1.19.10
        abilityLayer.setWalkSpeed(walkSpeed == 0f ? 0.01f : walkSpeed);
        abilityLayer.setVerticalFlySpeed(1.0f);
        Collections.addAll(abilityLayer.getAbilitiesSet(), USED_ABILITIES);

        updateAbilitiesPacket.getAbilityLayers().add(abilityLayer);
        sendUpstreamPacket(updateAbilitiesPacket);
    }

    private int getRenderDistance() {
        if (clientRenderDistance != -1) {
            // The client has sent a render distance
            return clientRenderDistance;
        } else if (serverRenderDistance != -1) {
            // only known once ClientboundLoginPacket is received
            return serverRenderDistance;
        }
        return 2; // unfortunate default until we got more info
    }

    // We need to send our skin parts to the server otherwise java sees us with no hat, jacket etc
    private static final List<SkinPart> SKIN_PARTS = Arrays.asList(SkinPart.values());

    /**
     * Send a packet to the server to indicate client render distance, locale, skin parts, and hand preference.
     */
    public void sendJavaClientSettings() {
        // Locale is lowercase on Java - (https://github.com/GeyserMC/Geyser/issues/5235)
        ServerboundClientInformationPacket clientSettingsPacket = new ServerboundClientInformationPacket(locale().toLowerCase(Locale.ROOT),
            getRenderDistance(), ChatVisibility.FULL, true, SKIN_PARTS,
            HandPreference.RIGHT_HAND, false, true, ParticleStatus.ALL); // TODO particle status
        sendDownstreamPacket(clientSettingsPacket);
    }

    /**
     * Used for updating statistic values since we only get changes from the server
     *
     * @param statistics Updated statistics values
     */
    public void updateStatistics(@NonNull Object2IntMap<Statistic> statistics) {
        if (this.statistics.isEmpty()) {
            // Initialize custom statistics to 0, so that they appear in the form
            for (CustomStatistic customStatistic : CustomStatistic.values()) {
                this.statistics.put(customStatistic, 0);
            }
        }
        this.statistics.putAll(statistics);
    }

    public void refreshEmotes(List<UUID> emotes) {
        this.emotes.addAll(emotes);
        for (GeyserSession player : geyser.getSessionManager().getSessions().values()) {
            List<UUID> pieces = new ArrayList<>();
            for (UUID piece : emotes) {
                if (!player.getEmotes().contains(piece)) {
                    pieces.add(piece);
                }
                player.getEmotes().add(piece);
            }
            EmoteListPacket emoteList = new EmoteListPacket();
            emoteList.setRuntimeEntityId(player.getPlayerEntity().getGeyserId());
            emoteList.getPieceIds().addAll(pieces);
            player.sendUpstreamPacket(emoteList);
        }
    }

    public boolean canUseCommandBlocks() {
        return instabuild && opPermissionLevel >= 2;
    }

    public void playSoundEvent(SoundEvent sound, Vector3f position) {
        LevelSoundEvent2Packet packet = new LevelSoundEvent2Packet();
        packet.setPosition(position);
        packet.setSound(sound);
        packet.setIdentifier(":");
        packet.setExtraData(-1);
        sendUpstreamPacket(packet);
    }

    public float getEyeHeight() {
        return switch (pose) {
            case SNEAKING -> 1.27f;
            case SWIMMING,
                 FALL_FLYING, // Elytra
                 SPIN_ATTACK -> 0.4f; // Trident spin attack
            case SLEEPING -> 0.2f;
            default -> EntityDefinitions.PLAYER.offset();
        };
    }

    /**
     * Sends a packet to update rain strength.
     * Stops rain if strength is 0.
     *
     * @param strength value between 0 and 1
     */
    public void updateRain(float strength) {
        boolean wasRaining = isRaining();
        this.rainStrength = strength;

        LevelEventPacket rainPacket = new LevelEventPacket();
        rainPacket.setType(isRaining() ? LevelEvent.START_RAINING : LevelEvent.STOP_RAINING);
        rainPacket.setData((int) (strength * 65535));
        rainPacket.setPosition(Vector3f.ZERO);
        sendUpstreamPacket(rainPacket);

        // Keep thunder in sync with rain when starting/stopping a storm
        if ((wasRaining != isRaining()) && isThunder()) {
            if (isRaining()) {
                LevelEventPacket thunderPacket = new LevelEventPacket();
                thunderPacket.setType(LevelEvent.START_THUNDERSTORM);
                thunderPacket.setData((int) (this.thunderStrength * 65535));
                thunderPacket.setPosition(Vector3f.ZERO);
                sendUpstreamPacket(thunderPacket);
            } else {
                LevelEventPacket thunderPacket = new LevelEventPacket();
                thunderPacket.setType(LevelEvent.STOP_THUNDERSTORM);
                thunderPacket.setData(0);
                thunderPacket.setPosition(Vector3f.ZERO);
                sendUpstreamPacket(thunderPacket);
            }
        }
    }

    /**
     * Sends a packet to update thunderstorm strength.
     * Stops thunderstorm if strength is 0.
     *
     * @param strength value between 0 and 1
     */
    public void updateThunder(float strength) {
        this.thunderStrength = strength;

        // Do not send thunder packet if not raining
        // The bedrock client will start raining automatically when updating thunder strength
        // https://github.com/GeyserMC/Geyser/issues/3679
        if (!isRaining()) {
            return;
        }

        LevelEventPacket thunderPacket = new LevelEventPacket();
        thunderPacket.setType(isThunder() ? LevelEvent.START_THUNDERSTORM : LevelEvent.STOP_THUNDERSTORM);
        thunderPacket.setData((int) (strength * 65535));
        thunderPacket.setPosition(Vector3f.ZERO);
        sendUpstreamPacket(thunderPacket);
    }

    public boolean isRaining() {
        return this.rainStrength > 0;
    }

    public boolean isThunder() {
        return this.thunderStrength > 0;
    }

    @Override
    public @NonNull String bedrockUsername() {
        return authData.name();
    }

    @Override
    public @MonotonicNonNull String javaUsername() {
        return playerEntity != null ? playerEntity.getUsername() : null;
    }

    @Override
    public UUID javaUuid() {
        return playerEntity != null ? playerEntity.getUuid() : null;
    }

    @Override
    public @NonNull String xuid() {
        return authData.xuid();
    }

    @Override
    public @NonNull String version() {
        return clientData.getGameVersion();
    }

    @Override
    public @NonNull BedrockPlatform platform() {
        return BedrockPlatform.values()[clientData.getDeviceOs().ordinal()]; //todo
    }

    @Override
    public @NonNull String languageCode() {
        return locale();
    }

    @Override
    public @NonNull UiProfile uiProfile() {
        return UiProfile.values()[clientData.getUiProfile().ordinal()]; //todo
    }

    @Override
    public @NonNull InputMode inputMode() {
        return InputMode.values()[inputCache.getInputMode().ordinal()]; //todo
    }

    @Override
    public boolean isLinked() {
        return false; //todo
    }

    @SuppressWarnings("ConstantConditions") // Need to enforce the parameter annotations
    @Override
    public boolean transfer(@NonNull String address, @IntRange(from = 0, to = 65535) int port) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Server address cannot be null or blank");
        } else if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Server port must be between 0 and 65535, was " + port);
        }
        TransferPacket transferPacket = new TransferPacket();
        transferPacket.setAddress(address);
        transferPacket.setPort(port);
        sendUpstreamPacket(transferPacket);
        return true;
    }

    @Override
    public @NonNull CompletableFuture<@Nullable GeyserEntity> entityByJavaId(@NonNegative int javaId) {
        return entities().entityByJavaId(javaId);
    }

    @Override
    public void showEmote(@NonNull GeyserPlayerEntity emoter, @NonNull String emoteId) {
        entities().showEmote(emoter, emoteId);
    }

    public void lockInputs(boolean camera, boolean movement) {
        UpdateClientInputLocksPacket packet = new UpdateClientInputLocksPacket();
        final int cameraOffset = 1 << 1;
        final int movementOffset = 1 << 2;

        int result = 0;
        if (camera) {
            result |= cameraOffset;
        }
        if (movement) {
            result |= movementOffset;
        }

        packet.setLockComponentData(result);
        packet.setServerPosition(this.playerEntity.getPosition());

        sendUpstreamPacket(packet);
    }

    @Override
    public @NonNull CameraData camera() {
        return this.cameraData;
    }

    @Override
    public @NonNull EntityData entities() {
        return this.entityData;
    }

    @Override
    public void shakeCamera(float intensity, float duration, @NonNull CameraShake type) {
        this.cameraData.shakeCamera(intensity, duration, type);
    }

    @Override
    public void stopCameraShake() {
        this.cameraData.stopCameraShake();
    }

    @Override
    public void sendFog(String... fogNameSpaces) {
        this.cameraData.sendFog(fogNameSpaces);
    }

    @Override
    public void removeFog(String... fogNameSpaces) {
        this.cameraData.removeFog(fogNameSpaces);
    }

    @Override
    public @NonNull Set<String> fogEffects() {
        return this.cameraData.fogEffects();
    }

    @Override
    public int ping() {
        RakSessionCodec rakSessionCodec = ((RakChildChannel) getUpstream().getSession().getPeer().getChannel()).rakPipeline().get(RakSessionCodec.class);
        return (int) Math.floor(rakSessionCodec.getPing());
    }

    @Override
    public int protocolVersion() {
        return upstream.getProtocolVersion();
    }

    @Override
    public void closeForm() {
        sendUpstreamPacket(new ClientboundCloseFormPacket());
    }

    public void addCommandEnum(String name, String enums) {
        softEnumPacket(name, SoftEnumUpdateType.ADD, enums);
    }

    public void removeCommandEnum(String name, String enums) {
        softEnumPacket(name, SoftEnumUpdateType.REMOVE, enums);
    }

    private void softEnumPacket(String name, SoftEnumUpdateType type, String enums) {
        // There is no need to send command enums if command suggestions are disabled
        if (!this.geyser.getConfig().isCommandSuggestions()) {
            return;
        }
        UpdateSoftEnumPacket packet = new UpdateSoftEnumPacket();
        packet.setType(type);
        packet.setSoftEnum(new CommandEnumData(name, Collections.singletonMap(enums, Collections.emptySet()), true));
        sendUpstreamPacket(packet);
    }
}
