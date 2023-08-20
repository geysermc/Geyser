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

package org.geysermc.geyser.session;

import com.github.steveice10.mc.auth.data.GameProfile;
import com.github.steveice10.mc.auth.exception.request.InvalidCredentialsException;
import com.github.steveice10.mc.auth.exception.request.RequestException;
import com.github.steveice10.mc.auth.service.AuthenticationService;
import com.github.steveice10.mc.auth.service.MojangAuthenticationService;
import com.github.steveice10.mc.auth.service.MsaAuthenticationService;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import com.github.steveice10.mc.protocol.MinecraftProtocol;
import com.github.steveice10.mc.protocol.data.ProtocolState;
import com.github.steveice10.mc.protocol.data.UnexpectedEncryptionException;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.Pose;
import com.github.steveice10.mc.protocol.data.game.entity.object.Direction;
import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.data.game.entity.player.HandPreference;
import com.github.steveice10.mc.protocol.data.game.entity.player.PlayerAction;
import com.github.steveice10.mc.protocol.data.game.setting.ChatVisibility;
import com.github.steveice10.mc.protocol.data.game.setting.SkinPart;
import com.github.steveice10.mc.protocol.data.game.statistic.CustomStatistic;
import com.github.steveice10.mc.protocol.data.game.statistic.Statistic;
import com.github.steveice10.mc.protocol.packet.handshake.serverbound.ClientIntentionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundChatPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.ServerboundClientInformationPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerAbilitiesPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundPlayerActionPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.mc.protocol.packet.login.serverbound.ServerboundCustomQueryPacket;
import com.github.steveice10.packetlib.BuiltinFlags;
import com.github.steveice10.packetlib.Session;
import com.github.steveice10.packetlib.event.session.ConnectedEvent;
import com.github.steveice10.packetlib.event.session.DisconnectedEvent;
import com.github.steveice10.packetlib.event.session.PacketErrorEvent;
import com.github.steveice10.packetlib.event.session.PacketSendingEvent;
import com.github.steveice10.packetlib.event.session.SessionAdapter;
import com.github.steveice10.packetlib.packet.Packet;
import com.github.steveice10.packetlib.tcp.TcpClientSession;
import com.github.steveice10.packetlib.tcp.TcpSession;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.value.qual.IntRange;
import org.cloudburstmc.math.vector.*;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.BedrockDisconnectReasons;
import org.cloudburstmc.protocol.bedrock.BedrockServerSession;
import org.cloudburstmc.protocol.bedrock.data.*;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.data.command.SoftEnumUpdateType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.protocol.common.util.OptionalBoolean;
import org.geysermc.api.util.BedrockPlatform;
import org.geysermc.api.util.InputMode;
import org.geysermc.api.util.UiProfile;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.preference.Preference;
import org.geysermc.geyser.api.preference.PreferenceKey;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormBuilder;
import org.geysermc.floodgate.crypto.FloodgateCipher;
import org.geysermc.floodgate.util.BedrockData;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.EmoteOffhandWorkaroundOption;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.entity.attribute.GeyserAttributeType;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.ItemFrameEntity;
import org.geysermc.geyser.entity.type.Tickable;
import org.geysermc.geyser.entity.type.player.SessionPlayerEntity;
import org.geysermc.geyser.erosion.AbstractGeyserboundPacketHandler;
import org.geysermc.geyser.erosion.GeyserboundHandshakePacketHandler;
import org.geysermc.geyser.inventory.Inventory;
import org.geysermc.geyser.inventory.PlayerInventory;
import org.geysermc.geyser.inventory.recipe.GeyserRecipe;
import org.geysermc.geyser.inventory.recipe.GeyserStonecutterData;
import org.geysermc.geyser.item.Items;
import org.geysermc.geyser.level.JavaDimension;
import org.geysermc.geyser.level.WorldManager;
import org.geysermc.geyser.level.physics.CollisionManager;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.network.netty.LocalSession;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.BlockMappings;
import org.geysermc.geyser.registry.type.ItemMappings;
import org.geysermc.geyser.session.auth.AuthData;
import org.geysermc.geyser.session.auth.BedrockClientData;
import org.geysermc.geyser.session.cache.*;
import org.geysermc.geyser.skin.FloodgateSkinUploader;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.text.TextDecoration;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.geyser.util.ChunkUtils;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.geyser.util.LoginEncryptionUtils;
import org.jetbrains.annotations.NotNull;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class GeyserSession implements GeyserConnection, GeyserCommandSource {

    private final GeyserImpl geyser;
    private final UpstreamSession upstream;
    private DownstreamSession downstream;
    /**
     * The loop where all packets and ticking is processed to prevent concurrency issues.
     * If this is manually called, ensure that any exceptions are properly handled.
     */
    private final EventLoop eventLoop;
    @Setter
    private AuthData authData;
    @Setter
    private BedrockClientData clientData;
    /**
     * Used for Floodgate skin uploading
     */
    @Setter
    private List<String> certChainData;

    @NotNull
    @Setter
    private AbstractGeyserboundPacketHandler erosionHandler;

    @Accessors(fluent = true)
    @Setter
    private RemoteServer remoteServer;

    @Deprecated
    @Setter
    private boolean microsoftAccount;

    private final SessionPlayerEntity playerEntity;

    private final AdvancementsCache advancementsCache;
    private final BookEditCache bookEditCache;
    private final ChunkCache chunkCache;
    private final EntityCache entityCache;
    private final EntityEffectCache effectCache;
    private final FormCache formCache;
    private final LodestoneCache lodestoneCache;
    private final PistonCache pistonCache;
    private final PreferencesCache preferencesCache;
    private final SkullCache skullCache;
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
    private Inventory openInventory;
    @Setter
    private boolean closingInventory;

    @Setter
    private InventoryTranslator inventoryTranslator = InventoryTranslator.PLAYER_INVENTORY_TRANSLATOR;

    /**
     * Use {@link #getNextItemNetId()} instead for consistency
     */
    @Getter(AccessLevel.NONE)
    private final AtomicInteger itemNetId = new AtomicInteger(2);

    @Setter
    private ScheduledFuture<?> craftingGridFuture;

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

    private final Long2ObjectMap<ClientboundMapItemDataPacket> storedMaps = new Long2ObjectOpenHashMap<>();

    /**
     * Required to decode biomes correctly.
     */
    @Setter
    private int biomeGlobalPalette;
    /**
     * Stores the map between Java and Bedrock biome network IDs.
     */
    @Setter
    private int[] biomeTranslations = null;

    /**
     * A map of Vector3i positions to Java entities.
     * Used for translating Bedrock block actions to Java entity actions.
     */
    private final Map<Vector3i, ItemFrameEntity> itemFrameCache = new Object2ObjectOpenHashMap<>();

    /**
     * Stores a list of all lectern locations and their block entity tags.
     * See {@link WorldManager#sendLecternData(GeyserSession, int, int, int)}
     * for more information.
     */
    private final Set<Vector3i> lecternCache;

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
    private int serverRenderDistance;

    // Exposed for GeyserConnect usage
    protected boolean sentSpawnPacket;

    private boolean loggedIn;
    private boolean loggingIn;

    @Setter
    private boolean spawned;
    /**
     * Accessed on the initial Java and Bedrock packet processing threads
     */
    private volatile boolean closed;

    @Setter
    private GameMode gameMode = GameMode.SURVIVAL;

    /**
     * Keeps track of the world name for respawning.
     */
    @Setter
    private String worldName = null;
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
     * Whether the player is swimming in water.
     * Used to update speed when crawling.
     */
    @Setter
    private boolean swimmingInWater;

    /**
     * Tracks the original speed attribute.
     *
     * We need to do this in order to emulate speeds when sneaking under 1.5-blocks-tall areas if the player isn't sneaking,
     * and when crawling.
     */
    @Setter
    private float originalSpeedAttribute;

    /**
     * The dimension of the player.
     * As all entities are in the same world, this can be safely applied to all other entities.
     */
    @Setter
    private String dimension = DimensionUtils.OVERWORLD;
    @MonotonicNonNull
    @Setter
    private JavaDimension dimensionType = null;
    /**
     * All dimensions that the client could possibly connect to.
     */
    private final Map<String, JavaDimension> dimensions = new Object2ObjectOpenHashMap<>(3);

    private final Int2ObjectMap<TextDecoration> chatTypes = new Int2ObjectOpenHashMap<>(7);

    @Setter
    private int breakingBlock;

    @Setter
    private Vector3i lastBlockPlacePosition;

    @Setter
    private String lastBlockPlacedId;

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

    @Setter
    private Int2ObjectMap<GeyserRecipe> craftingRecipes;
    private final AtomicInteger lastRecipeNetId;

    /**
     * Saves a list of all stonecutter recipes, for use in a stonecutter inventory.
     * The key is the Java ID of the item; the values are all the possible outputs' Java IDs sorted by their string identifier
     */
    @Setter
    private Int2ObjectMap<GeyserStonecutterData> stonecutterRecipes;

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
     * See https://github.com/GeyserMC/Geyser/issues/503 for context.
     */
    @Setter
    private long lastInteractionTime;

    /**
     * Stores whether the player intended to place a bucket.
     */
    @Setter
    private boolean placedBucket;

    /**
     * Used to send a movement packet every three seconds if the player hasn't moved. Prevents timeouts when AFK in certain instances.
     */
    @Setter
    private long lastMovementTimestamp = System.currentTimeMillis();

    /**
     * Used to send a ServerboundMoveVehiclePacket for every PlayerInputPacket after idling on a boat/horse for more than 100ms
     */
    @Setter
    private long lastVehicleMoveTimestamp = System.currentTimeMillis();

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
     * Caches current rain status.
     */
    @Setter
    private boolean raining = false;

    /**
     * Caches current thunder status.
     */
    @Setter
    private boolean thunder = false;

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

    /**
     * All fog effects that are currently applied to the client.
     */
    private final Set<String> appliedFog = new HashSet<>();

    private final Set<UUID> emotes;

    /**
     * Whether advanced tooltips will be added to the player's items.
     */
    @Setter
    private boolean advancedTooltips = false;

    /**
     * The thread that will run every 50 milliseconds - one Minecraft tick.
     */
    private ScheduledFuture<?> tickThread = null;

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

    private MinecraftProtocol protocol;

    public GeyserSession(GeyserImpl geyser, BedrockServerSession bedrockServerSession, EventLoop eventLoop) {
        this.geyser = geyser;
        this.upstream = new UpstreamSession(bedrockServerSession);
        this.eventLoop = eventLoop;

        this.erosionHandler = new GeyserboundHandshakePacketHandler(this);

        this.advancementsCache = new AdvancementsCache(this);
        this.bookEditCache = new BookEditCache(this);
        this.chunkCache = new ChunkCache(this);
        this.entityCache = new EntityCache(this);
        this.effectCache = new EntityEffectCache();
        this.formCache = new FormCache(this);
        this.lodestoneCache = new LodestoneCache();
        this.pistonCache = new PistonCache(this);
        this.preferencesCache = new PreferencesCache(this);
        this.skullCache = new SkullCache(this);
        this.tagCache = new TagCache();
        this.worldCache = new WorldCache(this);

        this.worldBorder = new WorldBorder(this);

        this.collisionManager = new CollisionManager(this);

        this.playerEntity = new SessionPlayerEntity(this);
        collisionManager.updatePlayerBoundingBox(this.playerEntity.getPosition());

        this.playerInventory = new PlayerInventory();
        this.openInventory = null;
        this.craftingRecipes = new Int2ObjectOpenHashMap<>();
        this.lastRecipeNetId = new AtomicInteger(1);

        this.spawned = false;
        this.loggedIn = false;

        if (geyser.getWorldManager().shouldExpectLecternHandled(this)) {
            // Unneeded on these platforms
            this.lecternCache = null;
        } else {
            this.lecternCache = new ObjectOpenHashSet<>();
        }

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
        startGame();
        sentSpawnPacket = true;

        // Set the hardcoded shield ID to the ID we just defined in StartGamePacket
        // upstream.getSession().getHardcodedBlockingId().set(this.itemMappings.getStoredItems().shield().getBedrockId());

        if (GeyserImpl.getInstance().getConfig().isAddNonBedrockItems()) {
            ItemComponentPacket componentPacket = new ItemComponentPacket();
            componentPacket.getItems().addAll(itemMappings.getComponentItemData());
            upstream.sendPacket(componentPacket);
        }

        ChunkUtils.sendEmptyChunks(this, playerEntity.getPosition().toInt(), 0, false);

        BiomeDefinitionListPacket biomeDefinitionListPacket = new BiomeDefinitionListPacket();
        biomeDefinitionListPacket.setDefinitions(Registries.BIOMES_NBT.get());
        upstream.sendPacket(biomeDefinitionListPacket);

        AvailableEntityIdentifiersPacket entityPacket = new AvailableEntityIdentifiersPacket();
        entityPacket.setIdentifiers(Registries.BEDROCK_ENTITY_IDENTIFIERS.get());
        upstream.sendPacket(entityPacket);

        CreativeContentPacket creativePacket = new CreativeContentPacket();
        creativePacket.setContents(this.itemMappings.getCreativeItems());
        upstream.sendPacket(creativePacket);

        // Potion mixes are registered by default, as they are needed to be able to put ingredients into the brewing stand.
        CraftingDataPacket craftingDataPacket = new CraftingDataPacket();
        craftingDataPacket.setCleanRecipes(true);
        craftingDataPacket.getPotionMixData().addAll(Registries.POTION_MIXES.forVersion(this.upstream.getProtocolVersion()));
        upstream.sendPacket(craftingDataPacket);

        PlayStatusPacket playStatusPacket = new PlayStatusPacket();
        playStatusPacket.setStatus(PlayStatusPacket.Status.PLAYER_SPAWN);
        upstream.sendPacket(playStatusPacket);

        UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
        attributesPacket.setRuntimeEntityId(getPlayerEntity().getGeyserId());
        // Default move speed
        // Bedrock clients move very fast by default until they get an attribute packet correcting the speed
        attributesPacket.setAttributes(Collections.singletonList(
                new AttributeData("minecraft:movement", 0.0f, 1024f, 0.1f, 0.1f)));
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
        upstream.sendPacket(gamerulePacket);
    }

    public void authenticate(String username) {
        authenticate(username, "");
    }

    public void authenticate(String username, String password) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", username));
            return;
        }

        loggingIn = true;

        // Use a future to prevent timeouts as all the authentication is handled sync
        CompletableFuture.supplyAsync(() -> {
            try {
                if (password != null && !password.isEmpty()) {
                    AuthenticationService authenticationService;
                    if (microsoftAccount) {
                        authenticationService = new MsaAuthenticationService(GeyserImpl.OAUTH_CLIENT_ID);
                    } else {
                        authenticationService = new MojangAuthenticationService();
                    }
                    authenticationService.setUsername(username);
                    authenticationService.setPassword(password);
                    authenticationService.login();

                    GameProfile profile = authenticationService.getSelectedProfile();
                    if (profile == null) {
                        // Java account is offline
                        disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", clientData.getLanguageCode()));
                        return null;
                    }

                    protocol = new MinecraftProtocol(profile, authenticationService.getAccessToken());
                } else {
                    // always replace spaces when using Floodgate,
                    // as usernames with spaces cause issues with Bungeecord's login cycle.
                    // However, this doesn't affect the final username as Floodgate is still in charge of that.
                    // So if you have (for example) replace spaces enabled on Floodgate the spaces will re-appear.
                    String validUsername = username;
                    if (this.remoteServer.authType() == AuthType.FLOODGATE) {
                        validUsername = username.replace(' ', '_');
                    }

                    protocol = new MinecraftProtocol(validUsername);
                }
            } catch (InvalidCredentialsException | IllegalArgumentException e) {
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.auth.login.invalid", username));
                disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.login.invalid.kick", getClientData().getLanguageCode()));
            } catch (RequestException ex) {
                disconnect(ex.getMessage());
            }
            return null;
        }).whenComplete((aVoid, ex) -> {
            if (ex != null) {
                disconnect(ex.toString());
            }
            if (this.closed) {
                if (ex != null) {
                    geyser.getLogger().error("", ex);
                }
                // Client disconnected during the authentication attempt
                return;
            }

            try {
                connectDownstream();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }

    public void authenticateWithRefreshToken(String refreshToken) {
        if (loggedIn) {
            geyser.getLogger().severe(GeyserLocale.getLocaleStringLog("geyser.auth.already_loggedin", getAuthData().name()));
            return;
        }

        loggingIn = true;

        CompletableFuture.supplyAsync(() -> {
            MsaAuthenticationService service = new MsaAuthenticationService(GeyserImpl.OAUTH_CLIENT_ID);
            service.setRefreshToken(refreshToken);
            try {
                service.login();
            } catch (RequestException e) {
                geyser.getLogger().error("Error while attempting to use refresh token for " + bedrockUsername() + "!", e);
                return Boolean.FALSE;
            }

            GameProfile profile = service.getSelectedProfile();
            if (profile == null) {
                // Java account is offline
                disconnect(GeyserLocale.getPlayerLocaleString("geyser.network.remote.invalid_account", clientData.getLanguageCode()));
                return null;
            }

            protocol = new MinecraftProtocol(profile, service.getAccessToken());
            geyser.saveRefreshToken(bedrockUsername(), service.getRefreshToken());
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
        task.setOnline(true);
        task.resetTimer();

        if (task.getAuthentication().isDone()) {
            onMicrosoftLoginComplete(task);
        } else {
            task.getCode(offlineAccess).whenComplete((response, ex) -> {
                boolean connected = !closed;
                if (ex != null) {
                    if (connected) {
                        geyser.getLogger().error("Failed to get Microsoft auth code", ex);
                        disconnect(ex.toString());
                    }
                    task.cleanup(); // error getting auth code -> clean up immediately
                } else if (connected) {
                    LoginEncryptionUtils.buildAndShowMicrosoftCodeWindow(this, response);
                    task.getAuthentication().whenComplete((r, $) -> onMicrosoftLoginComplete(task));
                }
            });
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
        Throwable ex = task.getLoginException();
        if (ex != null) {
            geyser.getLogger().error("Failed to log in with Microsoft code!", ex);
            disconnect(ex.toString());
        } else {
            MsaAuthenticationService service = task.getMsaAuthenticationService();
            GameProfile selectedProfile = service.getSelectedProfile();
            if (selectedProfile == null) {
                disconnect(GeyserLocale.getPlayerLocaleString(
                        "geyser.network.remote.invalid_account",
                        clientData.getLanguageCode()
                ));
            } else {
                this.protocol = new MinecraftProtocol(
                        selectedProfile,
                        service.getAccessToken()
                );
                try {
                    connectDownstream();
                } catch (Throwable t) {
                    t.printStackTrace();
                    return false;
                }

                // Save our refresh token for later use
                geyser.saveRefreshToken(bedrockUsername(), service.getRefreshToken());
                return true;
            }
        }
        return false;
    }

    /**
     * After getting whatever credentials needed, we attempt to join the Java server.
     */
    private void connectDownstream() {
        SessionLoginEvent loginEvent = new SessionLoginEvent(this, remoteServer);
        GeyserImpl.getInstance().eventBus().fire(loginEvent);
        if (loginEvent.isCancelled()) {
            String disconnectReason = loginEvent.disconnectReason() == null ?
                    BedrockDisconnectReasons.DISCONNECTED : loginEvent.disconnectReason();
            disconnect(disconnectReason);
            return;
        }

        this.remoteServer = loginEvent.remoteServer();
        boolean floodgate = this.remoteServer.authType() == AuthType.FLOODGATE;

        // Start ticking
        tickThread = eventLoop.scheduleAtFixedRate(this::tick, 50, 50, TimeUnit.MILLISECONDS);

        TcpSession downstream;
        if (geyser.getBootstrap().getSocketAddress() != null) {
            // We're going to connect through the JVM and not through TCP
            downstream = new LocalSession(this.remoteServer.address(), this.remoteServer.port(),
                    geyser.getBootstrap().getSocketAddress(), upstream.getAddress().getAddress().getHostAddress(),
                    this.protocol, this.protocol.createHelper());
            this.downstream = new DownstreamSession(downstream);
        } else {
            downstream = new TcpClientSession(this.remoteServer.address(), this.remoteServer.port(), this.protocol);
            this.downstream = new DownstreamSession(downstream);
            disableSrvResolving();
        }

        if (geyser.getConfig().getRemote().isUseProxyProtocol()) {
            downstream.setFlag(BuiltinFlags.ENABLE_CLIENT_PROXY_PROTOCOL, true);
            downstream.setFlag(BuiltinFlags.CLIENT_PROXIED_ADDRESS, upstream.getAddress());
        }
        if (geyser.getConfig().isForwardPlayerPing()) {
            // Let Geyser handle sending the keep alive
            downstream.setFlag(MinecraftConstants.AUTOMATIC_KEEP_ALIVE_MANAGEMENT, false);
        }
        downstream.addListener(new SessionAdapter() {
            @Override
            public void packetSending(PacketSendingEvent event) {
                //todo move this somewhere else
                if (event.getPacket() instanceof ClientIntentionPacket) {
                    String addressSuffix;
                    if (floodgate) {
                        byte[] encryptedData;

                        try {
                            FloodgateSkinUploader skinUploader = geyser.getSkinUploader();
                            FloodgateCipher cipher = geyser.getCipher();

                            String bedrockAddress = upstream.getAddress().getAddress().getHostAddress();
                            // both BungeeCord and Velocity remove the IPv6 scope (if there is one) for Spigot
                            int ipv6ScopeIndex = bedrockAddress.indexOf('%');
                            if (ipv6ScopeIndex != -1) {
                                bedrockAddress = bedrockAddress.substring(0, ipv6ScopeIndex);
                            }

                            encryptedData = cipher.encryptFromString(BedrockData.of(
                                    clientData.getGameVersion(),
                                    authData.name(),
                                    authData.xuid(),
                                    clientData.getDeviceOs().ordinal(),
                                    clientData.getLanguageCode(),
                                    clientData.getUiProfile().ordinal(),
                                    clientData.getCurrentInputMode().ordinal(),
                                    bedrockAddress,
                                    skinUploader.getId(),
                                    skinUploader.getVerifyCode()
                            ).toString());
                        } catch (Exception e) {
                            geyser.getLogger().error(GeyserLocale.getLocaleStringLog("geyser.auth.floodgate.encrypt_fail"), e);
                            disconnect(GeyserLocale.getPlayerLocaleString("geyser.auth.floodgate.encryption_fail", getClientData().getLanguageCode()));
                            return;
                        }

                        addressSuffix = '\0' + new String(encryptedData, StandardCharsets.UTF_8);
                    } else {
                        addressSuffix = "";
                    }

                    ClientIntentionPacket intentionPacket = event.getPacket();

                    String address;
                    if (geyser.getConfig().getRemote().isForwardHost()) {
                        address = clientData.getServerAddress().split(":")[0];
                    } else {
                        address = intentionPacket.getHostname();
                    }

                    event.setPacket(intentionPacket.withHostname(address + addressSuffix));
                }
            }

            @Override
            public void connected(ConnectedEvent event) {
                loggingIn = false;
                loggedIn = true;

                if (downstream instanceof LocalSession) {
                    // Connected directly to the server
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect_internal",
                            authData.name(), protocol.getProfile().getName()));
                } else {
                    // Connected to an IP address
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.connect",
                            authData.name(), protocol.getProfile().getName(), remoteServer.address()));
                }

                UUID uuid = protocol.getProfile().getId();
                if (uuid == null) {
                    // Set what our UUID *probably* is going to be
                    if (remoteServer.authType() == AuthType.FLOODGATE) {
                        uuid = new UUID(0, Long.parseLong(authData.xuid()));
                    } else {
                        uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + protocol.getProfile().getName()).getBytes(StandardCharsets.UTF_8));
                    }
                }
                playerEntity.setUuid(uuid);
                playerEntity.setUsername(protocol.getProfile().getName());

                String locale = clientData.getLanguageCode();

                // Let the user know there locale may take some time to download
                // as it has to be extracted from a JAR
                if (locale.equalsIgnoreCase("en_us") && !MinecraftLocale.LOCALE_MAPPINGS.containsKey("en_us")) {
                    // This should probably be left hardcoded as it will only show for en_us clients
                    sendMessage("Loading your locale (en_us); if this isn't already downloaded, this may take some time");
                }

                // Download and load the language for the player
                MinecraftLocale.downloadAndLoadLocale(locale);
            }

            @Override
            public void disconnected(DisconnectedEvent event) {
                loggingIn = false;
                loggedIn = false;

                String disconnectMessage;
                Throwable cause = event.getCause();
                if (cause instanceof UnexpectedEncryptionException) {
                    if (remoteServer.authType() != AuthType.FLOODGATE) {
                        // Server expects online mode
                        disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.authentication_type_mismatch", locale());
                        // Explain that they may be looking for Floodgate.
                        geyser.getLogger().warning(GeyserLocale.getLocaleStringLog(
                                geyser.getPlatformType() == PlatformType.STANDALONE ?
                                        "geyser.network.remote.floodgate_explanation_standalone"
                                        : "geyser.network.remote.floodgate_explanation_plugin",
                                Constants.FLOODGATE_DOWNLOAD_LOCATION
                        ));
                    } else {
                        // Likely that Floodgate is not configured correctly.
                        disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.floodgate_login_error", locale());
                        if (geyser.getPlatformType() == PlatformType.STANDALONE) {
                            geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.remote.floodgate_login_error_standalone"));
                        }
                    }
                } else if (cause instanceof ConnectException) {
                    // Server is offline, probably
                    disconnectMessage = GeyserLocale.getPlayerLocaleString("geyser.network.remote.server_offline", locale());
                } else {
                    disconnectMessage = MessageTranslator.convertMessage(event.getReason());
                }

                if (downstream instanceof LocalSession) {
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect_internal", authData.name(), disconnectMessage));
                } else {
                    geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.remote.disconnect", authData.name(), remoteServer.address(), disconnectMessage));
                }
                if (cause != null) {
                    cause.printStackTrace();
                }

                upstream.disconnect(disconnectMessage);
            }

            @Override
            public void packetReceived(Session session, Packet packet) {
                Registries.JAVA_PACKET_TRANSLATORS.translate(packet.getClass(), packet, GeyserSession.this);
            }

            @Override
            public void packetError(PacketErrorEvent event) {
                geyser.getLogger().warning(GeyserLocale.getLocaleStringLog("geyser.network.downstream_error", event.getCause().getMessage()));
                if (geyser.getConfig().isDebugMode())
                    event.getCause().printStackTrace();
                event.setSuppress(true);
            }
        });

        if (!daylightCycle) {
            setDaylightCycle(true);
        }

        downstream.connect(false);
    }

    public void disconnect(String reason) {
        if (!closed) {
            loggedIn = false;
            if (downstream != null) {
                downstream.disconnect(reason);
            } else {
                // Downstream's disconnect will fire an event that prints a log message
                // Otherwise, we print a message here
                String address = geyser.getConfig().isLogPlayerIpAddresses() ? upstream.getAddress().getAddress().toString() : "<IP address withheld>";
                geyser.getLogger().info(GeyserLocale.getLocaleStringLog("geyser.network.disconnect", address, reason));
            }
            if (!upstream.isClosed()) {
                upstream.disconnect(reason);
            }
            geyser.getSessionManager().removeSession(this);
            if (authData != null) {
                PendingMicrosoftAuthentication.AuthenticationTask task = geyser.getPendingMicrosoftAuthentication().getTask(authData.xuid());
                if (task != null) {
                    task.setOnline(false);
                }
            }
        }

        if (tickThread != null) {
            tickThread.cancel(false);
        }

        erosionHandler.close();

        closed = true;
    }

    /**
     * Moves task to the session event loop if already not in it. Otherwise, the task is automatically ran.
     */
    public void ensureInEventLoop(Runnable runnable) {
        if (eventLoop.inEventLoop()) {
            runnable.run();
            return;
        }
        executeInEventLoop(runnable);
    }

    /**
     * Executes a task and prints a stack trace if an error occurs.
     */
    public void executeInEventLoop(Runnable runnable) {
        eventLoop.execute(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
            }
        });
    }

    /**
     * Schedules a task and prints a stack trace if an error occurs.
     */
    public ScheduledFuture<?> scheduleInEventLoop(Runnable runnable, long duration, TimeUnit timeUnit) {
        return eventLoop.schedule(() -> {
            try {
                runnable.run();
            } catch (Throwable e) {
                geyser.getLogger().error("Error thrown in " + this.bedrockUsername() + "'s event loop!", e);
            }
        }, duration, timeUnit);
    }

    /**
     * Called every 50 milliseconds - one Minecraft tick.
     */
    protected void tick() {
        try {
            pistonCache.tick();
            // Check to see if the player's position needs updating - a position update should be sent once every 3 seconds
            if (spawned && (System.currentTimeMillis() - lastMovementTimestamp) > 3000) {
                // Recalculate in case something else changed position
                Vector3d position = collisionManager.adjustBedrockPosition(playerEntity.getPosition(), playerEntity.isOnGround(), false);
                // A null return value cancels the packet
                if (position != null) {
                    ServerboundMovePlayerPosPacket packet = new ServerboundMovePlayerPosPacket(playerEntity.isOnGround(),
                            position.getX(), position.getY(), position.getZ());
                    sendDownstreamPacket(packet);
                }
                lastMovementTimestamp = System.currentTimeMillis();
            }

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
                    sendFog("minecraft:fog_crimson_forest");
                }
            }
            if (!shouldShowFog && isInWorldBorderWarningArea) {
                // Clear fog as we are outside the world border now
                removeFog("minecraft:fog_crimson_forest");
                isInWorldBorderWarningArea = false;
            }


            for (Tickable entity : entityCache.getTickableEntities()) {
                entity.tick();
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
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
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
        AttributeData speedAttribute;
        if (!sneaking && (speedAttribute = adjustSpeed()) != null) {
            // Update attributes since we're still "sneaking" under a 1.5-block-tall area
            UpdateAttributesPacket attributesPacket = new UpdateAttributesPacket();
            attributesPacket.setRuntimeEntityId(playerEntity.getGeyserId());
            attributesPacket.setAttributes(Collections.singletonList(speedAttribute));
            sendUpstreamPacket(attributesPacket);
            // the server *should* update our pose once it has returned to normal
        } else {
            if (!flying) {
                // The pose and bounding box should not be updated if the player is flying
                setSneakingPose(sneaking);
            }
            collisionManager.updateScaffoldingFlags(false);
        }

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
        if (swimming) {
            this.pose = Pose.SWIMMING;
            playerEntity.setBoundingBoxHeight(0.6f);
        } else {
            this.pose = Pose.STANDING;
            playerEntity.setBoundingBoxHeight(playerEntity.getDefinition().height());
        }
        playerEntity.setFlag(EntityFlag.SWIMMING, swimming);
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

    /**
     * Adjusts speed if the player is crawling.
     *
     * @return not null if attributes should be updated.
     */
    public AttributeData adjustSpeed() {
        AttributeData currentPlayerSpeed = playerEntity.getAttributes().get(GeyserAttributeType.MOVEMENT_SPEED);
        if (currentPlayerSpeed != null) {
            if ((pose.equals(Pose.SNEAKING) && !sneaking && collisionManager.mustPlayerSneakHere()) ||
                    (!swimmingInWater && playerEntity.getFlag(EntityFlag.SWIMMING) && !collisionManager.isPlayerInWater())) {
                // Either of those conditions means that Bedrock goes zoom when they shouldn't be
                AttributeData speedAttribute = GeyserAttributeType.MOVEMENT_SPEED.getAttribute(originalSpeedAttribute / 3.32f);
                playerEntity.getAttributes().put(GeyserAttributeType.MOVEMENT_SPEED, speedAttribute);
                return speedAttribute;
            } else if (originalSpeedAttribute != currentPlayerSpeed.getValue()) {
                // Speed has reset to normal
                AttributeData speedAttribute = GeyserAttributeType.MOVEMENT_SPEED.getAttribute(originalSpeedAttribute);
                playerEntity.getAttributes().put(GeyserAttributeType.MOVEMENT_SPEED, speedAttribute);
                return speedAttribute;
            }
        }
        return null;
    }

    /**
     * Checks to see if a shield is in either hand to activate blocking. If so, it sets the Bedrock client to display
     * blocking and sends a packet to the Java server.
     */
    private boolean attemptToBlock() {
        ServerboundUseItemPacket useItemPacket;
        if (playerInventory.getItemInHand().asItem() == Items.SHIELD) {
            useItemPacket = new ServerboundUseItemPacket(Hand.MAIN_HAND, worldCache.nextPredictionSequence());
        } else if (playerInventory.getOffhand().asItem() == Items.SHIELD) {
            useItemPacket = new ServerboundUseItemPacket(Hand.OFF_HAND, worldCache.nextPredictionSequence());
        } else {
            // No blocking
            return false;
        }

        sendDownstreamPacket(useItemPacket);
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
     * For https://github.com/GeyserMC/Geyser/issues/2113 and combating arm ticking activating being delayed in
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
            sendDownstreamPacket(releaseItemPacket);
            playerEntity.setFlag(EntityFlag.BLOCKING, false);
            return true;
        }
        return false;
    }

    public void requestOffhandSwap() {
        ServerboundPlayerActionPacket swapHandsPacket = new ServerboundPlayerActionPacket(PlayerAction.SWAP_HANDS, Vector3i.ZERO,
                Direction.DOWN, 0);
        sendDownstreamPacket(swapHandsPacket);
    }

    /**
     * Will be overwritten for GeyserConnect.
     */
    protected void disableSrvResolving() {
        this.downstream.getSession().setFlag(BuiltinFlags.ATTEMPT_SRV_RESOLVE, false);
    }

    @Override
    public String name() {
        return null;
    }

    @Override
    public void sendMessage(String message) {
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
    public String locale() {
        return clientData.getLanguageCode();
    }

    /**
     * Sends a chat message to the Java server.
     */
    public void sendChat(String message) {
        sendDownstreamPacket(new ServerboundChatPacket(message, Instant.now().toEpochMilli(), 0L, null, 0, new BitSet()));
    }

    /**
     * Sends a command to the Java server.
     */
    public void sendCommand(String command) {
        sendDownstreamPacket(new ServerboundChatCommandPacket(command, Instant.now().toEpochMilli(), 0L, Collections.emptyList(), 0, new BitSet()));
    }

    public void setServerRenderDistance(int renderDistance) {
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
        this.upstream.getCodecHelper().setBlockDefinitions((DefinitionRegistry) this.blockMappings); //FIXME

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.setUniqueEntityId(playerEntity.getGeyserId());
        startGamePacket.setRuntimeEntityId(playerEntity.getGeyserId());
        startGamePacket.setPlayerGameType(switch (gameMode) {
            case CREATIVE -> GameType.CREATIVE;
            case ADVENTURE -> GameType.ADVENTURE;
            default -> GameType.SURVIVAL;
        });
        startGamePacket.setPlayerPosition(Vector3f.from(0, 69, 0));
        startGamePacket.setRotation(Vector2f.from(1, 1));

        startGamePacket.setSeed(-1L);
        startGamePacket.setDimensionId(DimensionUtils.javaToBedrock(chunkCache.getBedrockDimension()));
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

        startGamePacket.setItemDefinitions(this.itemMappings.getItemDefinitions().values().stream().toList()); // TODO
        // startGamePacket.setBlockPalette(this.blockMappings.getBedrockBlockPalette());

        startGamePacket.setVanillaVersion("*");
        startGamePacket.setInventoriesServerAuthoritative(true);
        startGamePacket.setServerEngine(""); // Do we want to fill this in?

        startGamePacket.setPlayerPropertyData(NbtMap.EMPTY);
        startGamePacket.setWorldTemplateId(UUID.randomUUID());

        startGamePacket.setChatRestrictionLevel(ChatRestrictionLevel.NONE);

        startGamePacket.setAuthoritativeMovementMode(AuthoritativeMovementMode.CLIENT);
        startGamePacket.setRewindHistorySize(0);
        startGamePacket.setServerAuthoritativeBlockBreaking(false);

        if (GameProtocol.isPre1_20(this)) {
            startGamePacket.getExperiments().add(new ExperimentData("next_major_update", true));
            startGamePacket.getExperiments().add(new ExperimentData("sniffer", true));
        }

        upstream.sendPacket(startGamePacket);
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
        if (protocol.getState().equals(ProtocolState.GAME) || packet.getClass() == ServerboundCustomQueryPacket.class) {
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
     * @param value    The value of the gamerule
     */
    public void sendGameRule(String gameRule, Object value) {
        GameRulesChangedPacket gameRulesChangedPacket = new GameRulesChangedPacket();
        gameRulesChangedPacket.getGameRules().add(new GameRuleData<>(gameRule, value));
        upstream.sendPacket(gameRulesChangedPacket);
    }

    /**
     * Checks if the given session's player has a permission
     *
     * @param permission The permission node to check
     * @return true if the player has the requested permission, false if not
     */
    @Override
    public boolean hasPermission(String permission) {
        return geyser.getWorldManager().hasPermission(this, permission);
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
        if (canFly || spectator) {
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
                sendDownstreamPacket(abilitiesPacket);
            }
            abilities.add(Ability.FLYING);
        }

        if (spectator) {
            abilities.add(Ability.NO_CLIP);
        }

        // https://github.com/GeyserMC/Geyser/issues/3769 Setting Spectator mode ability layer
        if (spectator) {
            abilityLayer.setLayerType(AbilityLayer.Type.SPECTATOR);
        } else {
            abilityLayer.setLayerType(AbilityLayer.Type.BASE);
        }
        abilityLayer.setFlySpeed(flySpeed);
        // https://github.com/GeyserMC/Geyser/issues/3139 as of 1.19.10
        abilityLayer.setWalkSpeed(walkSpeed == 0f ? 0.01f : walkSpeed);
        Collections.addAll(abilityLayer.getAbilitiesSet(), USED_ABILITIES);

        updateAbilitiesPacket.getAbilityLayers().add(abilityLayer);
        sendUpstreamPacket(updateAbilitiesPacket);
    }

    private int getRenderDistance() {
        if (clientRenderDistance != -1) {
            // The client has sent a render distance
            return clientRenderDistance;
        }
        return serverRenderDistance;
    }

    // We need to send our skin parts to the server otherwise java sees us with no hat, jacket etc
    private static final List<SkinPart> SKIN_PARTS = Arrays.asList(SkinPart.values());

    /**
     * Send a packet to the server to indicate client render distance, locale, skin parts, and hand preference.
     */
    public void sendJavaClientSettings() {
        ServerboundClientInformationPacket clientSettingsPacket = new ServerboundClientInformationPacket(locale(),
                getRenderDistance(), ChatVisibility.FULL, true, SKIN_PARTS,
                HandPreference.RIGHT_HAND, false, true);
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

    @Override
    public String bedrockUsername() {
        return authData.name();
    }

    @Override
    public @MonotonicNonNull String javaUsername() {
        return playerEntity.getUsername();
    }

    @Override
    public UUID javaUuid() {
        return playerEntity.getUuid();
    }

    @Override
    public String xuid() {
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
        return InputMode.values()[clientData.getCurrentInputMode().ordinal()]; //todo
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
        CompletableFuture<GeyserEntity> future = new CompletableFuture<>();
        ensureInEventLoop(() -> future.complete(this.entityCache.getEntityByJavaId(javaId)));
        return future;
    }

    @Override
    public void showEmote(@NonNull GeyserPlayerEntity emoter, @NonNull String emoteId) {
        Entity entity = (Entity) emoter;
        if (entity.getSession() != this) {
            throw new IllegalStateException("Given entity must be from this session!");
        }

        EmotePacket packet = new EmotePacket();
        packet.setRuntimeEntityId(entity.getGeyserId());
        packet.setXuid("");
        packet.setPlatformId(""); // BDS sends empty
        packet.setEmoteId(emoteId);
        sendUpstreamPacket(packet);
    }

    @Override
    public void shakeCamera(float intensity, float duration, @NonNull CameraShake type) {
        CameraShakePacket packet = new CameraShakePacket();
        packet.setIntensity(intensity);
        packet.setDuration(duration);
        packet.setShakeType(type == CameraShake.POSITIONAL ? CameraShakeType.POSITIONAL : CameraShakeType.ROTATIONAL);
        packet.setShakeAction(CameraShakeAction.ADD);
        sendUpstreamPacket(packet);
    }

    @Override
    public void stopCameraShake() {
        CameraShakePacket packet = new CameraShakePacket();
        // CameraShakeAction.STOP removes all types regardless of the given type, but regardless it can't be null
        packet.setShakeType(CameraShakeType.POSITIONAL);
        packet.setShakeAction(CameraShakeAction.STOP);
        sendUpstreamPacket(packet);
    }

    @Override
    public void sendFog(String... fogNameSpaces) {
        Collections.addAll(this.appliedFog, fogNameSpaces);

        PlayerFogPacket packet = new PlayerFogPacket();
        packet.getFogStack().addAll(this.appliedFog);
        sendUpstreamPacket(packet);
    }

    @Override
    public void removeFog(String... fogNameSpaces) {
        if (fogNameSpaces.length == 0) {
            this.appliedFog.clear();
        } else {
            for (String id : fogNameSpaces) {
                this.appliedFog.remove(id);
            }
        }
        PlayerFogPacket packet = new PlayerFogPacket();
        packet.getFogStack().addAll(this.appliedFog);
        sendUpstreamPacket(packet);
    }

    @Override
    public @NonNull Set<String> fogEffects() {
        // Use a copy so that sendFog/removeFog can be called while iterating the returned set (avoid CME)
        return Set.copyOf(this.appliedFog);
    }

    @Override
    public <T> void storePreference(@NonNull PreferenceKey<T> key, @NonNull Preference<T> preference) {
        this.preferencesCache.register(key, preference);
    }

    @Override
    public @NonNull <T> Preference<T> requirePreference(@NonNull PreferenceKey<T> key) throws IllegalArgumentException {
        return this.preferencesCache.require(key);
    }

    @Override
    public @NonNull <T> Optional<Preference<T>> getPreference(@NonNull PreferenceKey<T> key) {
        return this.preferencesCache.get(key);
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
