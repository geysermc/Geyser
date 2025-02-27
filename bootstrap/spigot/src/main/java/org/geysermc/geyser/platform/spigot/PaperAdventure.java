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

package org.geysermc.geyser.platform.spigot;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.mcprotocollib.protocol.data.DefaultComponentSerializer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class for converting our shaded Adventure into the Adventure bundled in Paper.
 * <p>
 * Code mostly taken from <a href="https://github.com/KyoriPowered/adventure-platform/blob/94d5821f2e755170f42bd8a5fe1d5bf6f66d04ad/platform-bukkit/src/main/java/net/kyori/adventure/platform/bukkit/PaperFacet.java#L46">here</a>
 * and the MinecraftReflection class.
 */
public final class PaperAdventure {
    private static final MethodHandle NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND;
    private static final Method SEND_MESSAGE_COMPONENT;

    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();

        MethodHandle nativeGsonComponentSerializerDeserializeMethodBound = null;

        // String.join because otherwise the class name will be relocated
        final Class<?> nativeGsonComponentSerializerClass = findClass(String.join(".",
                "net", "kyori", "adventure", "text", "serializer", "gson", "GsonComponentSerializer"));
        final Class<?> nativeGsonComponentSerializerImplClass = findClass(String.join(".",
                "net", "kyori", "adventure", "text", "serializer", "gson", "GsonComponentSerializerImpl"));
        if (nativeGsonComponentSerializerClass != null && nativeGsonComponentSerializerImplClass != null) {
            MethodHandle nativeGsonComponentSerializerGsonGetter = null;
            try {
                nativeGsonComponentSerializerGsonGetter = lookup.findStatic(nativeGsonComponentSerializerClass,
                        "gson", MethodType.methodType(nativeGsonComponentSerializerClass));
            } catch (final NoSuchMethodException | IllegalAccessException ignored) {
            }

            MethodHandle nativeGsonComponentSerializerDeserializeMethod = null;
            try {
                final Method method = nativeGsonComponentSerializerImplClass.getDeclaredMethod("deserialize", String.class);
                method.setAccessible(true);
                nativeGsonComponentSerializerDeserializeMethod = lookup.unreflect(method);
            } catch (final NoSuchMethodException | IllegalAccessException ignored) {
            }

            if (nativeGsonComponentSerializerGsonGetter != null) {
                if (nativeGsonComponentSerializerDeserializeMethod != null) {
                    try {
                        nativeGsonComponentSerializerDeserializeMethodBound = nativeGsonComponentSerializerDeserializeMethod
                                .bindTo(nativeGsonComponentSerializerGsonGetter.invoke());
                    } catch (final Throwable throwable) {
                        GeyserLogger.get().error("Failed to access native GsonComponentSerializer", throwable);
                    }
                }
            }
        }

        NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND = nativeGsonComponentSerializerDeserializeMethodBound;

        Method playerComponentSendMessage = null;
        final Class<?> nativeComponentClass = findClass(String.join(".",
                "net", "kyori", "adventure", "text", "Component"));
        if (nativeComponentClass != null) {
            try {
                playerComponentSendMessage = CommandSender.class.getMethod("sendMessage", nativeComponentClass);
            } catch (final NoSuchMethodException e) {
                if (GeyserLogger.get().isDebug()) {
                    e.printStackTrace();
                }
            }
        }
        SEND_MESSAGE_COMPONENT = playerComponentSendMessage;
    }

    public static @Nullable Object toNativeComponent(final Component component) {
        if (NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND == null) {
            GeyserLogger.get().error("Illegal state where Component serialization was called when it wasn't available!");
            return null;
        }

        try {
            return NATIVE_GSON_COMPONENT_SERIALIZER_DESERIALIZE_METHOD_BOUND.invoke(DefaultComponentSerializer.get().serialize(component));
        } catch (final Throwable throwable) {
            GeyserLogger.get().error("Failed to create native Component message", throwable);
            return null;
        }
    }

    public static void sendMessage(final CommandSender sender, final Component component) {
        if (SEND_MESSAGE_COMPONENT == null) {
            GeyserLogger.get().error("Illegal state where Component sendMessage was called when it wasn't available!");
            return;
        }

        final Object nativeComponent = toNativeComponent(component);
        if (nativeComponent != null) {
            try {
                SEND_MESSAGE_COMPONENT.invoke(sender, nativeComponent);
            } catch (final InvocationTargetException | IllegalAccessException e) {
                GeyserLogger.get().error("Failed to send native Component message", e);
            }
        }
    }

    public static boolean canSendMessageUsingComponent() {
        return SEND_MESSAGE_COMPONENT != null;
    }

    /**
     * Gets a class by the first name available.
     *
     * @return a class or {@code null} if not found
     */
    private static @Nullable Class<?> findClass(final String className) {
        try {
            return Class.forName(className);
        } catch (final ClassNotFoundException ignored) {
        }
        return null;
    }

    private PaperAdventure() {
    }
}
