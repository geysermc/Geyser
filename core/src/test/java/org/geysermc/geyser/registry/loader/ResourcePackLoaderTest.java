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

package org.geysermc.geyser.registry.loader;

import org.geysermc.geyser.api.pack.ResourcePack;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

public class ResourcePackLoaderTest {

    @Test
    public void testPathMatcher() {
        PathMatcher matcher = ResourcePackLoader.PACK_MATCHER;

        assertTrue(matcher.matches(Path.of("pack.mcpack")));
        assertTrue(matcher.matches(Path.of("pack.zip")));
        assertTrue(matcher.matches(Path.of("packs", "pack.mcpack")));
        assertTrue(matcher.matches(Path.of("packs", "category", "pack.mcpack")));

        assertTrue(matcher.matches(Path.of("packs", "Resource+Pack+1.2.3.mcpack")));
        assertTrue(matcher.matches(Path.of("Resource+Pack+1.2.3.mcpack")));

        assertTrue(matcher.matches(Path.of("packs", "Resource+Pack+1.2.3.zip")));
        assertTrue(matcher.matches(Path.of("Resource+Pack+1.2.3.zip")));

        assertFalse(matcher.matches(Path.of("resource.pack")));
        assertFalse(matcher.matches(Path.of("pack.7zip")));
        assertFalse(matcher.matches(Path.of("packs")));
    }

    @Test
    public void testPack() throws Exception {
        // this mcpack only contains a folder, which the manifest is in
        Path path = getResource("empty_pack.mcpack");
        ResourcePack pack = ResourcePackLoader.readPack(path).build();
        assertEquals("", pack.contentKey());
        // should probably add some more tests here related to the manifest
    }

    @Test
    public void testEncryptedPack() throws Exception {
        // this zip only contains a contents.json and manifest.json at the root
        Path path = getResource("encrypted_pack.zip");
        ResourcePack pack = ResourcePackLoader.readPack(path).build();
        assertEquals("JAGcSXcXwcODc1YS70GzeWAUKEO172UA", pack.contentKey());
    }

    private Path getResource(String name) throws URISyntaxException {
        URL url = Objects.requireNonNull(getClass().getClassLoader().getResource(name), "No resource for name: " + name);
        return Path.of(url.toURI());
    }
}
