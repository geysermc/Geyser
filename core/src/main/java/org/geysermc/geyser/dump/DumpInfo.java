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

import com.google.common.hash.Hashing;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.geysermc.floodgate.util.DeviceOs;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.network.GameProtocol;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.AsteriskSerializer;
import org.geysermc.geyser.util.CpuUtils;
import org.geysermc.geyser.util.FileUtils;
import org.geysermc.geyser.util.WebUtils;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.interfaces.InterfaceDefaultOptions;
import org.spongepowered.configurate.serialize.SerializationException;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
public class DumpInfo {
    private static final long MEGABYTE = 1024L * 1024L;

    private final DumpInfo.VersionInfo versionInfo;
    private final int cpuCount;
    private final String cpuName;
    private final Locale systemLocale;
    private final String systemEncoding;
    private final GitInfo gitInfo;
    private Object config;
    private Object advancedConfig;
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

        try {
            // Workaround for JsonAdapter not being allowed on methods
            ConfigurationOptions options = InterfaceDefaultOptions.addTo(ConfigurationOptions.defaults(), builder ->
                    builder.addProcessor(AsteriskSerializer.Asterisk.class, String.class, AsteriskSerializer.CONFIGURATE_SERIALIZER))
                .shouldCopyDefaults(false);

            ConfigurationNode configNode = CommentedConfigurationNode.root(options);
            configNode.set(geyser.config());
            this.config = toGson(configNode);

            ConfigurationNode advancedConfigNode = CommentedConfigurationNode.root(options);
            advancedConfigNode.set(geyser.config().advanced());
            this.advancedConfig = toGson(advancedConfigNode);
        } catch (SerializationException e) {
            e.printStackTrace();
            if (geyser.config().debugMode()) {
                e.printStackTrace();
            }
        }

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
            if (geyser.config().debugMode()) {
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

    private JsonElement toGson(ConfigurationNode node) {
        if (node.isMap()) {
            JsonObject object = new JsonObject();
            node.childrenMap().forEach((key, value) -> {
                JsonElement json = toGson(value);
                object.add(key.toString(), json);
            });
            return object;
        } else if (node.isList()) {
            JsonArray array = new JsonArray();
            node.childrenList().forEach(childNode -> array.add(toGson(childNode)));
            return array;
        } else {
            return convertRawScalar(node);
        }
    }

    private JsonElement convertRawScalar(ConfigurationNode node) {
        final @Nullable Object value = node.rawScalar();
        if (value == null) {
            return JsonNull.INSTANCE;
        } else if (value instanceof Number n) {
            return new JsonPrimitive(n);
        } else if (value instanceof Boolean b) {
            return new JsonPrimitive(b);
        } else {
            return new JsonPrimitive(value.toString());
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
                try (Socket socket = new Socket()) {
                    // This is the most reliable for getting the main local IP
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
            this.bedrockVersions = GameProtocol.SUPPORTED_BEDROCK_CODECS.stream().map(BedrockCodec::getMinecraftVersion).toList();
            this.bedrockProtocols = GameProtocol.SUPPORTED_BEDROCK_CODECS.stream().map(BedrockCodec::getProtocolVersion).toList();
            this.defaultBedrockProtocol = GameProtocol.DEFAULT_BEDROCK_CODEC.getProtocolVersion();
            this.javaVersions = GameProtocol.getJavaVersions();
            this.javaProtocol = GameProtocol.getJavaProtocolVersion();
        }
    }

    @Getter
    public static class LogsInfo {
        private String link;

        public LogsInfo(GeyserImpl geyser) {
            try {
                Map<String, String> fields = new HashMap<>();
                fields.put("content", FileUtils.readAllLines(geyser.getBootstrap().getLogsPath()).collect(Collectors.joining("\n")));

                JsonObject logData = new JsonParser().parse(WebUtils.postForm("https://api.mclo.gs/1/log", fields)).getAsJsonObject();

                this.link = logData.get("url").getAsString();
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

    public record GitInfo(String buildNumber, @SerializedName("git.commit.id.abbrev") String commitHashAbbrev, @SerializedName("git.commit.id") String commitHash,
                              @SerializedName("git.branch") String branchName, @SerializedName("git.remote.origin.url") String originUrl) {
    }
}
