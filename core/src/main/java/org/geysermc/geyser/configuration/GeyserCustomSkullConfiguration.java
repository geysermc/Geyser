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

package org.geysermc.geyser.configuration;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@ConfigSerializable
@SuppressWarnings("FieldMayBeFinal") // Jackson requires that the fields are not final
public class GeyserCustomSkullConfiguration {
    private List<String> playerUsernames;

    private List<String> playerUUIDs;

    private List<String> playerProfiles;

    private List<String> skinHashes;

    public List<String> getPlayerUsernames() {
        return Objects.requireNonNullElse(playerUsernames, Collections.emptyList());
    }

    public List<String> getPlayerUUIDs() {
        return Objects.requireNonNullElse(playerUUIDs, Collections.emptyList());
    }

    public List<String> getPlayerProfiles() {
        return Objects.requireNonNullElse(playerProfiles, Collections.emptyList());
    }

    public List<String> getPlayerSkinHashes() {
        return Objects.requireNonNullElse(skinHashes, Collections.emptyList());
    }

    @Override
    public String toString() {
        return "GeyserCustomSkullConfiguration{" +
            "playerUsernames=" + playerUsernames +
            ", playerUUIDs=" + playerUUIDs +
            ", playerProfiles=" + playerProfiles +
            ", skinHashes=" + skinHashes +
            '}';
    }
}
