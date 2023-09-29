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

package org.geysermc.geyser.extension.command;

import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.context.CommandContext;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.command.Command;
import org.geysermc.geyser.api.command.CommandExecutor;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.GeyserCommand;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.session.GeyserSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class GeyserExtensionCommand extends GeyserCommand {

    private final Extension extension;
    private final String rootCommand;

    public GeyserExtensionCommand(@NonNull Extension extension, @NonNull String name, @Nullable String description,
                                  @Nullable String permission, @Nullable TriState permissionDefault,
                                  boolean executableOnConsole, boolean bedrockOnly) {

        super(name, description, permission, permissionDefault, executableOnConsole, bedrockOnly);
        this.extension = extension;
        this.rootCommand = Objects.requireNonNull(extension.rootCommand());

        if (this.rootCommand.isBlank()) {
            throw new IllegalStateException("rootCommand of extension " + extension.name() + " may not be blank");
        }
    }

    public final Extension extension() {
        return this.extension;
    }

    @Override
    public final String rootCommand() {
        return this.rootCommand;
    }

    public static class Builder<T extends CommandSource> implements Command.Builder<T> {
        private final Extension extension;
        private Class<? extends T> sourceType;
        private String name;
        private String description;
        private String permission;
        private TriState permissionDefault;
        private List<String> aliases;
        private boolean suggestedOpOnly = false; // deprecated for removal
        private boolean executableOnConsole = true;
        private boolean bedrockOnly = false;
        private CommandExecutor<T> executor;

        public Builder(Extension extension) {
            this.extension = Objects.requireNonNull(extension);
        }

        @Override
        public Command.Builder<T> source(@NonNull Class<? extends T> sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        @Override
        public Builder<T> name(@NonNull String name) {
            this.name = name;
            return this;
        }

        @Override
        public Builder<T> description(@NonNull String description) {
            this.description = description;
            return this;
        }

        @Override
        public Builder<T> permission(@NonNull String permission) {
            this.permission = permission;
            return this;
        }

        @Override
        public Builder<T> permission(@NonNull String permission, @NonNull TriState defaultValue) {
            this.permission = permission;
            this.permissionDefault = Objects.requireNonNull(defaultValue, "defaultValue");
            return this;
        }

        @Override
        public Builder<T> aliases(@NonNull List<String> aliases) {
            this.aliases = aliases;
            return this;
        }

        @Override
        public Builder<T> suggestedOpOnly(boolean suggestedOpOnly) {
            this.suggestedOpOnly = suggestedOpOnly;
            if (suggestedOpOnly) {
                // the most amount of legacy/deprecated behaviour I'm willing to support
                this.permissionDefault = TriState.NOT_SET;
            }
            return this;
        }

        @Override
        public Builder<T> executableOnConsole(boolean executableOnConsole) {
            this.executableOnConsole = executableOnConsole;
            return this;
        }

        @Override
        public Builder<T> bedrockOnly(boolean bedrockOnly) {
            this.bedrockOnly = bedrockOnly;
            return this;
        }

        @Override
        public Builder<T> executor(@NonNull CommandExecutor<T> executor) {
            this.executor = executor;
            return this;
        }

        @NonNull
        @Override
        public GeyserExtensionCommand build() {
            final Class<? extends T> sourceType = this.sourceType;
            final boolean suggestedOpOnly = this.suggestedOpOnly;
            final CommandExecutor<T> executor = this.executor;

            if (sourceType == null) {
                throw new IllegalArgumentException("Source type was not defined for command " + name + " in extension " + extension.name());
            }
            if (executor == null) {
                throw new IllegalArgumentException("Command executor was not defined for command " + name + " in extension " + extension.name());
            }

            // if the source type is a GeyserConnection then it is inherently bedrockOnly
            final boolean bedrockOnly = GeyserConnection.class.isAssignableFrom(sourceType) || this.bedrockOnly;
            // a similar check would exist for executableOnConsole, but there is not a logger type exposed in the api

            GeyserExtensionCommand command = new GeyserExtensionCommand(extension, name, description, permission, permissionDefault, executableOnConsole, bedrockOnly) {

                @Override
                public void register(CommandManager<GeyserCommandSource> manager) {
                    // todo: if we don't find a way to expose cloud in the api, we should implement a way
                    //  to not have the [args] if its not necessary for this command. and maybe tab completion.
                    manager.command(baseBuilder(manager)
                        .argument(StringArgument.optional("args", StringArgument.StringMode.GREEDY))
                        .handler(this::execute));
                }

                @SuppressWarnings("unchecked")
                @Override
                public void execute(CommandContext<GeyserCommandSource> context) {
                    GeyserCommandSource source = context.getSender();
                    String[] args = context.getOrDefault("args", "").split(" ");

                    if (sourceType.isInstance(source)) {
                        executor.execute((T) source, this, args);
                        return;
                    }

                    GeyserSession session = source.connection().orElse(null);
                    if (sourceType.isInstance(session)) {
                        executor.execute((T) session, this, args);
                        return;
                    }

                    // currently, the only subclass of CommandSource exposed in the api is GeyserConnection.
                    // when this command was registered, we enabled bedrockOnly if the sourceType was a GeyserConnection.
                    // as a result, the permission checker should handle that case and this method shouldn't even be reached.
                    source.sendMessage("You must be a " + sourceType.getSimpleName() + " to run this command.");
                }

                @Override
                public boolean isSuggestedOpOnly() {
                    return suggestedOpOnly;
                }
            };

            command.aliases = aliases != null ? new ArrayList<>(aliases) : Collections.emptyList();
            return command;
        }
    }
}
