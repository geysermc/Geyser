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

package org.geysermc.geyser.command.defaults;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.util.LoopbackUtil;
import org.geysermc.geyser.util.WebUtils;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

import static org.incendo.cloud.parser.standard.IntegerParser.integerParser;
import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public class ConnectionTestCommand extends GeyserCommand {

    /*
     * The MOTD is temporarily changed during the connection test.
     * This allows us to check if we are pinging the correct Geyser instance
     */
    public static String CONNECTION_TEST_MOTD = null;

    private static final String ADDRESS = "address";
    private static final String PORT = "port";

    private final GeyserImpl geyser;
    private final Random random = new Random();

    public ConnectionTestCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void register(CommandManager<GeyserCommandSource> manager) {
        manager.command(baseBuilder(manager)
            .required(ADDRESS, stringParser())
            .optional(PORT, integerParser(0, 65535))
            .handler(this::execute));
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        GeyserCommandSource source = context.sender();
        String ipArgument = context.get(ADDRESS);
        Integer portArgument = context.getOrDefault(PORT, null); // null if port was not specified

        // Replace "<" and ">" symbols if they are present to avoid the common issue of people including them
        final String ip = ipArgument.replace("<", "").replace(">", "");
        final int port = portArgument != null ? portArgument : geyser.config().bedrock().broadcastPort(); // default bedrock port

        // Issue: people commonly checking placeholders
        if (ip.equals("ip")) {
            source.sendMessage(ip + " is not a valid IP, and instead a placeholder. Please specify the IP to check.");
            return;
        }

        // Issue: checking 0.0.0.0 won't work
        if (ip.equals("0.0.0.0")) {
            source.sendMessage("Please specify the IP that you would connect with. 0.0.0.0 in the config tells Geyser to the listen on the server's IPv4.");
            return;
        }

        // Issue: people testing local ip
        if (ip.equals("localhost") || ip.startsWith("127.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            source.sendMessage("This tool checks if connections from other networks are possible, so you cannot check a local IP.");
            return;
        }

        // Issue: port out of bounds
        if (port <= 0 || port >= 65535) {
            source.sendMessage("The port you specified is invalid! Please specify a valid port.");
            return;
        }

        GeyserConfig config = geyser.config();

        // Issue: do the ports not line up? We only check this if players don't override the broadcast port - if they do, they (hopefully) know what they're doing
        if (config.bedrock().broadcastPort() == config.bedrock().port()) {
            if (port != config.bedrock().port()) {
                if (portArgument != null) {
                    source.sendMessage("The port you are testing with (" + port + ") is not the same as you set in your Geyser configuration ("
                            + config.bedrock().port() + ")");
                    source.sendMessage("Re-run the command with the port in the config, or change the `bedrock` `port` in the config.");
                    if (config.bedrock().cloneRemotePort()) {
                        source.sendMessage("You have `clone-remote-port` enabled. This option ignores the `bedrock` `port` in the config, and uses the Java server port instead.");
                    }
                } else {
                    source.sendMessage("You did not specify the port to check (add it with \":<port>\"), " +
                            "and the default port 19132 does not match the port in your Geyser configuration ("
                            + config.bedrock().port() + ")!");
                    source.sendMessage("Re-run the command with that port, or change the port in the config under `bedrock` `port`.");
                }
            }
        } else {
            if (config.bedrock().broadcastPort() != port) {
                source.sendMessage("The port you are testing with (" + port + ") is not the same as the broadcast port set in your Geyser configuration ("
                        + config.bedrock().broadcastPort() + "). ");
                source.sendMessage("You ONLY need to change the broadcast port if clients connects with a port different from the port Geyser is running on.");
                source.sendMessage("Re-run the command with the port in the config, or change the `bedrock` `broadcast-port` in the config.");
            }
        }

        // Issue: is the `bedrock` `address` in the config different?
        if (!config.bedrock().address().equals("0.0.0.0")) {
            source.sendMessage("The address specified in `bedrock` `address` is not \"0.0.0.0\" - this may cause issues unless this is deliberate and intentional.");
        }

        // Issue: did someone turn on enable-proxy-protocol, and they didn't mean it?
        if (config.bedrock().enableProxyProtocol()) {
            source.sendMessage("You have the `enable-proxy-protocol` setting enabled. " +
                    "Unless you're deliberately using additional software that REQUIRES this setting, you may not need it enabled.");
        }

        CompletableFuture.runAsync(() -> {
            try {
                // Issue: SRV record?
                String[] record = WebUtils.findSrvRecord(geyser, ip);
                if (record != null && !ip.equals(record[3]) && !record[2].equals(String.valueOf(port))) {
                    source.sendMessage("Bedrock Edition does not support SRV records. Try connecting to your server using the address " + record[3] + " and the port " + record[2]
                            + ". If that fails, re-run this command with that address and port.");
                    return;
                }

                // Issue: does Loopback need applying?
                if (LoopbackUtil.needsLoopback(GeyserImpl.getInstance().getLogger())) {
                    source.sendMessage("Loopback is not applied on this computer! You will have issues connecting from the same computer. " +
                            "See here for steps on how to resolve: " + "https://wiki.geysermc.org/geyser/fixing-unable-to-connect-to-world/#using-geyser-on-the-same-computer");
                }

                // Generate some random, unique bits that another server wouldn't provide
                byte[] randomBytes = new byte[2];
                this.random.nextBytes(randomBytes);
                StringBuilder randomStr = new StringBuilder();
                for (byte b : randomBytes) {
                    randomStr.append(Integer.toHexString(b));
                }
                String connectionTestMotd = "Geyser Connection Test " + randomStr;
                CONNECTION_TEST_MOTD = connectionTestMotd;

                source.sendMessage("Testing server connection to " + ip + " with port: " + port + " now. Please wait...");
                JsonObject output;
                try {
                    String hostname = URLEncoder.encode(ip, StandardCharsets.UTF_8);
                    output = WebUtils.getJson("https://checker.geysermc.org/ping?hostname=" + hostname + "&port=" + port);
                } finally {
                    CONNECTION_TEST_MOTD = null;
                }

                if (output.get("success").getAsBoolean()) {
                    JsonObject cache = output.getAsJsonObject("cache");
                    String when;
                    if (cache.get("fromCache").isJsonPrimitive()) {
                        when = cache.get("secondsSince").getAsBoolean() + " seconds ago";
                    } else {
                        when = "now";
                    }

                    JsonObject ping = output.getAsJsonObject("ping");
                    JsonObject pong = ping.getAsJsonObject("pong");
                    String remoteMotd = pong.get("motd").getAsString();
                    if (!connectionTestMotd.equals(remoteMotd)) {
                        source.sendMessage("The MOTD did not match when we pinged the server (we got '" + remoteMotd + "'). " +
                                "Did you supply the correct IP and port of your server?");
                        sendLinks(source);
                        return;
                    }

                    if (ping.get("tcpFirst").getAsBoolean()) {
                        source.sendMessage("Your server hardware likely has some sort of firewall preventing people from joining easily. See https://geysermc.link/ovh-firewall for more information.");
                        sendLinks(source);
                        return;
                    }

                    source.sendMessage("Your server is likely online and working as of " + when + "!");
                    sendLinks(source);
                    return;
                }

                source.sendMessage("Your server is likely unreachable from outside the network!");
                JsonElement message = output.get("message");
                if (message != null && !message.getAsString().isEmpty()) {
                    source.sendMessage("Got the error message: " + message.getAsString());
                }
                sendLinks(source);
            } catch (Exception e) {
                source.sendMessage("An error occurred while trying to check your connection! Check the console for more information.");
                geyser.getLogger().error("Error while trying to check your connection!", e);
            }
        });
    }

    private void sendLinks(GeyserCommandSource sender) {
        sender.sendMessage("If you still face issues, check the setup guide for instructions: " +
                "https://wiki.geysermc.org/geyser/setup/");
        sender.sendMessage("If that does not work, see " + "https://wiki.geysermc.org/geyser/fixing-unable-to-connect-to-world/" + ", or contact us on Discord: " + "https://discord.gg/geysermc");
    }
}
