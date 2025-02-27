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

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.extension.ExtensionDescription;
import org.geysermc.geyser.api.extension.exception.InvalidExtensionException;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class GeyserExtensionClassLoader extends URLClassLoader {
    private final GeyserExtensionLoader loader;
    private final ExtensionDescription description;
    private final Object2ObjectMap<String, Class<?>> classes = new Object2ObjectOpenHashMap<>();
    private boolean warnedForExternalClassAccess;

    public GeyserExtensionClassLoader(GeyserExtensionLoader loader, ClassLoader parent, Path path, ExtensionDescription description) throws MalformedURLException {
        super(new URL[] { path.toUri().toURL() }, parent);
        this.loader = loader;
        this.description = description;
    }

    public Extension load() throws InvalidExtensionException {
        try {
            Class<?> jarClass;
            try {
                jarClass = Class.forName(description.main(), true, this);
            } catch (ClassNotFoundException ex) {
                throw new InvalidExtensionException("Class " + description.main() + " not found, extension cannot be loaded", ex);
            }

            Class<? extends Extension> extensionClass;
            try {
                extensionClass = jarClass.asSubclass(Extension.class);
            } catch (ClassCastException ex) {
                throw new InvalidExtensionException("Main class " + description.main() + " should implement Extension, but extends " + jarClass.getSuperclass().getSimpleName(), ex);
            }

            return extensionClass.getConstructor().newInstance();
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException ex) {
            throw new InvalidExtensionException("No public constructor", ex);
        } catch (InstantiationException ex) {
            throw new InvalidExtensionException("Abnormal extension type", ex);
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return this.findClass(name, true);
    }

    protected Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
        Class<?> result = this.classes.get(name);
        if (result == null) {
            // Try to find class in current extension
            try {
                result = super.findClass(name);
            } catch (ClassNotFoundException ignored) {
                // If class is not found in current extension, check in the global class loader
                // This is used for classes that are not in the extension, but are in other extensions
                if (checkGlobal) {
                    if (!warnedForExternalClassAccess) {
                        GeyserLogger.get().warning("Extension " + this.description.name() + " loads class " + name + " from an external source. " +
                                "This can change at any time and break the extension, additionally to potentially causing unexpected behaviour!");
                        warnedForExternalClassAccess = true;
                    }
                    result = this.loader.classByName(name);
                }
            }

            if (result != null) {
                // If class is found, cache it
                this.loader.setClass(name, result);
                this.classes.put(name, result);
            } else {
                // If class is not found, throw exception
                throw new ClassNotFoundException(name);
            }
        }
        return result;
    }
}
