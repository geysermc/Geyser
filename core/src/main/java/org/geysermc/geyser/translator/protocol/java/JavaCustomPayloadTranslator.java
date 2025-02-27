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

package org.geysermc.geyser.translator.protocol.java;

import com.google.common.base.Charsets;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;
import org.cloudburstmc.protocol.bedrock.packet.UnknownPacket;
import org.geysermc.cumulus.Forms;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.util.FormType;
import org.geysermc.erosion.Constants;
import org.geysermc.erosion.packet.ErosionPacket;
import org.geysermc.erosion.packet.Packets;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPacket;
import org.geysermc.floodgate.pluginmessage.PluginMessageChannels;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomPayloadPacket;

import java.nio.charset.StandardCharsets;

@Translator(packet = ClientboundCustomPayloadPacket.class)
public class JavaCustomPayloadTranslator extends PacketTranslator<ClientboundCustomPayloadPacket> {
    private final GeyserLogger logger = GeyserLogger.get();

    @Override
    public void translate(GeyserSession session, ClientboundCustomPayloadPacket packet) {
        String channel = packet.getChannel().asString();

        if (channel.equals(Constants.PLUGIN_MESSAGE)) {
            ByteBuf buf = Unpooled.wrappedBuffer(packet.getData());
            ErosionPacket<?> erosionPacket = Packets.decode(buf);
            ((GeyserboundPacket) erosionPacket).handle(session.getErosionHandler());
            return;
        }

        if (channel.equals(PluginMessageChannels.FORM)) {
            session.ensureInEventLoop(() -> {
                byte[] data = packet.getData();

                // If the data is empty, we just need to close the form
                if (data.length == 0) {
                    session.closeForm();
                    return;
                }

                // receive: first byte is form type, second and third are the id, remaining is the form data
                // respond: first and second byte id, remaining is form response data

                FormType type = FormType.fromOrdinal(data[0]);
                if (type == null) {
                    throw new NullPointerException("Got type " + data[0] + " which isn't a valid form type!");
                }

                String dataString = new String(data, 3, data.length - 3, Charsets.UTF_8);

                Form form = Forms.fromJson(dataString, type, (ignored, response) -> {
                    byte[] finalData;
                    if (response == null) {
                        // Response data can be null as of 1.19.20 (same behaviour as empty response data)
                        // Only need to send the form id
                        finalData = new byte[]{data[1], data[2]};
                    } else {
                        byte[] raw = response.getBytes(StandardCharsets.UTF_8);
                        finalData = new byte[raw.length + 2];

                        finalData[0] = data[1];
                        finalData[1] = data[2];
                        System.arraycopy(raw, 0, finalData, 2, raw.length);
                    }

                    session.sendDownstreamPacket(new ServerboundCustomPayloadPacket(packet.getChannel(), finalData));
                });
                session.sendForm(form);
            });
        } else if (channel.equals(PluginMessageChannels.TRANSFER)) {
            session.ensureInEventLoop(() -> {
                byte[] data = packet.getData();

                // port (4 bytes), address (remaining data)
                if (data.length < 5) {
                    throw new NullPointerException("Transfer data should be at least 5 bytes long");
                }

                int port = data[0] << 24 | (data[1] & 0xFF) << 16 | (data[2] & 0xFF) << 8 | data[3] & 0xFF;
                String address = new String(data, 4, data.length - 4);

                if (logger.isDebug()) {
                    logger.info("Transferring client to: " + address + ":" + port);
                }

                TransferPacket transferPacket = new TransferPacket();
                transferPacket.setAddress(address);
                transferPacket.setPort(port);
                session.sendUpstreamPacket(transferPacket);
            });

        } else if (channel.equals(PluginMessageChannels.PACKET)) {
            session.ensureInEventLoop(() -> {
                logger.debug("A packet has been sent using the Floodgate api");
                byte[] data = packet.getData();

                // packet id, packet data
                if (data.length < 2) {
                    throw new IllegalStateException("Packet data should be at least 2 bytes long");
                }

                int packetId = data[0] & 0xFF;
                ByteBuf packetData = Unpooled.wrappedBuffer(data, 1, data.length - 1);

                var toSend = new UnknownPacket();
                toSend.setPacketId(packetId);
                toSend.setPayload(packetData);

                session.sendUpstreamPacket(toSend);
            });
        }
    }
}
