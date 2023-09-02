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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.command.GeyserCommand;

public abstract class GeyserExtensionCommand extends GeyserCommand {

    private final Extension extension;
    private final String rootCommand;

    public GeyserExtensionCommand(@NonNull Extension extension, @NonNull String name, @Nullable String description, @Nullable String permission, boolean executableOnConsole, boolean bedrockOnly) {
        super(name, description, permission, executableOnConsole, bedrockOnly);
        this.extension = extension;
        this.rootCommand = extension.rootCommand();

        if (this.rootCommand == null || this.rootCommand.isBlank()) {
            throw new IllegalStateException("rootCommand of extension " + extension.name() + " may not be null or blank");
        }
    }

    public final Extension extension() {
        return this.extension;
    }

    @Override
    public final String rootCommand() {
        return this.rootCommand;
    }
}
