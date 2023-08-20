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

package org.geysermc.geyser.configuration;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.io.IOException;

@Getter
@AllArgsConstructor
public enum CooldownType {
    TITLE("options.attack.crosshair"),
    ACTIONBAR("options.attack.hotbar"),
    DISABLED("options.off");

    public static final String OPTION_DESCRIPTION = "options.attackIndicator";
    public static final CooldownType[] VALUES = values();

    private final String translation;

    /**
     * Convert the CooldownType string (from config) to the enum, DISABLED on fail
     *
     * @param name CooldownType string
     * @return The converted CooldownType
     */
    public static CooldownType getByName(String name) {
        if (name.equalsIgnoreCase("true")) { // Backwards config compatibility
            return CooldownType.TITLE;
        }

        for (CooldownType type : VALUES) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return DISABLED;
    }

    public static class Deserializer extends JsonDeserializer<CooldownType> {
        @Override
        public CooldownType deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            return CooldownType.getByName(p.getValueAsString());
        }
    }
}
