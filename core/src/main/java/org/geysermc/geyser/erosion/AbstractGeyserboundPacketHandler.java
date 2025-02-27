/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.erosion;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBatchBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockEntityPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockIdPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockLookupFailPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundBlockPlacePacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPacketHandler;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPickBlockPacket;
import org.geysermc.erosion.packet.geyserbound.GeyserboundPistonEventPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.session.GeyserSession;

public abstract class AbstractGeyserboundPacketHandler implements GeyserboundPacketHandler {
    protected final GeyserSession session;

    public AbstractGeyserboundPacketHandler(GeyserSession session) {
        this.session = session;
    }

    @Override
    public void handleBatchBlockId(GeyserboundBatchBlockIdPacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handleBlockEntity(GeyserboundBlockEntityPacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handleBlockId(GeyserboundBlockIdPacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handleBlockLookupFail(GeyserboundBlockLookupFailPacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handleBlockPlace(GeyserboundBlockPlacePacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handlePistonEvent(GeyserboundPistonEventPacket packet) {
        illegalPacket(packet);
    }

    @Override
    public void handlePickBlock(GeyserboundPickBlockPacket packet) {
        illegalPacket(packet);
    }

    /**
     * Is this handler actually listening to any packets?
     */
    public abstract boolean isActive();

    @Nullable
    public abstract GeyserboundPacketHandlerImpl getAsActive();

    public void close() {
    }

    protected final void illegalPacket(GeyserboundPacket packet) {
        GeyserLogger.get().warning("Illegal packet sent from backend server! " + packet);
    }
}
