/*
 * Copyright (c) 2019-2021 GeyserMC. http://geysermc.org
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

package org.geysermc.platform.sponge.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.geysermc.connector.command.CommandExecutor;
import org.geysermc.connector.command.CommandManager;
import org.geysermc.connector.command.CommandSender;
import org.geysermc.connector.command.GeyserCommand;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.utils.LanguageUtils;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.ArgumentReader;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class GeyserSpongeCommandExecutor extends CommandExecutor implements Command.Raw {

    public GeyserSpongeCommandExecutor(CommandManager commandManager) {
        super(commandManager);
    }

    @Override
    public CommandResult process(CommandCause cause, ArgumentReader.Mutable arguments) {
        CommandSender commandSender = new SpongeCommandSender(cause);
        GeyserSession session = commandSender.asGeyserSession();

        String[] args = arguments.input().split(" ");
        // This split operation results in an array of length 1, containing a zero length string, if the input string is empty
        if (args.length > 0 && !args[0].isEmpty()) {
            GeyserCommand command = getCommand(args[0]);
            if (command != null) {
                if (!cause.hasPermission(command.getPermission())) {
                    // Not ideal to use log here but we don't get a session
                    cause.audience().sendMessage(Component.text(LanguageUtils.getLocaleStringLog("geyser.bootstrap.command.permission_fail")).color(NamedTextColor.RED));
                    return CommandResult.success();
                }
                if (command.isBedrockOnly() && session == null) {
                    cause.audience().sendMessage(Component.text(LanguageUtils.getLocaleStringLog("geyser.bootstrap.command.bedrock_only")).color(NamedTextColor.RED));
                    return CommandResult.success();
                }
                command.execute(session, commandSender, args.length > 1 ? Arrays.copyOfRange(args, 1, args.length) : new String[0]);
            } else {
                cause.audience().sendMessage(Component.text(LanguageUtils.getLocaleStringLog("geyser.command.not_found")).color(NamedTextColor.RED));
            }
        } else {
            GeyserCommand help = getCommand("help");
            if (help == null) {
                // If construction fails during a reload then the geyser command will be registered but the command manager will be emtpy
                cause.audience().sendMessage(Component.text(LanguageUtils.getLocaleStringLog("geyser.command.not_found")).color(NamedTextColor.RED));
            } else {
                help.execute(session, commandSender, new String[0]);
            }
        }
        return CommandResult.success();
    }

    @Override
    public List<CommandCompletion> complete(CommandCause cause, ArgumentReader.Mutable arguments) {
        if (arguments.input().split(" ").length == 1) {
            return tabComplete(new SpongeCommandSender(cause)).stream().map(CommandCompletion::of).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public boolean canExecute(CommandCause cause) {
        return true;
    }

    @Override
    public Optional<Component> shortDescription(CommandCause cause) {
        return Optional.of(Component.text("The main command for Geyser."));
    }

    @Override
    public Optional<Component> extendedDescription(CommandCause cause) {
        return shortDescription(cause);
    }

    @Override
    public Optional<Component> help(@NotNull CommandCause cause) {
        return Optional.of(Component.text("/geyser help"));
    }

    @Override
    public Component usage(CommandCause cause) {
        return Component.text("/geyser help");
    }
}
