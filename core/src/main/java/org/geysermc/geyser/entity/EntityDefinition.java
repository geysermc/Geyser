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

package org.geysermc.geyser.entity;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.factory.EntityFactory;
import org.geysermc.geyser.entity.properties.GeyserEntityProperties;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.translator.entity.EntityMetadataTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.EntityMetadata;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.MetadataType;
import org.geysermc.mcprotocollib.protocol.data.game.entity.type.EntityType;

import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Represents data for an entity. This includes properties such as height and width, as well as the list of entity
 * metadata translators needed to translate the properties sent from the server. The translators are structured in such
 * a way that inserting a new one (for example in version updates) is convenient.
 *
 * @param identifier the Bedrock identifier of this entity
 * @param <T> the entity type this definition represents
 */
public record EntityDefinition<T extends Entity>(EntityFactory<T> factory, EntityType entityType, String identifier,
                                                 float width, float height, float offset, GeyserEntityProperties registeredProperties, List<EntityMetadataTranslator<? super T, ?, ?>> translators) {

    public static <T extends Entity> Builder<T> inherited(EntityFactory<T> factory, EntityDefinition<? super T> parent) {
        return new Builder<>(factory, parent.entityType, parent.identifier, parent.width, parent.height, parent.offset, parent.registeredProperties, new ObjectArrayList<>(parent.translators));
    }

    public static <T extends Entity> Builder<T> builder(EntityFactory<T> factory) {
        return new Builder<>(factory);
    }

    @SuppressWarnings("unchecked")
    public <M> void translateMetadata(T entity, EntityMetadata<M, ? extends MetadataType<M>> metadata) {
        EntityMetadataTranslator<? super T, M, EntityMetadata<M, ? extends MetadataType<M>>> translator = (EntityMetadataTranslator<? super T, M, EntityMetadata<M, ? extends MetadataType<M>>>) this.translators.get(metadata.getId());
        if (translator == null) {
            // This can safely happen; it means we don't translate this entity metadata
            return;
        }

        if (translator.acceptedType() != metadata.getType()) {
            GeyserLogger.get().warning("Metadata ID " + metadata.getId() + " was received with type " + metadata.getType() + " but we expected " + translator.acceptedType() + " for " + entity.getDefinition().entityType());
            if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                GeyserLogger.get().debug(metadata.toString());
            }
            return;
        }

        translator.translate(entity, metadata);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class Builder<T extends Entity> {
        private final EntityFactory<T> factory;
        private EntityType type;
        private String identifier;
        private float width;
        private float height;
        private float offset = 0.00001f;
        private GeyserEntityProperties registeredProperties;
        private final List<EntityMetadataTranslator<? super T, ?, ?>> translators;

        private Builder(EntityFactory<T> factory) {
            this.factory = factory;
            translators = new ObjectArrayList<>();
        }

        public Builder(EntityFactory<T> factory, EntityType type, String identifier, float width, float height, float offset, GeyserEntityProperties registeredProperties, List<EntityMetadataTranslator<? super T, ?, ?>> translators) {
            this.factory = factory;
            this.type = type;
            this.identifier = identifier;
            this.width = width;
            this.height = height;
            this.offset = offset;
            this.registeredProperties = registeredProperties;
            this.translators = translators;
        }

        /**
         * Sets the height and width as one value
         */
        public Builder<T> heightAndWidth(float value) {
            height = value;
            width = value;
            return this;
        }

        public Builder<T> offset(float offset) {
            this.offset = offset + 0.00001f;
            return this;
        }

        /**
         * Resets the identifier as well
         */
        public Builder<T> type(EntityType type) {
            this.type = type;
            identifier = null;
            return this;
        }

        public Builder<T> properties(GeyserEntityProperties registeredProperties) {
            this.registeredProperties = registeredProperties;
            return this;
        }

        public <U, EM extends EntityMetadata<U, ? extends MetadataType<U>>> Builder<T> addTranslator(MetadataType<U> type, BiConsumer<T, EM> translateFunction) {
            translators.add(new EntityMetadataTranslator<>(type, translateFunction));
            return this;
        }

        public Builder<T> addTranslator(EntityMetadataTranslator<T, ?, ?> translator) {
            translators.add(translator);
            return this;
        }

        public EntityDefinition<T> build() {
            return build(true);
        }

        /**
         * @param register whether to register this entity in the Registries for entity types. Generally this should be
         *                 set to false if we're not expecting this entity to spawn from the network.
         */
        public EntityDefinition<T> build(boolean register) {
            if (identifier == null && type != null) {
                identifier = "minecraft:" + type.name().toLowerCase(Locale.ROOT);
            }
            EntityDefinition<T> definition = new EntityDefinition<>(factory, type, identifier, width, height, offset, registeredProperties, translators);
            if (register && definition.entityType() != null) {
                Registries.ENTITY_DEFINITIONS.get().putIfAbsent(definition.entityType(), definition);
                Registries.JAVA_ENTITY_IDENTIFIERS.get().putIfAbsent("minecraft:" + type.name().toLowerCase(Locale.ROOT), definition);
                if (definition.registeredProperties() != null) {
                    Registries.BEDROCK_ENTITY_PROPERTIES.get().add(definition.registeredProperties().toNbtMap(identifier));
                }
            }
            return definition;
        }
    }
}
