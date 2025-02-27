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

package org.geysermc.geyser.pack.option;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.pack.ResourcePack;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.api.pack.exception.ResourcePackException;
import org.geysermc.geyser.api.pack.option.SubpackOption;

import java.util.Objects;

/**
 * Can be used to specify which subpack from a resource pack a player should load.
 * Available subpacks can be seen in a resource pack manifest {@link ResourcePackManifest#subpacks()}
 */
public record GeyserSubpackOption(String subpackName) implements SubpackOption {

    @Override
    public @NonNull Type type() {
        return Type.SUBPACK;
    }

    @Override
    public @NonNull String value() {
        return subpackName;
    }

    @Override
    public void validate(@NonNull ResourcePack pack) {
        Objects.requireNonNull(pack);

        // Allow empty subpack names - they're the same as "none"
        if (subpackName.isEmpty()) {
            return;
        }

        if (pack.manifest().subpacks().stream().noneMatch(subpack -> subpack.name().equals(subpackName))) {
            throw new ResourcePackException(ResourcePackException.Cause.INVALID_PACK_OPTION,
                "No subpack with the name %s found!".formatted(subpackName));
        }
    }
}
