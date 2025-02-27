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

package org.geysermc.geyser.extension;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionLoader;
import org.geysermc.geyser.api.extension.ExtensionManager;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class GeyserExtensionManager extends ExtensionManager {
    private final GeyserExtensionLoader extensionLoader = new GeyserExtensionLoader();
    private final Map<String, Extension> extensions = new LinkedHashMap<>();

    public void init() {
        GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.extensions.load.loading"));

        loadAllExtensions(this.extensionLoader);
        enableExtensions();

        GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.extensions.load.done", this.extensions.size()));
    }

    @Override
    public Extension extension(@NonNull String id) {
        return this.extensions.get(id);
    }

    @Override
    public void enable(@NonNull Extension extension) {
        if (!extension.isEnabled()) {
            try {
                this.enableExtension(extension);
            } catch (Exception e) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.extensions.enable.failed", extension.name()), e);
                this.disable(extension);
            }
        }
    }

    @Override
    public void disable(@NonNull Extension extension) {
        if (extension.isEnabled()) {
            try {
                this.disableExtension(extension);
            } catch (Exception e) {
                GeyserLogger.get().error(GeyserLocale.getLocaleStringLog("geyser.extensions.disable.failed", extension.name()), e);
            }
        }
    }

    public void enableExtension(Extension extension) {
        if (!extension.isEnabled()) {
            extension.setEnabled(true);
            GeyserImpl.getInstance().eventBus().register(extension, extension);
            GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.extensions.enable.success", extension.name()));
        }
    }

    public void enableExtensions() {
        for (Extension extension : this.extensions()) {
            this.enable(extension);
        }
    }

    private void disableExtension(@NonNull Extension extension) {
        if (extension.isEnabled()) {
            GeyserImpl.getInstance().eventBus().unregisterAll(extension);

            extension.setEnabled(false);
            GeyserLogger.get().info(GeyserLocale.getLocaleStringLog("geyser.extensions.disable.success", extension.name()));
        }
    }

    public void disableExtensions() {
        for (Extension extension : this.extensions()) {
            this.disable(extension);
        }
    }

    @NonNull
    @Override
    public Collection<Extension> extensions() {
        return Collections.unmodifiableCollection(this.extensions.values());
    }

    @Override
    public @Nullable ExtensionLoader extensionLoader() {
        return this.extensionLoader;
    }

    @Override
    public void register(@NonNull Extension extension) {
        this.extensions.put(extension.description().id(), extension);
    }
}
