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

package org.geysermc.geyser.dump;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.floodgate.util.FloodgateInfoHolder;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.util.MinecraftVersion;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.AsteriskSerializer;
import org.geysermc.geyser.util.CpuUtils;
import org.geysermc.geyser.util.FileUtils;
import org.geysermc.geyser.util.WebUtils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@Getter
public class DumpInfo {
    @JsonIgnore
    private static final long MEGABYTE = 1024L * 1024L;

    private final DumpInfo.VersionInfo versionInfo;
    private final int cpuCount;
    private final String cpuName;
    private final Locale systemLocale;
    private final String systemEncoding;
    private final GitInfo gitInfo;
    private final GeyserConfiguration config;
    private final Floodgate floodgate;
    private final Object2IntMap<DeviceOs> userPlatforms;
    private final int connectionAttempts;
    private final HashInfo hashInfo;
    private final RamInfo ramInfo;
    private LogsInfo logsInfo;
    private final BootstrapDumpInfo bootstrapInfo;
    private final FlagsInfo flagsInfo;
    private final List<ExtensionInfo> extensionInfo;

    public DumpInfo(GeyserImpl geyser, boolean addLog) {
        this.versionInfo = new VersionInfo();

        this.cpuCount = Runtime.getRuntime().availableProcessors();
        this.cpuName = CpuUtils.tryGetProcessorName();
        this.systemLocale = Locale.getDefault();
        this.systemEncoding = System.getProperty("file.encoding");

        this.gitInfo = new GitInfo(GeyserImpl.BUILD_NUMBER, GeyserImpl.COMMIT.substring(0, 7), GeyserImpl.COMMIT, GeyserImpl.BRANCH, GeyserImpl.REPOSITORY);

        this.config = geyser.getConfig();
        this.floodgate = new Floodgate();

        String md5Hash = "unknown";
        String sha256Hash = "unknown";
        try {
            // https://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
            // https://stackoverflow.com/questions/304268/getting-a-files-md5-checksum-in-java
            File file = new File(DumpInfo.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            ByteSource byteSource = Files.asByteSource(file);
            // Jenkins uses MD5 for its hash - TODO remove
            //noinspection UnstableApiUsage,deprecation
            md5Hash = byteSource.hash(Hashing.md5()).toString();
            //noinspection UnstableApiUsage
            sha256Hash = byteSource.hash(Hashing.sha256()).toString();
        } catch (Exception e) {
            if (this.config.isDebugMode()) {
                e.printStackTrace();
            }
        }
        this.hashInfo = new HashInfo(md5Hash, sha256Hash);

        this.ramInfo = new RamInfo();

        if (addLog) {
            this.logsInfo = new LogsInfo(geyser);
        }

        this.userPlatforms = new Object2IntOpenHashMap<>();
        for (GeyserSession session : geyser.getSessionManager().getAllSessions()) {
            DeviceOs device = session.getClientData().getDeviceOs();
            userPlatforms.put(device, userPlatforms.getOrDefault(device, 0) + 1);
        }

        if (geyser.getGeyserServer() != null) {
            this.connectionAttempts = geyser.getGeyserServer().getConnectionAttempts();
        } else {
            this.connectionAttempts = 0; // Fallback if Geyser failed to fully startup
        }

        this.bootstrapInfo = geyser.getBootstrap().getDumpInfo();

        this.flagsInfo = new FlagsInfo();

        this.extensionInfo = new ArrayList<>();
        for (Extension extension : GeyserApi.api().extensionManager().extensions()) {
            this.extensionInfo.add(new ExtensionInfo(extension.isEnabled(), extension.name(), extension.description().version(), extension.description().apiVersion(), extension.description().main(), extension.description().authors()));
        }
    }

    @Getter
    public static class VersionInfo {
        private final String name;
        private final String version;
        private final String javaName;
        private final String javaVendor;
        private final String javaVersion;
        private final String architecture;
        private final String operatingSystem;
        private final String operatingSystemVersion;

        private final NetworkInfo network;
        private final MCInfo mcInfo;

        VersionInfo() {
            this.name = GeyserImpl.NAME;
            this.version = GeyserImpl.VERSION;
            this.javaName = System.getProperty("java.vm.name");
            this.javaVendor = System.getProperty("java.vendor");
            this.javaVersion = ManagementFactory.getRuntimeMXBean().getVmVersion(); // Gives a little more to the version we can use over the system property
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

            this.dockerCheck = checkDockerBasic();
        }

        // By default, Geyser now sets the IP to the local IP in all cases on plugin versions so we don't notify the user of anything
        // However we still have this check for the potential future bug
        private boolean checkDockerBasic() {
            try {
                String OS = System.getProperty("os.name").toLowerCase();
                if (OS.contains("nix") || OS.contains("nux") || OS.indexOf("aix") > 0) {
                    String output = new String(java.nio.file.Files.readAllBytes(Paths.get("/proc/1/cgroup")));

                    if (output.contains("docker")) {
                        return true;
                    }
                }
            } catch (Exception ignored) { } // Ignore any errors, inc ip failed to fetch, process could not run or access denied

            return false;
        }
    }

    @Getter
    public static class MCInfo {
        private final List<String> bedrockVersions;
        private final List<Integer> bedrockProtocols;
        private final int defaultBedrockProtocol;
        private final List<String> javaVersions;
        private final int javaProtocol;

        MCInfo() {
            this.bedrockVersions = GameProtocol.SUPPORTED_BEDROCK_VERSIONS.stream().map(MinecraftVersion::versionString).toList();
            this.bedrockProtocols = GameProtocol.SUPPORTED_BEDROCK_PROTOCOLS;
            this.defaultBedrockProtocol = GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion();
            this.javaVersions = GameProtocol.getJavaVersions();
            this.javaProtocol = GameProtocol.getJavaProtocolVersion();
        }
    }

    @Getter
    public static class Floodgate {
        private final Properties gitInfo;
        private final Object config;

        Floodgate() {
            this.gitInfo = FloodgateInfoHolder.getGitProperties();
            this.config = FloodgateInfoHolder.getConfig();
        }
    }

    @Getter
    public static class LogsInfo {
        private String link;

        public LogsInfo(GeyserImpl geyser) {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("content", FileUtils.readAllLines(geyser.getBootstrap().getLogsPath()).collect(Collectors.joining("\n")));

                JsonNode logData = GeyserImpl.JSON_MAPPER.readTree(WebUtils.postForm("https://api.mclo.gs/1/log", fields));

                this.link = logData.get("url").textValue();
            } catch (IOException ignored) { }
        }
    }

    public record HashInfo(String md5Hash, String sha256Hash) {
    }

    public record RamInfo(long free, long total, long max) {
        public RamInfo() {
            this(Runtime.getRuntime().freeMemory() / MEGABYTE,
                    Runtime.getRuntime().totalMemory() / MEGABYTE,
                    Runtime.getRuntime().maxMemory() / MEGABYTE);
        }
    }

    /**
     * E.G. `-Xmx1024M` - all runtime JVM flags on this machine
     */
    public record FlagsInfo(List<String> flags) {
        public FlagsInfo() {
            this(ManagementFactory.getRuntimeMXBean().getInputArguments());
        }
    }

    public record ExtensionInfo(boolean enabled, String name, String version, String apiVersion, String main, List<String> authors) {
    }

    public record GitInfo(String buildNumber, @JsonProperty("git.commit.id.abbrev") String commitHashAbbrev, @JsonProperty("git.commit.id") String commitHash,
                              @JsonProperty("git.branch") String branchName, @JsonProperty("git.remote.origin.url") String originUrl) {
    }
}
