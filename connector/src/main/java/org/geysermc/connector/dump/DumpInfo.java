/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.dump;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.steveice10.mc.protocol.MinecraftConstants;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.common.serializer.AsteriskSerializer;
import org.geysermc.connector.configuration.GeyserConfiguration;
import org.geysermc.connector.network.BedrockProtocol;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.utils.DockerCheck;
import org.geysermc.connector.utils.FileUtils;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.FloodgateConfigHolder;
import org.geysermc.floodgate.util.FloodgateGitPropertiesHolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

@Getter
public class DumpInfo {
    @JsonIgnore
    private static final long MEGABYTE = 1024L * 1024L;

    private final DumpInfo.VersionInfo versionInfo;
    private Properties gitInfo;
    private final GeyserConfiguration config;
    private final Object floodgateInfo;
    private final Object2IntMap<DeviceOs> userPlatforms;
    private final RamInfo ramInfo;
    private final BootstrapDumpInfo bootstrapInfo;

    public DumpInfo() {
        this.versionInfo = new VersionInfo();

        try {
            this.gitInfo = new Properties();
            this.gitInfo.load(FileUtils.getResource("git.properties"));
        } catch (IOException ignored) {
        }

        this.config = GeyserConnector.getInstance().getConfig();
        this.floodgateInfo = new FloodgateInfo();

        this.userPlatforms = new Object2IntOpenHashMap<>();
        for (GeyserSession session : GeyserConnector.getInstance().getPlayers()) {
            DeviceOs device = session.getClientData().getDeviceOs();
            userPlatforms.put(device, userPlatforms.getOrDefault(device, 0) + 1);
        }

        this.ramInfo = new RamInfo();
        this.bootstrapInfo = GeyserConnector.getInstance().getBootstrap().getDumpInfo();
    }

    @Getter
    public static class VersionInfo {
        private final String name;
        private final String version;
        private final String javaVersion;
        private final String architecture;
        private final String operatingSystem;
        private final String operatingSystemVersion;

        private final NetworkInfo network;
        private final MCInfo mcInfo;

        VersionInfo() {
            this.name = GeyserConnector.NAME;
            this.version = GeyserConnector.VERSION;
            this.javaVersion = System.getProperty("java.version");
            // Usually gives Java architecture but still may be helpful.
            this.architecture = System.getProperty("os.arch");
            this.operatingSystem = System.getProperty("os.name");
            this.operatingSystemVersion = System.getProperty("os.version");

            this.network = new NetworkInfo();
            this.mcInfo = new MCInfo();
        }
    }

    @Getter
    public static class NetworkInfo {
        private final boolean dockerCheck;
        private String internalIP;

        NetworkInfo() {
            if (AsteriskSerializer.showSensitive) {
                try {
                    // This is the most reliable for getting the main local IP
                    Socket socket = new Socket();
                    socket.connect(new InetSocketAddress("geysermc.org", 80));
                    this.internalIP = socket.getLocalAddress().getHostAddress();
                } catch (IOException e1) {
                    try {
                        // Fallback to the normal way of getting the local IP
                        this.internalIP = InetAddress.getLocalHost().getHostAddress();
                    } catch (UnknownHostException ignored) {
                    }
                }
            } else {
                // Sometimes the internal IP is the external IP...
                this.internalIP = "***";
            }

            this.dockerCheck = DockerCheck.checkBasic();
        }
    }

    @Getter
    public static class MCInfo {
        private final String bedrockVersion;
        private final int bedrockProtocol;
        private final String javaVersion;
        private final int javaProtocol;

        MCInfo() {
            this.bedrockVersion = BedrockProtocol.DEFAULT_BEDROCK_CODEC.getMinecraftVersion();
            this.bedrockProtocol = BedrockProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion();
            this.javaVersion = MinecraftConstants.GAME_VERSION;
            this.javaProtocol = MinecraftConstants.PROTOCOL_VERSION;
        }
    }

    @Getter
    public static class FloodgateInfo {
        private final Properties floodgateGitInfo;
        private final Object floodgateConfig;

        FloodgateInfo() {
            this.floodgateGitInfo = FloodgateGitPropertiesHolder.getGitProperties();
            this.floodgateConfig = FloodgateConfigHolder.getConfig();
        }
    }

    @Getter
    public static class RamInfo {
        private final long free;
        private final long total;
        private final long max;

        RamInfo() {
            this.free = Runtime.getRuntime().freeMemory() / MEGABYTE;
            this.total = Runtime.getRuntime().totalMemory() / MEGABYTE;
            this.max = Runtime.getRuntime().maxMemory() / MEGABYTE;
        }
    }
}
