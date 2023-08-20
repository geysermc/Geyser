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

package org.geysermc.geyser.configuration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.network.AuthType;
import org.geysermc.geyser.api.network.BedrockListener;
import org.geysermc.geyser.api.network.RemoteServer;
import org.geysermc.geyser.network.CIDRMatcher;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.text.GeyserLocale;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface GeyserConfiguration {
    /**
     * If the config was originally 'auto' before the values changed
     */
    void setAutoconfiguredRemote(boolean autoconfiguredRemote);

    // Modify this when you introduce breaking changes into the config
    int CURRENT_CONFIG_VERSION = 4;

    IBedrockConfiguration getBedrock();

    IRemoteConfiguration getRemote();

    List<String> getSavedUserLogins();

    @Deprecated
    Map<String, ? extends IUserAuthenticationInfo> getUserAuths();

    boolean isCommandSuggestions();

    @JsonIgnore
    boolean isPassthroughMotd();

    @JsonIgnore
    boolean isPassthroughProtocolName();

    @JsonIgnore
    boolean isPassthroughPlayerCounts();

    @JsonIgnore
    boolean isLegacyPingPassthrough();

    int getPingPassthroughInterval();

    boolean isForwardPlayerPing();

    int getMaxPlayers();

    boolean isDebugMode();

    boolean isAllowThirdPartyCapes();

    boolean isAllowThirdPartyEars();

    CooldownType getShowCooldown();

    boolean isShowCoordinates();

    boolean isDisableBedrockScaffolding();

    EmoteOffhandWorkaroundOption getEmoteOffhandWorkaround();

    String getDefaultLocale();

    Path getFloodgateKeyPath();

    boolean isAddNonBedrockItems();

    boolean isAboveBedrockNetherBuilding();

    boolean isForceResourcePacks();

    boolean isXboxAchievementsEnabled();

    int getCacheImages();

    boolean isAllowCustomSkulls();

    int getMaxVisibleCustomSkulls();

    int getCustomSkullRenderDistance();

    boolean isLogPlayerIpAddresses();

    boolean isNotifyOnNewBedrockUpdate();

    String getUnusableSpaceBlock();

    IMetricsInfo getMetrics();

    int getPendingAuthenticationTimeout();

    boolean isAutoconfiguredRemote();

    interface IBedrockConfiguration extends BedrockListener {
        void setAddress(String address);

        void setPort(int port);

        boolean isCloneRemotePort();

        int getCompressionLevel();

        boolean isEnableProxyProtocol();

        List<String> getProxyProtocolWhitelistedIPs();

        /**
         * @return Unmodifiable list of {@link CIDRMatcher}s from {@link #getProxyProtocolWhitelistedIPs()}
         */
        List<CIDRMatcher> getWhitelistedIPsMatchers();
    }

    interface IRemoteConfiguration extends RemoteServer {

        void setAddress(String address);

        void setPort(int port);

        boolean isPasswordAuthentication();

        boolean isUseProxyProtocol();

        boolean isForwardHost();

        default String minecraftVersion() {
            return GameProtocol.getJavaMinecraftVersion();
        }

        default int protocolVersion() {
            return GameProtocol.getJavaProtocolVersion();
        }

        void setAuthType(AuthType authType);
    }

    interface IUserAuthenticationInfo {
        String getEmail();

        String getPassword();

        /**
         * Will be removed after Microsoft accounts are fully migrated
         */
        @Deprecated
        boolean isMicrosoftAccount();
    }

    interface IMetricsInfo {

        boolean isEnabled();

        String getUniqueId();
    }

    int getScoreboardPacketThreshold();

    // if u have offline mode enabled pls be safe
    boolean isEnableProxyConnections();

    int getMtu();

    boolean isUseDirectConnection();

    boolean isDisableCompression();

    int getConfigVersion();

    static void checkGeyserConfiguration(GeyserConfiguration geyserConfig, GeyserLogger geyserLogger) {
        if (geyserConfig.getConfigVersion() < CURRENT_CONFIG_VERSION) {
            geyserLogger.warning(GeyserLocale.getLocaleStringLog("geyser.bootstrap.config.outdated"));
        } else if (geyserConfig.getConfigVersion() > CURRENT_CONFIG_VERSION) {
            geyserLogger.warning(GeyserLocale.getLocaleStringLog("geyser.bootstrap.config.too_new"));
        }
    }
}
