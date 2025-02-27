/*
 * Copyright (c) 2019-2023 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.api.pack;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.geyser.api.event.bedrock.SessionLoadResourcePacksEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineResourcePacksEvent;
import org.geysermc.geyser.api.pack.option.SubpackOption;

import java.util.Collection;
import java.util.UUID;

/**
 * Represents a Bedrock edition resource pack manifest (manifest.json).
 * All resource packs are required to have such a file as it identifies the resource pack.
 * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/examples/addonmanifest?view=minecraft-bedrock-stable">
 *     Microsoft's docs for more info</a>.
 * @since 2.1.1
 */
public interface ResourcePackManifest {

    /**
     * Gets the format version of the resource pack.
     * <p>
     * "1" is used for skin packs,
     * "2" is used for resource and behavior packs, and world templates.
     *
     * @return the format version
     * @since 2.1.1
     */
    int formatVersion();

    /**
     * Gets the {@link Header} of the resource pack.
     *
     * @return the {@link Header}
     * @since 2.1.1
     */
    @NonNull
    Header header();

    /**
     * Gets the {@link Module}'s of the resource pack.
     *
     * @return a collection of modules
     * @since 2.1.1
     */
    @NonNull
    Collection<? extends Module> modules();

    /**
     * Gets the {@link Dependency}'s of the resource pack.
     *
     * @return a collection of dependencies
     * @since 2.6.2
     */
    @NonNull
    Collection<? extends Dependency> dependencies();

    /**
     * Gets the {@link Subpack}'s of the resource pack.
     * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/documents/utilizingsubpacks">Microsoft's docs on subpacks
     * </a> for more information.
     *
     * @return a collection of subpacks
     * @since 2.6.2
     */
    @NonNull
    Collection<? extends Subpack> subpacks();

    /**
     * Gets the {@link Setting}'s of the resource pack.
     * These are shown to Bedrock client's in the resource pack settings menu (<a href="https://learn.microsoft.com/en-us/minecraft/creator/documents/media/utilizingsubpacks/subpackgif.gif?view=minecraft-bedrock-stable">see here</a>)
     * to inform users about what the resource pack and sub-packs include.
     *
     * @return a collection of settings
     * @since 2.6.2
     */
    @NonNull
    Collection<? extends Setting> settings();

    /**
     * Represents the header of a resource pack.
     * It contains the main information about the resource pack, such as
     * the name, description, or uuid.
     * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/examples/addonmanifest?view=minecraft-bedrock-stable#header">
     *     Microsoft's docs for further details on headers.</a>
     * @since 2.1.1
     */
    interface Header {

        /**
         * Gets the UUID of the resource pack. It is a unique identifier that differentiates this resource pack from any other resource pack.
         * Bedrock clients will cache resource packs, and download resource packs when the uuid is new (or the version changes).
         *
         * @return the UUID
         * @since 2.1.1
         */
        @NonNull
        UUID uuid();

        /**
         * Gets the version of the resource pack.
         *
         * @return the version
         * @since 2.1.1
         */
        @NonNull
        Version version();

        /**
         * Gets the name of the resource pack.
         *
         * @return the name
         * @since 2.1.1
         */
        @NonNull
        String name();

        /**
         * Gets the description of the resource pack.
         *
         * @return the description
         * @since 2.1.1
         */
        @NonNull
        String description();

        /**
         * Gets the minimum supported Minecraft version of the resource pack.
         *
         * @return the minimum supported Minecraft version
         * @since 2.1.1
         */
        @NonNull
        Version minimumSupportedMinecraftVersion();
    }

    /**
     * Represents a module of a resource pack.
     * It contains information about the content type that is
     * offered by this resource pack.
     * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/examples/addonmanifest?view=minecraft-bedrock-stable#modules">
     *     Microsoft's docs for further details on modules.</a>
     * @since 2.1.1
     */
    interface Module {

        /**
         * Gets the UUID of the module.
         * This should usually be different from the UUID in the {@link Header}.
         *
         * @return the UUID
         * @since 2.1.1
         */
        @NonNull
        UUID uuid();

        /**
         * Gets the {@link Version} of the module.
         *
         * @return the {@link Version}
         * @since 2.1.1
         */
        @NonNull
        Version version();

        /**
         * Gets the type of the module.
         *
         * @return the type
         * @since 2.1.1
         */
        @NonNull
        String type();

        /**
         * Gets the description of the module.
         *
         * @return the description
         * @since 2.1.1
         */
        @NonNull
        String description();
    }

    /**
     * Represents a dependency of a resource pack.
     * These are references to other resource packs that must be
     * present in order for this resource pack to apply.
     * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/reference/content/addonsreference/examples/addonmanifest?view=minecraft-bedrock-stable#dependencies">
     *     Microsoft's docs for further details on dependencies.</a>
     * @since 2.1.1
     */
    interface Dependency {

        /**
         * Gets the UUID of the resource pack dependency.
         *
         * @return the uuid
         * @since 2.1.1
         */
        @NonNull
        UUID uuid();

        /**
         * Gets the {@link Version} of the dependency.
         *
         * @return the {@link Version}
         * @since 2.1.1
         */
        @NonNull
        Version version();
    }

    /**
     * Represents a subpack of a resource pack. These are often used for "variants" of the resource pack,
     * such as lesser details, or additional features either to be determined by player's taste or adapted to the player device's performance.
     * See <a href="https://learn.microsoft.com/en-us/minecraft/creator/documents/utilizingsubpacks">Micoroft's docs</a> for more information.
     */
    interface Subpack {

        /**
         * Gets the folder name where this sub-pack is placed in.
         *
         * @return the folder name
         * @since 2.6.2
         */
        @NonNull
        String folderName();

        /**
         * Gets the name of this subpack. Required for each subpack to be valid.
         * To make a Bedrock client load any subpack, register the resource pack
         * in the {@link SessionLoadResourcePacksEvent} or {@link GeyserDefineResourcePacksEvent} and specify a
         * {@link SubpackOption} with the name of the subpack to load.
         *
         * @return the subpack name
         * @since 2.6.2
         */
        @NonNull
        String name();

        /**
         * Gets the memory tier of this Subpack, representing how much RAM a device must have to run it.
         * Each memory tier requires 0.25 GB of RAM. For example, a memory tier of 0 is no requirement,
         * and a memory tier of 4 requires 1GB of RAM.
         *
         * @return the memory tier
         * @since 2.6.2
         */
        @Nullable
        Float memoryTier();
    }

    /**
     * Represents a setting that is shown client-side that describe what a pack does.
     * Multiple setting entries are shown in separate paragraphs.
     * @since 2.6.2
     */
    interface Setting {

        /**
         * The type of the setting. Usually just "label".
         *
         * @return the type
         * @since 2.6.2
         */
        @NonNull
        String type();

        /**
         * The text shown for the setting.
         *
         * @return the text content
         * @since 2.6.2
         */
        @NonNull
        String text();
    }

    /**
     * Represents a version of a resource pack.
     * @since 2.1.1
     */
    interface Version {

        /**
         * Gets the major version.
         *
         * @return the major version
         * @since 2.1.1
         */
        int major();

        /**
         * Gets the minor version.
         *
         * @return the minor version
         * @since 2.1.1
         */
        int minor();

        /**
         * Gets the patch version.
         *
         * @return the patch version
         * @since 2.1.1
         */
        int patch();

        /**
         * Gets the version formatted as a String.
         *
         * @return the version string
         * @since 2.1.1
         */
        @NonNull String toString();
    }
}

