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

package org.geysermc.geyser.registry.loader;

import net.kyori.adventure.key.Key;
import org.geysermc.geyser.api.bedrock.camera.CameraFade;
import org.geysermc.geyser.api.bedrock.camera.CameraPosition;
import org.geysermc.geyser.api.block.custom.CustomBlockData;
import org.geysermc.geyser.api.block.custom.NonVanillaCustomBlockData;
import org.geysermc.geyser.api.block.custom.component.CustomBlockComponents;
import org.geysermc.geyser.api.block.custom.component.GeometryComponent;
import org.geysermc.geyser.api.block.custom.component.MaterialInstance;
import org.geysermc.geyser.api.block.custom.nonvanilla.JavaBlockState;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.item.custom.CustomItemData;
import org.geysermc.geyser.api.item.custom.CustomItemOptions;
import org.geysermc.geyser.api.item.custom.NonVanillaCustomItemData;
import org.geysermc.geyser.api.item.custom.v2.CustomItemBedrockOptions;
import org.geysermc.geyser.api.item.custom.v2.CustomItemDefinition;
import org.geysermc.geyser.api.item.custom.v2.predicate.ConditionItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.MatchItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.RangeDispatchItemPredicate;
import org.geysermc.geyser.api.item.custom.v2.predicate.RangeDispatchPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.condition.ConditionPredicateProperty;
import org.geysermc.geyser.api.item.custom.v2.predicate.match.MatchPredicateProperty;
import org.geysermc.geyser.api.pack.PathPackCodec;
import org.geysermc.geyser.api.util.Identifier;
import org.geysermc.geyser.impl.IdentifierImpl;
import org.geysermc.geyser.impl.camera.GeyserCameraFade;
import org.geysermc.geyser.impl.camera.GeyserCameraPosition;
import org.geysermc.geyser.event.GeyserEventRegistrar;
import org.geysermc.geyser.extension.command.GeyserExtensionCommand;
import org.geysermc.geyser.item.GeyserCustomItemData;
import org.geysermc.geyser.item.GeyserCustomItemOptions;
import org.geysermc.geyser.item.GeyserNonVanillaCustomItemData;
import org.geysermc.geyser.item.custom.GeyserCustomItemBedrockOptions;
import org.geysermc.geyser.item.custom.GeyserCustomItemDefinition;
import org.geysermc.geyser.item.custom.predicate.ConditionPredicate;
import org.geysermc.geyser.item.custom.predicate.MatchPredicate;
import org.geysermc.geyser.item.custom.predicate.RangeDispatchPredicate;
import org.geysermc.geyser.level.block.GeyserCustomBlockComponents;
import org.geysermc.geyser.level.block.GeyserCustomBlockData;
import org.geysermc.geyser.level.block.GeyserGeometryComponent;
import org.geysermc.geyser.level.block.GeyserJavaBlockState;
import org.geysermc.geyser.level.block.GeyserMaterialInstance;
import org.geysermc.geyser.level.block.GeyserNonVanillaCustomBlockData;
import org.geysermc.geyser.pack.path.GeyserPathPackCodec;
import org.geysermc.geyser.registry.provider.ProviderSupplier;

import java.nio.file.Path;
import java.util.Map;

/**
 * Registers the provider data from the provider.
 */
public class ProviderRegistryLoader implements RegistryLoader<Map<Class<?>, ProviderSupplier>, Map<Class<?>, ProviderSupplier>> {

    @Override
    public Map<Class<?>, ProviderSupplier> load(Map<Class<?>, ProviderSupplier> providers) {
        // misc
        providers.put(Identifier.class, args -> new IdentifierImpl(Key.key((String) args[0], (String) args[1])));

        providers.put(Command.Builder.class, args -> new GeyserExtensionCommand.Builder<>((Extension) args[0]));

        providers.put(CustomBlockComponents.Builder.class, args -> new GeyserCustomBlockComponents.Builder());
        providers.put(CustomBlockData.Builder.class, args -> new GeyserCustomBlockData.Builder());
        providers.put(JavaBlockState.Builder.class, args -> new GeyserJavaBlockState.Builder());
        providers.put(NonVanillaCustomBlockData.Builder.class, args -> new GeyserNonVanillaCustomBlockData.Builder());
        providers.put(MaterialInstance.Builder.class, args -> new GeyserMaterialInstance.Builder());
        providers.put(GeometryComponent.Builder.class, args -> new GeyserGeometryComponent.Builder());

        providers.put(EventRegistrar.class, args -> new GeyserEventRegistrar(args[0]));
        providers.put(PathPackCodec.class, args -> new GeyserPathPackCodec((Path) args[0]));

        // items
        providers.put(CustomItemData.Builder.class, args -> new GeyserCustomItemData.Builder());
        providers.put(CustomItemOptions.Builder.class, args -> new GeyserCustomItemOptions.Builder());
        providers.put(NonVanillaCustomItemData.Builder.class, args -> new GeyserNonVanillaCustomItemData.Builder());

        // items v2
        providers.put(CustomItemDefinition.Builder.class, args -> new GeyserCustomItemDefinition.Builder((Identifier) args[0], (Identifier) args[1]));
        providers.put(CustomItemBedrockOptions.Builder.class, args -> new GeyserCustomItemBedrockOptions.Builder());
        providers.put(ConditionItemPredicate.class, args -> new ConditionPredicate<>((ConditionPredicateProperty<? super Object>) args[0], (boolean) args[1], args[2]));
        providers.put(MatchItemPredicate.class, args -> new MatchPredicate<>((MatchPredicateProperty<? super Object>) args[0], args[1]));
        providers.put(RangeDispatchItemPredicate.class, args -> new RangeDispatchPredicate((RangeDispatchPredicateProperty) args[0], (double) args[1], (double) args[2], (boolean) args[3], (int) args[4]));

        // cameras
        providers.put(CameraFade.Builder.class, args -> new GeyserCameraFade.Builder());
        providers.put(CameraPosition.Builder.class, args -> new GeyserCameraPosition.Builder());

        return providers;
    }
}
