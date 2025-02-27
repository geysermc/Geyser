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

package org.geysermc.geyser.api.pack.option;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.pack.ResourcePackManifest;

/**
 * Can be used to specify which subpack from a resource pack a player should load.
 * Available subpacks can be seen in a resource pack manifest {@link ResourcePackManifest#subpacks()}.
 * @since 2.6.2
 */
public interface SubpackOption extends ResourcePackOption<String> {

    /**
     * Creates a subpack option based on a {@link ResourcePackManifest.Subpack}.
     *
     * @param subpack the chosen subpack
     * @return a subpack option specifying that subpack
     * @since 2.6.2
     */
    static SubpackOption subpack(ResourcePackManifest.@NonNull Subpack subpack) {
        return named(subpack.name());
    }

    /**
     * Creates a subpack option based on a subpack name.
     *
     * @param subpackName the name of the subpack
     * @return a subpack option specifying a subpack with that name
     * @since 2.6.2
     */
    static SubpackOption named(@NonNull String subpackName) {
        return GeyserApi.api().provider(SubpackOption.class, subpackName);
    }

    /**
     * Creates a subpack option with no subpack specified.
     *
     * @return a subpack option specifying no subpack
     * @since 2.6.2
     */
    static SubpackOption empty() {
        return GeyserApi.api().provider(SubpackOption.class, "");
    }

}
