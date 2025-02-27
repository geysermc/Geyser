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

package org.geysermc.geyser.api.util;

import org.geysermc.geyser.api.GeyserApi;

public interface Identifier {
    String DEFAULT_NAMESPACE = "minecraft";

    static Identifier of(String namespace, String path) {
        return GeyserApi.api().provider(Identifier.class, namespace, path);
    }

    static Identifier of(String identifier) {
        String[] split = identifier.split(":");
        String namespace;
        String path;
        if (split.length == 1) {
            namespace = DEFAULT_NAMESPACE;
            path = split[0];
        } else if (split.length == 2) {
            namespace = split[0];
            path = split[1];
        } else {
            throw new IllegalArgumentException("':' in identifier path: " + identifier);
        }
        return of(namespace, path);
    }

    String namespace();

    String path();
}
