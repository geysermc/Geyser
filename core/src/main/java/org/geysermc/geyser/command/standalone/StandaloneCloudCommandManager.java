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

package org.geysermc.geyser.command.standalone;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.event.lifecycle.GeyserRegisterPermissionCheckersEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserRegisterPermissionsEvent;
import org.geysermc.geyser.api.permission.PermissionChecker;
import org.geysermc.geyser.api.util.TriState;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.util.FileUtils;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class StandaloneCloudCommandManager extends CommandManager<GeyserCommandSource> {

    private final GeyserImpl geyser;

    /**
     * The checkers we use to test if a command source has a permission
     */
    private final List<PermissionChecker> permissionCheckers = new ArrayList<>();

    /**
     * Any permissions that all connections have
     */
    private final Set<String> basePermissions = new ObjectOpenHashSet<>();

    public StandaloneCloudCommandManager(GeyserImpl geyser) {
        super(ExecutionCoordinator.simpleCoordinator(), CommandRegistrationHandler.nullCommandRegistrationHandler());
        // simpleCoordinator: execute commands immediately on the calling thread.
        // nullCommandRegistrationHandler: cloud is not responsible for handling our CommandRegistry, which is fairly decoupled.
        this.geyser = geyser;

        // allow any extensions to customize permissions
        geyser.getEventBus().fire((GeyserRegisterPermissionCheckersEvent) permissionCheckers::add);

        // must still implement a basic permission system
        try {
            File permissionsFile = geyser.getBootstrap().getConfigFolder().resolve("permissions.yml").toFile();
            FileUtils.fileOrCopiedFromResource(permissionsFile, "permissions.yml", geyser.getBootstrap());
            PermissionConfiguration config = FileUtils.loadConfig(permissionsFile, PermissionConfiguration.class);
            basePermissions.addAll(config.getDefaultPermissions());
        } catch (Exception e) {
            GeyserLogger.get().error("Failed to load permissions.yml - proceeding without it", e);
        }
    }

    /**
     * Fire a {@link GeyserRegisterPermissionsEvent} to determine any additions or removals to the base list of
     * permissions. This should be called after any event listeners have been registered, such as that of {@link CommandRegistry}.
     */
    public void fireRegisterPermissionsEvent() {
        geyser.getEventBus().fire((GeyserRegisterPermissionsEvent) (permission, def) -> {
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(def, "permission default for " + permission);

            if (permission.isBlank()) {
                return;
            }
            if (def == TriState.TRUE) {
                basePermissions.add(permission);
            }
        });
    }

    @Override
    public boolean hasPermission(@NonNull GeyserCommandSource sender, @NonNull String permission) {
        // Note: the two GeyserCommandSources on Geyser-Standalone are GeyserLogger and GeyserSession
        // GeyserLogger#hasPermission always returns true
        // GeyserSession#hasPermission delegates to this method,
        // which is why this method doesn't just call GeyserCommandSource#hasPermission
        if (sender.isConsole()) {
            return true;
        }

        // An empty or blank permission is treated as a lack of permission requirement
        if (permission.isBlank()) {
            return true;
        }

        for (PermissionChecker checker : permissionCheckers) {
            Boolean result = checker.hasPermission(sender, permission).toBoolean();
            if (result != null) {
                return result;
            }
            // undefined - try the next checker to see if it has a defined value
        }
        // fallback to our list of default permissions
        // note that a PermissionChecker may in fact override any values set here by returning FALSE
        return basePermissions.contains(permission);
    }
}
