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

package org.geysermc.connector.command;

import org.geysermc.connector.GeyserConnector;
import org.geysermc.connector.command.defaults.HelpCommand;
import org.geysermc.connector.command.defaults.StopCommand;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class GeyserCommandMap {

    private final Map<String, GeyserCommand> commandMap = Collections.synchronizedMap(new HashMap<>());
    private GeyserConnector connector;

    public GeyserCommandMap(GeyserConnector connector) {
        this.connector = connector;

        registerDefaults();
    }

    public void registerDefaults() {
        registerCommand(new HelpCommand(connector, "help", "Shows help for all registered commands."));
        registerCommand(new StopCommand(connector, "stop", "Shut down Geyser."));
    }

    public void registerCommand(GeyserCommand command) {
        commandMap.put(command.getName(), command);
        connector.getLogger().debug("Registered command " + command.getName());

        if (command.getAliases().isEmpty())
            return;

        for (String alias : command.getAliases())
            commandMap.put(alias, command);
    }

    public void runCommand(CommandSender sender, String command) {
        if (!command.startsWith("/geyser "))
            return;

        command = command.trim();
        command = command.replace("geyser ", "");
        String label;
        String[] args;

        if (!command.contains(" ")) {
            label = command.toLowerCase();
            args = new String[0];
        } else {
            label = command.substring(0, command.indexOf(" ")).toLowerCase();
            String argLine = command.substring(command.indexOf(" " + 1));
            args = argLine.contains(" ") ? argLine.split(" ") : new String[] { argLine };
        }

        GeyserCommand cmd = commandMap.get(label);
        if (cmd == null) {
            connector.getLogger().warning("Invalid Command! Try /help for a list of commands.");
            return;
        }

        cmd.execute(sender, args);
    }

    public Map<String, GeyserCommand> getCommands() {
        return commandMap;
    }
}
