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

package org.geysermc.geyser.api.connection;

import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.geysermc.api.connection.Connection;
import org.geysermc.geyser.api.bedrock.camera.CameraFade;
import org.geysermc.geyser.api.bedrock.camera.CameraPerspective;
import org.geysermc.geyser.api.bedrock.camera.CameraPosition;
import org.geysermc.geyser.api.bedrock.camera.CameraShake;
import org.geysermc.geyser.api.command.CommandSource;
import org.geysermc.geyser.api.entity.type.GeyserEntity;
import org.geysermc.geyser.api.entity.type.player.GeyserPlayerEntity;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a player connection used in Geyser.
 */
public interface GeyserConnection extends Connection, CommandSource {
    /**
     * @param javaId the Java entity ID to look up.
     * @return a {@link GeyserEntity} if present in this connection's entity tracker.
     */
    @NonNull
    CompletableFuture<@Nullable GeyserEntity> entityByJavaId(@NonNegative int javaId);

    /**
     * Displays a player entity as emoting to this client.
     *
     * @param emoter the player entity emoting.
     * @param emoteId the emote ID to send to this client.
     */
    void showEmote(@NonNull GeyserPlayerEntity emoter, @NonNull String emoteId);

    /**
     * Shakes the client's camera.<br><br>
     * If the camera is already shaking with the same {@link CameraShake} type, then the additional intensity
     * will be layered on top of the existing intensity, with their own distinct durations.<br>
     * If the existing shake type is different and the new intensity/duration are not positive, the existing shake only
     * switches to the new type. Otherwise, the existing shake is completely overridden.
     *
     * @param intensity the intensity of the shake. The client has a maximum total intensity of 4.
     * @param duration the time in seconds that the shake will occur for
     * @param type the type of shake
     */
    void shakeCamera(float intensity, float duration, @NonNull CameraShake type);

    /**
     * Stops all camera shake of any type.
     */
    void stopCameraShake();

    /**
     * Sends a camera instruction to the client.
     * If an existing camera fade is already in progress, the current fade will be prolonged.
     * Can be built using {@link CameraFade.Builder}.
     *
     * @param fade the camera fade to send
     */
    void sendCameraFade(CameraFade fade);

    /**
     * Sends a camera instruction to the client.
     * If an existing camera movement is already in progress:
     * The (optional) camera fade will be added on top of the existing fade, and
     * the final camera position will be the one of the latest instruction.
     * Can be built using {@link CameraPosition.Builder}.
     *
     * @param position the camera position to send
     */
    void sendCameraPosition(CameraPosition position);

    /**
     * Stops all sent camera instructions (fades, movements, and perspective locks).
     * This will not stop any camera shakes/input locks/fog effects, use the respective methods for those.
     */
    void clearCameraInstructions();

    /**
     * Forces a {@link CameraPerspective} on the client. This will prevent
     * the client from changing their camera perspective until it is unlocked via {@link #clearCameraInstructions()}.
     * <p>
     * Note: You cannot force a client into a free camera perspective with this method.
     * To do that, send a {@link CameraPosition} via {@link #sendCameraPosition(CameraPosition)} - it requires a set position
     * instead of being relative to the player.
     *
     * @param perspective the {@link CameraPerspective} to force.
     */
    void forceCameraPerspective(@NonNull CameraPerspective perspective);

    /**
     * Gets the client's current {@link CameraPerspective}, if forced.
     * This will return {@code null} if the client is not currently forced into a perspective.
     * If a perspective is forced, the client will not be able to change their camera perspective until it is unlocked
     *
     * @return the forced perspective, or {@code null} if none is forced.
     */
    @Nullable CameraPerspective forcedCameraPerspective();

    /**
     * Adds the given fog IDs to the fog cache, then sends all fog IDs in the cache to the client.
     * <p>
     * Fog IDs can be found <a href="https://wiki.bedrock.dev/documentation/fog-ids.html">here</a>
     *
     * @param fogNameSpaces the fog IDs to add. If empty, the existing cached IDs will still be sent.
     */
    void sendFog(String... fogNameSpaces);

    /**
     * Removes the given fog IDs from the fog cache, then sends all fog IDs in the cache to the client.
     *
     * @param fogNameSpaces the fog IDs to remove. If empty, all fog IDs will be removed.
     */
    void removeFog(String... fogNameSpaces);

    /**
     * Locks/Unlocks the client's ability to move or look around.
     *
     * @param camera whether to lock the camera (prevents looking around)
     * @param movement whether to lock movement (prevents moving around)
     */
    void lockInputs(boolean camera, boolean movement);

    /**
     * Unlocks the client's ability to move or look around.
     */
    void unlockInputs();

    /**
     * Returns an immutable copy of all fog affects currently applied to this client.
     */
    @NonNull
    Set<String> fogEffects();

    /**
     * Returns the {@link GeyserPlayerEntity} of this connection.
     */
    @NonNull
    GeyserPlayerEntity playerEntity();
}
