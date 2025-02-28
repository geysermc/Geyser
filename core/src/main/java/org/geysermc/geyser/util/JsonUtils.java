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

package org.geysermc.geyser.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import marcono1234.gson.recordadapter.RecordTypeAdapterFactory;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.pack.GeyserResourcePackManifest;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public final class JsonUtils {

    public static <T> T fromJson(byte[] bytes, Class<T> type) {
        return GeyserImpl.GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
    }

    public static JsonObject fromJson(InputStream stream) {
        return (JsonObject) new JsonParser().parse(new InputStreamReader(stream));
    }

    public static JsonObject parseJson(String s) {
        return (JsonObject) new JsonParser().parse(s);
    }

    public static <T> T fromJson(InputStream stream, Type type) {
        return GeyserImpl.GSON.fromJson(new InputStreamReader(stream), type);
    }

    public static Gson createGson() {
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
        try {
            new Gson().fromJson("{\"version\":[1,0,0],\"uuid\":\"eebb4ea8-a701-11eb-95ba-047d7bb283ba\"}", GeyserResourcePackManifest.Dependency.class);
        } catch (Throwable e) {
            // 1.16.5 and 1.17.1 (at minimum) have an outdated Gson version that doesn't support records.
            // Remove this workaround when all platforms support Gson 2.10+
            // (Explicitly allow missing component values - the dependencies module for resource packs, for example, can be missing)
            builder.registerTypeAdapterFactory(RecordTypeAdapterFactory.builder().allowMissingComponentValues().create())
                // Since this is a record, the above will take precedence unless we explicitly declare it.
                .registerTypeAdapter(GeyserResourcePackManifest.Version.class, new GeyserResourcePackManifest.Version.VersionDeserializer());
        }
        return builder.create();
    }

    private JsonUtils() {
    }
}
