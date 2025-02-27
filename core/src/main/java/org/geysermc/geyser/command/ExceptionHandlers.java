/*
 * Copyright (c) 2019-2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.command;

import io.leangen.geantyref.GenericTypeReflector;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.text.ChatColor;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.geyser.text.MinecraftLocale;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.exception.ArgumentParseException;
import org.incendo.cloud.exception.CommandExecutionException;
import org.incendo.cloud.exception.InvalidCommandSenderException;
import org.incendo.cloud.exception.InvalidSyntaxException;
import org.incendo.cloud.exception.NoPermissionException;
import org.incendo.cloud.exception.NoSuchCommandException;
import org.incendo.cloud.exception.handling.ExceptionController;

import java.lang.reflect.Type;
import java.util.function.BiConsumer;

/**
 * Geyser's exception handlers for command execution with Cloud.
 * Overrides Cloud's defaults so that messages can be customized to our liking: localization, etc.
 */
final class ExceptionHandlers {

    final static String PERMISSION_FAIL_LANG_KEY = "geyser.command.permission_fail";

    private final ExceptionController<GeyserCommandSource> controller;

    private ExceptionHandlers(ExceptionController<GeyserCommandSource> controller) {
        this.controller = controller;
    }

    /**
     * Clears the existing handlers that are registered to the given command manager, and repopulates them.
     *
     * @param manager the manager whose exception handlers will be modified
     */
    static void register(CommandManager<GeyserCommandSource> manager) {
        new ExceptionHandlers(manager.exceptionController()).register();
    }

    private void register() {
        // Yeet the default exception handlers that cloud provides so that we can perform localization.
        controller.clearHandlers();

        registerExceptionHandler(InvalidSyntaxException.class,
            (src, e) -> src.sendLocaleString("geyser.command.invalid_syntax", e.correctSyntax()));

        registerExceptionHandler(InvalidCommandSenderException.class, (src, e) -> {
            // We currently don't use cloud sender type requirements anywhere.
            // This can be implemented better in the future if necessary.
            Type type = e.requiredSenderTypes().iterator().next(); // just grab the first
            String typeString = GenericTypeReflector.getTypeName(type);
            src.sendLocaleString("geyser.command.invalid_sender", e.commandSender().getClass().getSimpleName(), typeString);
        });

        registerExceptionHandler(NoPermissionException.class, ExceptionHandlers::handleNoPermission);

        registerExceptionHandler(NoSuchCommandException.class,
            (src, e) -> src.sendLocaleString("geyser.command.not_found"));

        registerExceptionHandler(ArgumentParseException.class,
            (src, e) -> src.sendLocaleString("geyser.command.invalid_argument", e.getCause().getMessage()));

        registerExceptionHandler(CommandExecutionException.class,
            (src, e) -> handleUnexpectedThrowable(src, e.getCause()));

        registerExceptionHandler(Throwable.class,
            (src, e) -> handleUnexpectedThrowable(src, e.getCause()));
    }

    private <E extends Throwable> void registerExceptionHandler(Class<E> type, BiConsumer<GeyserCommandSource, E> handler) {
        controller.registerHandler(type, context -> handler.accept(context.context().sender(), context.exception()));
    }

    private static void handleNoPermission(GeyserCommandSource source, NoPermissionException exception) {
        // custom handling if the source can't use the command because of additional requirements
        if (exception.permissionResult() instanceof GeyserPermission.Result result) {
            if (result.meta() == GeyserPermission.Result.Meta.NOT_BEDROCK) {
                source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.command.bedrock_only", source.locale()));
                return;
            }
            if (result.meta() == GeyserPermission.Result.Meta.NOT_PLAYER) {
                source.sendMessage(ChatColor.RED + GeyserLocale.getPlayerLocaleString("geyser.command.player_only", source.locale()));
                return;
            }
        } else {
            GeyserLogger logger = GeyserLogger.get();
            if (logger.isDebug()) {
                logger.debug("Expected a GeyserPermission.Result for %s but instead got %s from %s".formatted(exception.currentChain(), exception.permissionResult(), exception.missingPermission()));
            }
        }

        // Result.NO_PERMISSION or generic permission failure
        source.sendLocaleString(PERMISSION_FAIL_LANG_KEY);
    }

    private static void handleUnexpectedThrowable(GeyserCommandSource source, Throwable throwable) {
        source.sendMessage(MinecraftLocale.getLocaleString("command.failed", source.locale())); // java edition translation key
        GeyserLogger.get().error("Exception while executing command handler", throwable);
    }
}
