/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
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

package org.geysermc.connector.network.translators.world.block.entity;

import com.github.steveice10.mc.protocol.data.message.MessageSerializer;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.nukkitx.nbt.NbtMap;
import org.geysermc.connector.utils.MessageUtils;

import java.util.HashMap;
import java.util.Map;

@BlockEntity(name = "Sign", regex = "sign")
public class SignBlockEntityTranslator extends BlockEntityTranslator {

    @Override
    public Map<String, Object> translateTag(CompoundTag tag, int blockState) {
        Map<String, Object> tags = new HashMap<>();

        StringBuilder signText = new StringBuilder();
        for(int i = 0; i < 4; i++) {
            int currentLine = i+1;
            String signLine = getOrDefault(tag.getValue().get("Text" + currentLine), "");
            signLine = MessageUtils.getBedrockMessage(MessageSerializer.fromString(signLine));

            //Java allows up to 16+ characters on certain symbols. 
            if(signLine.length() >= 15 && (signLine.contains("-") || signLine.contains("="))) {
                signLine = signLine.substring(0, 14);
            }

            // Java Edition 1.14 added the ability to change the text color of the whole sign using dye
            if (tag.contains("Color")) {
                signText.append(getBedrockSignColor(tag.get("Color").getValue().toString()));
            }

            signText.append(signLine);
            signText.append("\n");
        }

        tags.put("Text", MessageUtils.getBedrockMessage(MessageSerializer.fromString(signText.toString())));
        return tags;
    }

    @Override
    public CompoundTag getDefaultJavaTag(String javaId, int x, int y, int z) {
        CompoundTag tag = getConstantJavaTag(javaId, x, y, z);
        tag.put(new com.github.steveice10.opennbt.tag.builtin.StringTag("Text1", "{\"text\":\"\"}"));
        tag.put(new com.github.steveice10.opennbt.tag.builtin.StringTag("Text2", "{\"text\":\"\"}"));
        tag.put(new com.github.steveice10.opennbt.tag.builtin.StringTag("Text3", "{\"text\":\"\"}"));
        tag.put(new com.github.steveice10.opennbt.tag.builtin.StringTag("Text4", "{\"text\":\"\"}"));
        return tag;
    }

    @Override
    public NbtMap getDefaultBedrockTag(String bedrockId, int x, int y, int z) {
        return getConstantBedrockTag(bedrockId, x, y, z).toBuilder()
                .putString("Text", "")
                .build();
    }

    private static String getBedrockSignColor(String javaColor) {
        String base = "\u00a7";
        switch (javaColor) {
            case "white":
                base += 'f';
                break;
            case "orange":
                base += '6';
                break;
            case "magenta":
            case "purple":
                base += '5';
                break;
            case "light_blue":
                base += 'b';
                break;
            case "yellow":
                base += 'e';
                break;
            case "lime":
                base += 'a';
                break;
            case "pink":
                base += 'd';
                break;
            case "gray":
                base += '8';
                break;
            case "light_gray":
                base += '7';
                break;
            case "cyan":
                base += '3';
                break;
            case "blue":
                base += '9';
                break;
            case "brown": // Brown does not have a bedrock counterpart.
            case "red": // In Java Edition light red (&c) can only be applied using commands. Red dye gives &4.
                base += '4';
                break;
            case "green":
                base += '2';
                break;
            case "black":
                base += '0';
                break;
        }

        if (base.length() > 1) {
            return base;
        } else {
            return "";
        }
    }

}
