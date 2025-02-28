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

package org.geysermc.geyser.scoreboard.network.util;

import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.configuration.AdvancedConfig;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class GeyserMockContext {
    private final List<Object> mocksAndSpies = new ArrayList<>();
    private final List<Object> storedObjects = new ArrayList<>();
    private final List<BedrockPacket> packets = Collections.synchronizedList(new ArrayList<>());

    public static void mockContext(Consumer<GeyserMockContext> geyserContext) {
        var context = new GeyserMockContext();

        var geyserImpl = context.mock(GeyserImpl.class);
        var config = context.mock(GeyserConfig.class);
        when(config.advanced()).thenReturn(context.mock(AdvancedConfig.class));

        when(config.advanced().scoreboardPacketThreshold()).thenReturn(1_000);

        when(geyserImpl.config()).thenReturn(config);

        var logger = context.storeObject(new EmptyGeyserLogger());
        when(geyserImpl.getLogger()).thenReturn(logger);

        try (var geyserImplMock = mockStatic(GeyserImpl.class)) {
            geyserImplMock.when(GeyserImpl::getInstance).thenReturn(geyserImpl);

            geyserContext.accept(context);
        }
    }

    public static void mockContext(Runnable runnable) {
        mockContext(context -> runnable.run());
    }

    public <T> T mock(Class<T> type) {
        return addMockOrSpy(Mockito.mock(type));
    }

    public <T> T spy(T object) {
        return addMockOrSpy(Mockito.spy(object));
    }

    private <T> T addMockOrSpy(T mockOrSpy) {
        mocksAndSpies.add(mockOrSpy);
        return mockOrSpy;
    }

    public <T> T storeObject(T object) {
        storedObjects.add(object);
        return object;
    }

    /**
     * Retries the mock or spy that is an instance of the specified type.
     * This is only really intended for classes where you only need a single instance of.
     */
    public <T> T mockOrSpy(Class<T> type) {
        for (Object mock : mocksAndSpies) {
            if (type.isInstance(mock)) {
                return type.cast(mock);
            }
        }
        return null;
    }

    public <T> T storedObject(Class<T> type) {
        for (Object storedObject : storedObjects) {
            if (type.isInstance(storedObject)) {
                return type.cast(storedObject);
            }
        }
        return null;
    }

    public GeyserSession session() {
        return mockOrSpy(GeyserSession.class);
    }

    void addPacket(BedrockPacket packet) {
        packets.add(packet);
    }

    public int packetCount() {
        return packets.size();
    }

    public BedrockPacket nextPacket() {
        if (packets.isEmpty()) {
            return null;
        }
        return packets.remove(0);
    }

    public List<BedrockPacket> packets() {
        return Collections.unmodifiableList(packets);
    }

    public <T> void translate(PacketTranslator<T> translator, T packet) {
        translator.translate(session(), packet);
    }
}
