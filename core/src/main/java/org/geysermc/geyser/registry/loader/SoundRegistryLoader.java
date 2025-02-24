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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.registry.type.SoundMapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Loads sounds from the given input.
 */
public class SoundRegistryLoader implements RegistryLoader<String, Map<String, SoundMapping>> {
    @Override
    public Map<String, SoundMapping> load(String input) {
        JsonNode soundsTree;
        try (InputStream stream = GeyserImpl.getInstance().getBootstrap().getResourceOrThrow(input)) {
            soundsTree = GeyserImpl.JSON_MAPPER.readTree(stream);
        } catch (IOException e) {
            throw new AssertionError("Unable to load sound mappings", e);
        }

        Map<String, SoundMapping> soundMappings = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> soundsIterator = soundsTree.fields();
        while (soundsIterator.hasNext()) {
            Map.Entry<String, JsonNode> next = soundsIterator.next();
            JsonNode brMap = next.getValue();
            String javaSound = next.getKey();
            soundMappings.put(javaSound, new SoundMapping(
                            javaSound,
                            brMap.has("bedrock_mapping") && brMap.get("bedrock_mapping").isTextual() ? brMap.get("bedrock_mapping").asText() : null,
                            brMap.has("playsound_mapping") && brMap.get("playsound_mapping").isTextual() ? brMap.get("playsound_mapping").asText() : null,
                            brMap.has("extra_data") && brMap.get("extra_data").isInt() ? brMap.get("extra_data").asInt() : -1,
                            brMap.has("identifier") && brMap.get("identifier").isTextual() ? brMap.get("identifier").asText() : null,
                            brMap.has("level_event") && brMap.get("level_event").isBoolean() && brMap.get("level_event").asBoolean(),
                            brMap.has("pitch_adjust") && brMap.get("pitch_adjust").isNumber() ? brMap.get("pitch_adjust").floatValue() : 1.0f
                    )
            );
        }
        return soundMappings;
    }
}