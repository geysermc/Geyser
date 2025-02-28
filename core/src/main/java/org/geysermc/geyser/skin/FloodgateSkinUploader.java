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

package org.geysermc.geyser.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.floodgate.util.WebsocketEventType;
import org.geysermc.geyser.Constants;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.util.JsonUtils;
import org.geysermc.geyser.util.PluginMessageUtils;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class FloodgateSkinUploader {
    private final List<String> skinQueue = new ArrayList<>();

    private final GeyserLogger logger;
    private final WebSocketClient client;
    private volatile boolean closed;

    @Getter private int id;
    @Getter private String verifyCode;
    @Getter private int subscribersCount;

    public FloodgateSkinUploader(GeyserImpl geyser) {
        this.logger = geyser.getLogger();
        this.client = new WebSocketClient(Constants.GLOBAL_API_WS_URI) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                setConnectionLostTimeout(11);

                Iterator<String> queueIterator = skinQueue.iterator();
                while (isOpen() && queueIterator.hasNext()) {
                    send(queueIterator.next());
                    queueIterator.remove();
                }
            }

            @Override
            public void onMessage(String message) {
                try {
                    JsonObject node = JsonUtils.parseJson(message);
                    if (node.has("error")) {
                        logger.error("Got an error: " + node.get("error").getAsString());
                        return;
                    }

                    int typeId = node.get("event_id").getAsInt();
                    WebsocketEventType type = WebsocketEventType.fromId(typeId);
                    if (type == null) {
                        logger.warning(String.format(
                                "Got (unknown) type %s. Ensure that Geyser is on the latest version and report this issue!",
                                typeId));
                        return;
                    }

                    switch (type) {
                        case SUBSCRIBER_CREATED:
                            id = node.get("id").getAsInt();
                            verifyCode = node.get("verify_code").getAsString();
                            break;
                        case SUBSCRIBER_COUNT:
                            subscribersCount = node.get("subscribers_count").getAsInt();
                            break;
                        case SKIN_UPLOADED:
                            // if Geyser is the only subscriber we have send it to the server manually
                            // otherwise it's handled by the Floodgate plugin subscribers
                            if (subscribersCount != 1) {
                                break;
                            }

                            String xuid = node.get("xuid").getAsString();
                            GeyserSession session = geyser.connectionByXuid(xuid);

                            if (session != null) {
                                if (!node.get("success").getAsBoolean()) {
                                    logger.info("Failed to upload skin for " + session.bedrockUsername());
                                    return;
                                }

                                JsonObject data = node.getAsJsonObject("data");

                                String value = data.get("value").getAsString();
                                String signature = data.get("signature").getAsString();

                                byte[] bytes = (value + '\0' + signature)
                                        .getBytes(StandardCharsets.UTF_8);
                                PluginMessageUtils.sendMessage(session, PluginMessageChannels.SKIN, bytes);
                            }
                            break;
                        case LOG_MESSAGE:
                            String logMessage = node.get("message").getAsString();
                            switch (node.get("priority").getAsInt()) {
                                case -1 -> logger.debug("Got a message from skin uploader: " + logMessage);
                                case 0 -> logger.info("Got a message from skin uploader: " + logMessage);
                                case 1 -> logger.error("Got a message from skin uploader: " + logMessage);
                                default -> logger.info(logMessage);
                            }
                            break;
                        case NEWS_ADDED:
                            //todo
                    }
                } catch (Exception e) {
                    logger.error("Error while receiving a message", e);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (reason != null && !reason.isEmpty()) {
                    try {
                        JsonObject node = JsonUtils.parseJson(reason);
                        // info means that the uploader itself did nothing wrong
                        if (node.has("info")) {
                            String info = node.get("info").getAsString();
                            logger.debug("Got disconnected from the skin uploader: " + info);
                        }
                        // error means that the uploader did something wrong
                        if (node.has("error")) {
                            String error = node.get("error").getAsString();
                            logger.info("Got disconnected from the skin uploader: " + error);
                        }
                    } catch (JsonSyntaxException ignored) {
                        // ignore invalid json
                    } catch (Exception e) {
                        logger.error("Error while handling onClose", e);
                    }
                }
                // try to reconnect (which will make a new id and verify token) after a few seconds
                reconnectLater(geyser);
            }

            @Override
            public void onError(Exception ex) {
                if (ex instanceof UnknownHostException) {
                    logger.error("Unable to resolve the skin api! This can be caused by your connection or the skin api being unreachable. " + ex.getMessage());
                    return;
                }
                if (ex instanceof ConnectException || ex instanceof SSLException) {
                    if (logger.isDebug()) {
                        logger.error("[debug] Got an error", ex);
                    }
                    return;
                }
                logger.error("Got an error", ex);
            }
        };
    }

    public void uploadSkin(List<String> chainData, String clientData) {
        if (chainData == null || clientData == null) {
            return;
        }

        JsonObject node = new JsonObject();
        JsonArray chainDataNode = new JsonArray();
        chainData.forEach(chainDataNode::add);
        node.add("chain_data", chainDataNode);
        node.addProperty("client_data", clientData);

        String jsonString = node.toString();

        if (client.isOpen()) {
            client.send(jsonString);
            return;
        }
        skinQueue.add(jsonString);
    }

    private void reconnectLater(GeyserImpl geyser) {
        // we ca only reconnect when the thread pool is open
        if (geyser.getScheduledThread().isShutdown() || closed) {
            logger.info("The skin uploader has been closed");
            return;
        }

        long additionalTime = ThreadLocalRandom.current().nextInt(7);
        // we don't have to check the result. onClose will handle that for us
        geyser.getScheduledThread()
                .schedule(client::reconnect, 8 + additionalTime, TimeUnit.SECONDS);
    }

    public FloodgateSkinUploader start() {
        client.connect();
        return this;
    }

    public void close() {
        if (!closed) {
            closed = true;
            client.close();
        }
    }
}
