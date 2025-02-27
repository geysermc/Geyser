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

package org.geysermc.geyser.network.translators.chat;

import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.mcprotocollib.protocol.data.DefaultComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.HashMap;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class MessageTranslatorTest {

    private final Map<String, String> messages = new HashMap<>();

    @BeforeAll
    public void setUp() throws Exception {
        messages.put("{\"text\":\"\",\"extra\":[{\"text\":\"DoctorMad9952 joined the game\",\"color\":\"yellow\"}]}",
                "§r§eDoctorMad9952 joined the game");

        messages.put("{\"text\":\"\",\"extra\":[\"Plugins (3): \",{\"text\":\"WorldEdit\",\"color\":\"green\"},{\"text\":\", \",\"color\":\"white\"},{\"text\":\"ViaVersion\",\"color\":\"green\"},{\"text\":\", \",\"color\":\"white\"},{\"text\":\"Geyser-Spigot\",\"color\":\"green\"}]}",
                "Plugins (3): §r§aWorldEdit§r§f, §r§aViaVersion§r§f, §r§aGeyser-Spigot");

        // RGB downgrade test
        messages.put("{\"extra\":[{\"text\":\"          \"},{\"color\":\"gold\",\"text\":\"The \"},{\"color\":\"#E14248\",\"obfuscated\":true,\"text\":\"||\"},{\"color\":\"#3AA9FF\",\"bold\":true,\"text\":\"CubeCraft\"},{\"color\":\"#E14248\",\"obfuscated\":true,\"text\":\"||\"},{\"color\":\"gold\",\"text\":\" Network \"},{\"color\":\"green\",\"text\":\"[1.8/1.9+]\\n         \"},{\"color\":\"#f5e342\",\"text\":\"✦ \"},{\"color\":\"#b042f5\",\"bold\":true,\"text\":\"N\"},{\"color\":\"#c142f5\",\"bold\":true,\"text\":\"E\"},{\"color\":\"#d342f5\",\"bold\":true,\"text\":\"W\"},{\"color\":\"#e442f5\",\"bold\":true,\"text\":\":\"},{\"color\":\"#f542f5\",\"bold\":true,\"text\":\" \"},{\"color\":\"#bcf542\",\"bold\":true,\"text\":\"A\"},{\"color\":\"#acee3f\",\"bold\":true,\"text\":\"M\"},{\"color\":\"#9ce73c\",\"bold\":true,\"text\":\"O\"},{\"color\":\"#8ce039\",\"bold\":true,\"text\":\"N\"},{\"color\":\"#7cd936\",\"bold\":true,\"text\":\"G\"},{\"color\":\"#6cd233\",\"bold\":true,\"text\":\" \"},{\"color\":\"#5ccb30\",\"bold\":true,\"text\":\"S\"},{\"color\":\"#4cc42d\",\"bold\":true,\"text\":\"L\"},{\"color\":\"#3cbd2a\",\"bold\":true,\"text\":\"I\"},{\"color\":\"#2cb627\",\"bold\":true,\"text\":\"M\"},{\"color\":\"#1caf24\",\"bold\":true,\"text\":\"E\"},{\"color\":\"#0ca821\",\"bold\":true,\"text\":\"S\"},{\"color\":\"#f5e342\",\"text\":\" \"},{\"color\":\"#6d7c87\",\"text\":\"(kinda sus) \"},{\"color\":\"#f5e342\",\"text\":\"✦\"}],\"text\":\"\"}","          §r§6The §r§c§k||§r§b§lCubeCraft§r§c§k||§r§6 Network §r§a[1.8/1.9+]\n§a" +
                        "         §r§e✦ §r§d§lN§r§d§lE§r§d§lW§r§d§l:§r§d§l §r§e§lA§r§e§lM§r§e§lO§r§a§lN§r§a§lG§r§a§l §r§a§lS§r§2§lL§r§2§lI§r§q§lM§r§q§lE§r§q§lS§r§e §r§t(kinda sus) §r§e✦");

        // Color code format resetting
        messages.put("{\"text\":\"\",\"extra\":[{\"text\":\"\",\"extra\":[{\"text\":\"[\",\"color\":\"gray\"},{\"text\":\"H\",\"color\":\"yellow\"},{\"text\":\"]\",\"color\":\"gray\"},{\"text\":\" \",\"color\":\"white\"},{\"text\":\"GUEST\",\"color\":\"#b7b7b7\",\"bold\":true}]},{\"text\":\"\",\"extra\":[{\"text\":\" \",\"bold\":true},{\"text\":\"»\",\"color\":\"blue\"},{\"text\":\" \",\"color\":\"gray\"}]},{\"text\":\"\",\"extra\":[{\"text\":\"rtm516\",\"color\":\"white\"},{\"text\":\": \",\"color\":\"gray\"},{\"text\":\"\",\"color\":\"white\"}]},{\"text\":\"\",\"extra\":[{\"text\":\"This is an amazing bedrock test message\",\"color\":\"white\"}]}]}\n",
                "§r§7[§r§eH§r§7]§r§f §r§7§lGUEST§r§l §r§9»§r§7 §r§frtm516§r§7: §r§fThis is an amazing bedrock test message");

        // Test translation and positional arguments
        // Disabled due to not having an Geyser instance, hence it fails
        //messages.put("{\"translate\":\"death.attack.player\",\"with\":[{\"text\":\"rtm516\",\"insertion\":\"rtm516\"},{\"text\":\"*invincible_rt\",\"insertion\":\"*invincible_rt\"}]}",
        //        "rtm516 was slain by *invincible_rt");
        //// Test translation with the ' character (which MessageFormat requires special handling for)
        //messages.put("{\"translate\":\"commands.give.success.single\",\"with\":[{\"text\":\"1\"},{\"color\":\"yellow\",\"hoverEvent\":{\"action\":\"show_item\",\"contents\":{\"id\":\"minecraft:player_head\",\"tag\":\"{SkullOwner:\\\"Camotoy\\\"}\"}},\"translate\":\"chat.square_brackets\",\"with\":[{\"extra\":[{\"translate\":\"block.minecraft.player_head.named\",\"with\":[{\"text\":\"Camotoy\"}]}],\"text\":\"\"}]},{\"insertion\":\"DoctorMad9952\",\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/tell DoctorMad9952 \"},\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"type\":\"minecraft:player\",\"id\":\"8d712993-d208-3dac-b4d8-f2ce7e7d2b75\",\"name\":{\"text\":\"DoctorMad9952\"}}},\"extra\":[{\"text\":\"DoctorMad9952\"}],\"text\":\"\"}]}",
        //        "Gave 1 §r§e[Camotoy's Head]§r to DoctorMad9952");

        // Newline color restore
        messages.put("{\"color\":\"#F7DC77\",\"text\":\" Contribute to a weekly community goal.\\n All participants will receive a reward\\n and the top 3 will get extra bonus prizes!\"}", "§r§e Contribute to a weekly community goal.\n" +
            "§e All participants will receive a reward\n" +
            "§e and the top 3 will get extra bonus prizes!");

        MessageTranslator.init();
    }

    @Test
    public void convertMessage() {
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            String bedrockMessage = MessageTranslator.convertJsonMessage(entry.getKey(), "en_US");
            Assertions.assertEquals(entry.getValue(), bedrockMessage, "Translation of messages is incorrect");
        }
    }

    @Test
    public void convertMessageLenient() {
        Assertions.assertEquals("\n\n\n\n", MessageTranslator.convertMessageLenient("\n\n\n\n"), "All newline message is not handled properly");
        Assertions.assertEquals("", MessageTranslator.convertMessageLenient(""), "Empty message is not handled properly");
        Assertions.assertEquals("§r§eGame Selector", MessageTranslator.convertMessageLenient("§r§eGame Selector"), "Reset before message is not handled properly");
        Assertions.assertEquals("Bold Underline", MessageTranslator.convertMessageLenient("§m§nBold Underline"), "Unimplemented formatting chars not stripped");
        Assertions.assertEquals("§r§d[Test]", MessageTranslator.convertMessageLenient("§r§r§d[Test]§r"), "Duplicate and redundant reset removal");
    }

    @Test
    public void convertToPlainText() {
        Assertions.assertEquals("Many colors here", MessageTranslator.convertToPlainTextLenient("{\"extra\":[{\"color\":\"red\",\"text\":\"M\"},{\"color\":\"gold\",\"text\":\"a\"},{\"color\":\"yellow\",\"text\":\"n\"},{\"color\":\"green\",\"text\":\"y \"},{\"color\":\"aqua\",\"text\":\"c\"},{\"color\":\"dark_purple\",\"text\":\"o\"},{\"color\":\"red\",\"text\":\"l\"},{\"color\":\"gold\",\"text\":\"o\"},{\"color\":\"yellow\",\"text\":\"r\"},{\"color\":\"green\",\"text\":\"s \"},{\"color\":\"aqua\",\"text\":\"h\"},{\"color\":\"dark_purple\",\"text\":\"e\"},{\"color\":\"red\",\"text\":\"r\"},{\"color\":\"gold\",\"text\":\"e\"}],\"text\":\"\"}", "en_US"), "JSON message is not handled properly");
        Assertions.assertEquals("Many colors here", MessageTranslator.convertToPlainText("§cM§6a§en§ay §bc§5o§cl§6o§er§as §bh§5e§cr§6e"), "Legacy formatted message is not handled properly (Colors)");
        Assertions.assertEquals("Many colors here", MessageTranslator.convertToPlainTextLenient("§cM§6a§en§ay §bc§5o§cl§6o§er§as §bh§5e§cr§6e", "en_US"), "Legacy formatted message is not handled properly (Colors)");
        Assertions.assertEquals("Obf Bold Strikethrough Underline Italic Reset", MessageTranslator.convertToPlainTextLenient("§kObf §lBold §mStrikethrough §nUnderline §oItalic §rReset", "en_US"), "Legacy formatted message is not handled properly (Style)");
        Assertions.assertEquals("Strange", MessageTranslator.convertToPlainTextLenient("§rStrange", "en_US"), "Valid lenient JSON is not handled properly");
        Assertions.assertEquals("", MessageTranslator.convertToPlainTextLenient("", "en_US"), "Empty message is not handled properly");
        Assertions.assertEquals("     ", MessageTranslator.convertToPlainTextLenient("     ", "en_US"), "Whitespace is not preserved");
    }

    @Test
    public void testNullTextPacket() {
        DefaultComponentSerializer.get().deserialize("null");
    }
}
