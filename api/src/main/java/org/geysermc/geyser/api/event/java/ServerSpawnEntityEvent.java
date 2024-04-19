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

package org.geysermc.geyser.api.event.java;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.entity.EntityDefinition;
import org.geysermc.geyser.api.event.connection.ConnectionEvent;

import java.util.UUID;

/**
 * Called when the downstream server spawns an entity.
 */
public class ServerSpawnEntityEvent extends ConnectionEvent {
    private final int entityId;
    private final UUID uuid;

    private EntityDefinition entityDefinition;

    public ServerSpawnEntityEvent(@NonNull GeyserConnection connection, int entityId, @Nullable UUID uuid,
                                  @NonNull EntityDefinition entityDefinition) {
        super(connection);
        this.entityId = entityId;
        this.uuid = uuid;
        this.entityDefinition = entityDefinition;
    }

    /**
     * Gets the entity id of the entity being spawned.
     *
     * @return the entity id of the entity being spawned
     */
    public int entityId() {
        return this.entityId;
    }

    /**
     * Gets the uuid of the entity being spawned.
     *
     * @return the uuid of the entity being spawned
     */
    @Nullable
    public UUID uuid() {
        return this.uuid;
    }

    /**
     * Gets the entity definition sent to the connection
     * when the entity is spawned.
     *
     * @return the entity definition sent to the connection
     *         when the entity is spawned
     */
    @NonNull
    public EntityDefinition entityDefinition() {
        return this.entityDefinition;
    }

    /**
     * Sets the entity definition sent to the connection
     * when the entity is spawned.
     *
     * @param entityDefinition the entity definition sent to the connection
     *                         when the entity is spawned
     */
    public void entityDefinition(@NonNull EntityDefinition entityDefinition) {
        this.entityDefinition = entityDefinition;
    }
}