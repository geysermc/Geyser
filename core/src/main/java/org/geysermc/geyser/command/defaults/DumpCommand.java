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

package org.geysermc.geyser.command.defaults;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.dump.DumpInfo;
import org.geysermc.geyser.text.AsteriskSerializer;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.util.WebUtils;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.suggestion.SuggestionProvider;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.incendo.cloud.parser.standard.StringArrayParser.stringArrayParser;

public class DumpCommand extends GeyserCommand {

    private static final String ARGUMENTS = "args";
    private static final Iterable<String> SUGGESTIONS = List.of("full", "offline", "logs");

    private final GeyserImpl geyser;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DUMP_URL = "https://dump.geysermc.org/";

    public DumpCommand(GeyserImpl geyser, String name, String description, String permission) {
        super(name, description, permission, TriState.NOT_SET);
        this.geyser = geyser;
    }

    @Override
    public void register(CommandManager<GeyserCommandSource> manager) {
        manager.command(baseBuilder(manager)
            .optional(ARGUMENTS, stringArrayParser(), SuggestionProvider.blockingStrings((ctx, input) -> {
                // parse suggestions here
                List<String> inputs = new ArrayList<>();
                while (input.hasRemainingInput()) {
                    inputs.add(input.readStringSkipWhitespace());
                }

                if (inputs.size() <= 2) {
                    return SUGGESTIONS; // only `geyser dump` was typed (2 literals)
                }

                // the rest of the input after `geyser dump` is for this argument
                inputs = inputs.subList(2, inputs.size());

                // don't suggest any words they have already typed
                List<String> suggestions = new ArrayList<>();
                SUGGESTIONS.forEach(suggestions::add);
                suggestions.removeAll(inputs);
                return suggestions;
            }))
            .handler(this::execute));
    }

    @Override
    public void execute(CommandContext<GeyserCommandSource> context) {
        GeyserCommandSource source = context.sender();
        String[] args = context.getOrDefault(ARGUMENTS, new String[0]);

        boolean showSensitive = false;
        boolean offlineDump = false;
        boolean addLog = false;
        if (args.length >= 1) {
            for (String arg : args) {
                switch (arg) {
                    case "full" -> showSensitive = true;
                    case "offline" -> offlineDump = true;
                    case "logs" -> addLog = true;
                    default -> context.sender().sendMessage("Invalid geyser dump option " + arg + "! Fallback to no arguments.");
                }
            }
        }

        AsteriskSerializer.showSensitive = showSensitive;

        source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.dump.collecting", source.locale()));
        String dumpData;
        try {
            DumpInfo dump = new DumpInfo(geyser, addLog);

            if (offlineDump) {
                DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
                // Make arrays easier to read
                prettyPrinter.indentArraysWith(new DefaultIndenter("    ", "\n"));
                dumpData = MAPPER.writer(prettyPrinter).writeValueAsString(dump);
            } else {
                dumpData = MAPPER.writeValueAsString(dump);
            }
        } catch (IOException e) {
            source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.commands.dump.collect_error", source.locale()));
            GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.commands.dump.collect_error_short"), e);
            return;
        }

        String uploadedDumpUrl;

        if (offlineDump) {
            source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.dump.writing", source.locale()));

            try {
                FileOutputStream outputStream = new FileOutputStream(GeyserImpl.getInstance().getBootstrap().getConfigFolder().resolve("dump.json").toFile());
                outputStream.write(dumpData.getBytes());
                outputStream.close();
            } catch (IOException e) {
                source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.commands.dump.write_error", source.locale()));
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.commands.dump.write_error_short"), e);
                return;
            }

            uploadedDumpUrl = "dump.json";
        } else {
            source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.dump.uploading", source.locale()));

            String response;
            JsonNode responseNode;
            try {
                response = WebUtils.post(DUMP_URL + "documents", dumpData);
                responseNode = MAPPER.readTree(response);
            } catch (IOException e) {
                source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.commands.dump.upload_error", source.locale()));
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.commands.dump.upload_error_short"), e);
                return;
            }

            if (!responseNode.has("key")) {
                source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.commands.dump.upload_error_short", source.locale()) + ": " + (responseNode.has("message") ? responseNode.get("message").asText() : response));
                return;
            }

            uploadedDumpUrl = DUMP_URL + responseNode.get("key").asText();
        }

        source.sendMessage(GeyserLocale.getPlayerLocaleString("geyser.commands.dump.message", source.locale()) + " " + ChatColor.DARK_AQUA + uploadedDumpUrl);
        if (!source.isConsole()) {
            GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.commands.dump.created", source.name(), uploadedDumpUrl));
        }
    }
}
