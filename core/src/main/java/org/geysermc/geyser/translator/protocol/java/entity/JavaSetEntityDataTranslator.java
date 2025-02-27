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

package org.geysermc.geyser.translator.protocol.java.entity;

import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.EntityDefinition;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundSetEntityDataPacket;

@Translator(packet = ClientboundSetEntityDataPacket.class)
public class JavaSetEntityDataTranslator extends PacketTranslator<ClientboundSetEntityDataPacket> {

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void translate(GeyserSession session, ClientboundSetEntityDataPacket packet) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());
        if (entity == null) return;

        EntityDefinition<?> definition = entity.getDefinition();
        for (EntityMetadata<?, ?> metadata : packet.getMetadata()) {
            if (metadata.getId() >= definition.translators().size()) {
                if (session.getGeyser().getConfig().isDebugMode()) {
                    // Minecraft client just ignores these
                    GeyserLogger.get().warning("Metadata ID " + metadata.getId() + " is out of bounds of known entity metadata size " + definition.translators().size() + " for entity type " + entity.getDefinition().entityType());
                    GeyserLogger.get().debug(metadata.toString());
                }
                continue;
            }

            ((EntityDefinition) definition).translateMetadata(entity, metadata);
        }

        entity.updateBedrockMetadata();

        // Update the interactive tag, if necessary
        Entity mouseoverEntity = session.getMouseoverEntity();
        if (mouseoverEntity != null && mouseoverEntity.getEntityId() == entity.getEntityId()) {
            mouseoverEntity.updateInteractiveTag();
        }
    }
}
