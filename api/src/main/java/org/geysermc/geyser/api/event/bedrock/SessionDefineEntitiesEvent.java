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

package org.geysermc.geyser.api.event.bedrock;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.event.Event;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.EntityDefinition;
import org.geysermc.geyser.api.entity.EntityIdentifier;
import org.geysermc.geyser.api.event.connection.ConnectionEvent;

import java.util.List;

/**
 * Called when Geyser sends a list of available entities to the
 * Bedrock client. This will typically contain all the available
 * entities within vanilla, but can be modified to include any custom
 * entity defined through a resource pack.
 */
public abstract class SessionDefineEntitiesEvent extends ConnectionEvent {
    private final List<EntityIdentifier> identifiers;

    public SessionDefineEntitiesEvent(@NonNull GeyserConnection connection, @NonNull List<EntityIdentifier> identifiers) {
        super(connection);
        this.identifiers = identifiers;
    }

    /**
     * Gets the list of entity identifiers.
     *
     * @return the list of entity identifiers
     */
    @NonNull
    public List<EntityIdentifier> entityIdentifiers() {
        return this.identifiers;
    }

    /**
     * Registers a new entity identifier for the player.
     *
     * @param entityIdentifier the entity identifier to register
     * @return {@code true} if the entity identifier was registered, {@code false} otherwise
     */
    public abstract boolean register(@NonNull EntityIdentifier entityIdentifier);
}