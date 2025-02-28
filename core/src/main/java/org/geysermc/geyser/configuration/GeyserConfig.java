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

package org.geysermc.geyser.configuration;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.BedrockListener;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.AsteriskSerializer;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.CooldownUtils;
import org.spongepowered.configurate.interfaces.meta.Exclude;
import org.spongepowered.configurate.interfaces.meta.Field;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultBoolean;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultNumeric;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultString;
import org.spongepowered.configurate.interfaces.meta.range.NumericRange;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

import java.util.Collections;
import java.util.List;

@ConfigSerializable
public interface GeyserConfig {
    BedrockConfig bedrock();

    JavaConfig java();

    @Comment("""
            For online mode authentication type only.
            Stores a list of Bedrock player usernames that should have their Java Edition account saved after login.
            This saves a token that can be reused to authenticate the player later. This does not save emails or passwords,
            but you should still be cautious when adding to this list and giving others access to this Geyser instance's files.
            Removing a name from this list will delete its cached login information on the next Geyser startup.
            The file that tokens will be saved in is in the same folder as this config, named "saved-refresh-tokens.json".""")
    default List<String> savedUserLogins() {
        return List.of("ThisExampleUsernameShouldBeLongEnoughToNeverBeAnXboxUsername",
                "ThisOtherExampleUsernameShouldAlsoBeLongEnough");
    }

    @Comment("""
            Specify how many seconds to wait while user authorizes Geyser to access their Microsoft account.
            User is allowed to disconnect from the server during this period.""")
    @DefaultNumeric(128)
    int pendingAuthenticationTimeout();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @Comment("""
            Bedrock clients can freeze when opening up the command prompt for the first time if given a lot of commands.
            Disabling this will prevent command suggestions from being sent and solve freezing for Bedrock clients.""")
    @DefaultBoolean(true)
    boolean commandSuggestions();

    @Comment("Relay the MOTD from the Java server to Bedrock players.")
    @DefaultBoolean(true)
    boolean passthroughMotd();

    @Comment("Relay the player count and max players from the Java server to Bedrock players.")
    @DefaultBoolean(true)
    boolean passthroughPlayerCounts();

    @Comment("""
            Use server API methods to determine the Java server's MOTD and ping passthrough.
            There is no need to disable this unless your MOTD or player count does not appear properly.""")
    @DefaultBoolean(true)
    @PluginSpecific
    boolean integratedPingPassthrough();

    @Comment("How often to ping the Java server to refresh MOTD and player count, in seconds.")
    @DefaultNumeric(3)
    int pingPassthroughInterval();

    @Comment("""
            Whether to forward player ping to the server. While enabling this will allow Bedrock players to have more accurate
            ping, it may also cause players to time out more easily.""")
    boolean forwardPlayerPing();

    @Comment("""
            Maximum amount of players that can connect.
            This is only visual, and is only applied if passthrough-motd is disabled.""")
    @DefaultNumeric(100)
    int maxPlayers();

    @Comment("If debug messages should be sent through console")
    boolean debugMode();

    @Comment("""
            Allow a fake cooldown indicator to be sent. Bedrock players otherwise do not see a cooldown as they still use 1.8 combat.
            Please note: if the cooldown is enabled, some users may see a black box during the cooldown sequence, like below:
            https://geysermc.org/img/external/cooldown_indicator.png
            This can be disabled by going into Bedrock settings under the accessibility tab and setting "Text Background Opacity" to 0
            This setting can be set to "title", "actionbar" or "false\"""")
    default CooldownUtils.CooldownType showCooldown() {
        return CooldownUtils.CooldownType.TITLE;
    }

    @Comment("Controls if coordinates are shown to players.")
    @DefaultBoolean(true)
    boolean showCoordinates();

    @Comment("Whether Bedrock players are blocked from performing their scaffolding-style bridging.")
    boolean disableBedrockScaffolding();

    @Comment("The default locale if we don't have the one the client requested. If set to \"system\", the system's language will be used.")
    @NonNull
    @DefaultString(GeyserLocale.SYSTEM_LOCALE)
    String defaultLocale();

    @Comment("Allows custom skulls to be displayed. Keeping them enabled may cause a performance decrease on older/weaker devices.")
    @DefaultBoolean(true)
    boolean allowCustomSkulls();

    @Comment("""
            Whether to add any items and blocks which normally does not exist in Bedrock Edition.
            This should only need to be disabled if using a proxy that does not use the "transfer packet" style of server switching.
            If this is disabled, furnace minecart items will be mapped to hopper minecart items.
            Geyser's block, item, and skull mappings systems will also be disabled.
            This option requires a restart of Geyser in order to change its setting.""")
    @DefaultBoolean(true)
    boolean addNonBedrockItems();

    @Comment("""
            Bedrock prevents building and displaying blocks above Y127 in the Nether.
            This config option works around that by changing the Nether dimension ID to the End ID.
            The main downside to this is that the entire Nether will have the same red fog rather than having different fog for each biome.""")
    boolean aboveBedrockNetherBuilding();

    @Comment("""
            Force clients to load all resource packs if there are any.
            If set to false, it allows the user to connect to the server even if they don't
            want to download the resource packs.""")
    @DefaultBoolean(true)
    boolean forceResourcePacks();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    @Comment("""
            Allows Xbox achievements to be unlocked.
            If a player types in an unknown command, they will receive a message that states cheats are disabled.
            Otherwise, commands work as expected.""")
    boolean xboxAchievementsEnabled();

    @Comment("Whether player IP addresses will be logged by the server.")
    @DefaultBoolean(true)
    boolean logPlayerIpAddresses();

    @Comment("""
            Whether to alert the console and operators that a new Geyser version is available that supports a Bedrock version
            that this Geyser version does not support. It's recommended to keep this option enabled, as many Bedrock platforms
            auto-update.""")
    @DefaultBoolean(true)
    boolean notifyOnNewBedrockUpdate();

    @Comment("""
            bStats is a stat tracker that is entirely anonymous and tracks only basic information
            about Geyser, such as how many people are online, how many servers are using Geyser,
            what OS is being used, etc. You can learn more about bStats here: https://bstats.org/.
            https://bstats.org/plugin/server-implementation/GeyserMC""")
    @DefaultBoolean(true)
    @ExcludePlatform(platforms = {"BungeeCord", "Spigot", "Velocity"}) // bStats platform versions used
    boolean enableMetrics();

    /**
     * A separate config file added to this class manually.
     */
    @Field
    @NonNull
    AdvancedConfig advanced();

    @Field
    void advanced(AdvancedConfig config);

    @ConfigSerializable
    interface BedrockConfig extends BedrockListener {
        @Override
        @Comment("""
                The IP address that will listen for connections.
                Generally, you should only uncomment and change this if you want to limit what IPs can connect to your server.""")
        @NonNull
        @DefaultString("0.0.0.0")
        @AsteriskSerializer.Asterisk
        String address();

        @Override
        @Comment("The port that will listen for connections")
        @DefaultNumeric(19132)
        @NumericRange(from = 0, to = 65535)
        int port();

        @Override
        @Comment("""
                The port to broadcast to Bedrock clients with the MOTD that they should use to connect to the server.
                DO NOT change this unless Geyser runs on a different internal port than the one that is used to connect.""")
        @DefaultNumeric(0)
        @NumericRange(from = 0, to = 65535)
        int broadcastPort();

        @Comment("""
                Some hosting services change your Java port everytime you start the server and require the same port to be used for Bedrock.
                This option makes the Bedrock port the same as the Java port every time you start the server.""")
        @DefaultBoolean
        @PluginSpecific
        boolean cloneRemotePort();

        void address(String address);

        void port(int port);

        void broadcastPort(int broadcastPort);

        @Override
        @Comment("""
            The MOTD that will be broadcasted to Minecraft: Bedrock Edition clients. This is irrelevant if "passthrough-motd" is set to true.
            If either of these are empty, the respective string will default to "Geyser\"""")
        @DefaultString("Geyser")
        String primaryMotd();

        @Override
        @DefaultString("Another Geyser server.")
        String secondaryMotd();

        @Override
        @Comment("The Server Name that will be sent to Minecraft: Bedrock Edition clients. This is visible in both the pause menu and the settings menu.")
        @DefaultString("Geyser")
        String serverName();

        @Comment("""
                How much to compress network traffic to the Bedrock client. The higher the number, the more CPU usage used, but
                the smaller the bandwidth used. Does not have any effect below -1 or above 9. Set to -1 to disable.""")
        @DefaultNumeric(6)
        @NumericRange(from = -1, to = 9)
        int compressionLevel();

        @Comment("""
                Whether to enable PROXY protocol or not for clients. You DO NOT WANT this feature unless you run UDP reverse proxy
                in front of your Geyser instance.""")
        @DefaultBoolean
        boolean enableProxyProtocol();

        @Comment("""
                A list of allowed PROXY protocol speaking proxy IP addresses/subnets. Only effective when "enable-proxy-protocol" is enabled, and
                should really only be used when you are not able to use a proper firewall (usually true with shared hosting providers etc.).
                Keeping this list empty means there is no IP address whitelist.
                IP addresses, subnets, and links to plain text files are supported.""")
        default List<String> proxyProtocolWhitelistedIps() {
            return Collections.emptyList();
        }
    }

    @ConfigSerializable
    interface JavaConfig extends RemoteServer {

        void address(String address);

        void port(int port);

        @Override
        @Comment("""
                What type of authentication Bedrock players will be checked against when logging into the Java server.
                Can be "floodgate" (see https://wiki.geysermc.org/floodgate/), "online", or "offline".""")
        @NonNull
        default AuthType authType() {
            return AuthType.ONLINE;
        }

        void authType(AuthType authType);

        @Comment("""
                Whether to enable PROXY protocol or not while connecting to the server.
                This is useful only when:
                1) Your server supports PROXY protocol (it probably doesn't)
                2) You run Velocity or BungeeCord with the option enabled in the proxy's main config.
                IF YOU DON'T KNOW WHAT THIS IS, DON'T TOUCH IT!""")
        boolean useProxyProtocol();

        boolean forwardHostname();

        @Override
        @Exclude
        default String minecraftVersion() {
            return GameProtocol.getJavaMinecraftVersion();
        }

        @Override
        @Exclude
        default int protocolVersion() {
            return GameProtocol.getJavaProtocolVersion();
        }

        @Override
        @Exclude
        default boolean resolveSrv() {
            return false;
        }
    }

    @Comment("""
            Allow connections from ProxyPass and Waterdog.
            See https://www.spigotmc.org/wiki/firewall-guide/ for assistance - use UDP instead of TCP.""")
    // if u have offline mode enabled pls be safe
    boolean enableProxyConnections();

    @Comment("Do not change!")
    @SuppressWarnings("unused")
    default int configVersion() {
        return Constants.CONFIG_VERSION;
    }
}
