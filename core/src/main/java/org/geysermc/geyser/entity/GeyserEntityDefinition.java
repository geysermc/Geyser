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

import com.github.steveice10.mc.protocol.data.game.entity.metadata.EntityMetadata;
import com.github.steveice10.mc.protocol.data.game.entity.metadata.MetadataType;
import com.github.steveice10.mc.protocol.data.game.entity.type.EntityType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtType;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.api.entity.EntityDefinition;
import org.geysermc.geyser.api.entity.EntityIdentifier;
import org.geysermc.geyser.entity.factory.EntityFactory;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.translator.entity.EntityMetadataTranslator;
import org.geysermc.geyser.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Represents data for an entity. This includes properties such as height and width, as well as the list of entity
 * metadata translators needed to translate the properties sent from the server. The translators are structured in such
 * a way that inserting a new one (for example in version updates) is convenient.
 *
 * @param <T> the entity type this definition represents
 */
public record GeyserEntityDefinition<T extends Entity>(EntityFactory<T> factory, EntityType entityType, EntityIdentifier entityIdentifier,
                                                       float width, float height, float offset, List<EntityMetadataTranslator<? super T, ?, ?>> translators, boolean custom) implements EntityDefinition {

    public static <T extends Entity> EntityDefinitionBuilder<T> inherited(EntityFactory<T> factory, GeyserEntityDefinition<? super T> parent) {
        return new EntityDefinitionBuilder<>(factory, parent.entityType, parent.entityIdentifier, parent.width, parent.height, parent.offset, new ObjectArrayList<>(parent.translators));
    }

    public static <T extends Entity> EntityDefinitionBuilder<T> builder(EntityFactory<T> factory) {
        return new EntityDefinitionBuilder<>(factory);
    }

    @SuppressWarnings("unchecked")
    public <M> void translateMetadata(T entity, EntityMetadata<M, ? extends MetadataType<M>> metadata) {
        EntityMetadataTranslator<? super T, M, EntityMetadata<M, ? extends MetadataType<M>>> translator = (EntityMetadataTranslator<? super T, M, EntityMetadata<M, ? extends MetadataType<M>>>) this.translators.get(metadata.getId());
        if (translator == null) {
            // This can safely happen; it means we don't translate this entity metadata
            return;
        }

        if (translator.acceptedType() != metadata.getType()) {
            GeyserImpl.getInstance().getLogger().warning("Metadata ID " + metadata.getId() + " was received with type " + metadata.getType() + " but we expected " + translator.acceptedType() + " for " + entity.getDefinition().entityType());
            if (GeyserImpl.getInstance().getConfig().isDebugMode()) {
                GeyserImpl.getInstance().getLogger().debug(metadata.toString());
            }
            return;
        }

        translator.translate(entity, metadata);
    }

    public String identifier() {
        return this.entityIdentifier.identifier();
    }

    public boolean isRegistered() {
        return Registries.ENTITY_DEFINITIONS.get().containsValue(this);
    }

    public EntityDefinitionBuilder<T> toBuilder() {
        return new EntityDefinitionBuilder<>(this);
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class EntityDefinitionBuilder<T extends Entity> implements EntityDefinition.Builder {
        private final EntityFactory<T> factory;
        private EntityType type;
        private EntityIdentifier identifier;
        private float width;
        private float height;
        private float offset = 0.00001f;
        private final List<EntityMetadataTranslator<? super T, ?, ?>> translators;
        private final boolean custom;

        private EntityDefinitionBuilder(GeyserEntityDefinition<T> definition) {
            this.factory = definition.factory;
            this.type = definition.entityType;
            this.identifier = definition.entityIdentifier;
            this.width = definition.width;
            this.height = definition.height;
            this.offset = definition.offset;
            this.translators = new ArrayList<>(definition.translators);
            this.custom = definition.custom;
        }

        private EntityDefinitionBuilder(EntityFactory<T> factory) {
            this(factory, false);
        }

        public EntityDefinitionBuilder(EntityFactory<T> factory, boolean custom) {
            this.factory = factory;
            this.translators = new ObjectArrayList<>();
            this.custom = custom;
        }

        public EntityDefinitionBuilder(EntityFactory<T> factory, EntityType type, EntityIdentifier identifier, float width, float height, float offset, List<EntityMetadataTranslator<? super T, ?, ?>> translators) {
            this.factory = factory;
            this.type = type;
            this.identifier = identifier;
            this.width = width;
            this.height = height;
            this.offset = offset;
            this.translators = translators;
            this.custom = false;
        }

        @Override
        public EntityDefinitionBuilder<T> identifier(EntityIdentifier identifier) {
            this.identifier = identifier;
            return this;
        }

        public EntityDefinitionBuilder<T> identifier(String identifier) {
            NbtMap nbt = Registries.BEDROCK_ENTITY_IDENTIFIERS.get();
            List<NbtMap> idlist = nbt.getList("idlist", NbtType.COMPOUND);
            Optional<NbtMap> entityIdentifier = idlist.stream().filter(tag -> tag.getString("id").equals(identifier)).findFirst();

            // Create a fake entity identifier for entities which are
            // in Java but may not be in Bedrock (e.g. item frames).
            if (entityIdentifier.isEmpty()) {
                this.identifier = new GeyserEntityIdentifier(NbtMap.builder()
                        .putString("id", identifier)
                        .putBoolean("hasspawnegg", false)
                        .putBoolean("summonable", false)
                        .build());

                return this;
            }

            this.identifier = new GeyserEntityIdentifier(entityIdentifier.get());
            return this;
        }

        /**
         * Sets the height and width as one value
         */
        public EntityDefinitionBuilder<T> heightAndWidth(float value) {
            height = value;
            width = value;
            return this;
        }

        public EntityDefinitionBuilder<T> offset(float offset) {
            this.offset = offset + 0.00001f;
            return this;
        }

        /**
         * Resets the identifier as well
         */
        public EntityDefinitionBuilder<T> type(EntityType type) {
            this.type = type;
            identifier = null;
            return this;
        }

        public <U, EM extends EntityMetadata<U, ? extends MetadataType<U>>> EntityDefinitionBuilder<T> addTranslator(MetadataType<U> type, BiConsumer<T, EM> translateFunction) {
            translators.add(new EntityMetadataTranslator<>(type, translateFunction));
            return this;
        }

        public EntityDefinitionBuilder<T> addTranslator(EntityMetadataTranslator<T, ?, ?> translator) {
            translators.add(translator);
            return this;
        }

        public GeyserEntityDefinition<T> build() {
            return build(true);
        }

        /**
         * @param register whether to register this entity in the Registries for entity types. Generally this should be
         *                 set to false if we're not expecting this entity to spawn from the network.
         */
        public GeyserEntityDefinition<T> build(boolean register) {
            String identifier = null;
            if (this.identifier == null && type != null) {
                identifier = "minecraft:" + type.name().toLowerCase(Locale.ROOT);
                this.identifier(identifier);
            } else if (this.identifier != null && type == null) {
                identifier = this.identifier.identifier();
            }

            GeyserEntityDefinition<T> definition = new GeyserEntityDefinition<>(factory, type, this.identifier, width, height, offset, translators, custom);
            if (register && identifier != null) {
                EntityUtils.registerEntity(identifier, definition);
            }

            return definition;
        }
    }
}
