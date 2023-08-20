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

package org.geysermc.geyser.session.cache;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.Getter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.preference.Preference;
import org.geysermc.geyser.api.preference.PreferenceKey;
import org.geysermc.geyser.configuration.CooldownType;
import org.geysermc.geyser.configuration.GeyserConfiguration;
import org.geysermc.geyser.preference.CooldownPreference;
import org.geysermc.geyser.preference.CustomSkullsPreference;
import org.geysermc.geyser.preference.ShowCoordinatesPreference;
import org.geysermc.geyser.session.GeyserSession;

import java.util.Map;
import java.util.Optional;

public class PreferencesCache {
    private final GeyserSession session;

    @Getter
    private final Map<PreferenceKey<?>, Preference<?>> preferences = new Object2ObjectOpenHashMap<>();

    public PreferencesCache(GeyserSession session) {
        this.session = session;

        register(CooldownPreference.KEY, new CooldownPreference(session));
        register(CustomSkullsPreference.KEY, new CustomSkullsPreference(session));
        register(ShowCoordinatesPreference.KEY, new ShowCoordinatesPreference(session));
    }

    public <T> void register(PreferenceKey<T> key, Preference<T> preference) {
        if (preference == null) {
            throw new IllegalArgumentException("preference cannot be null");
        }
        preferences.put(key, preference);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public <T> Preference<T> require(PreferenceKey<T> key) throws IllegalArgumentException {
        Preference<T> preference = (Preference<T>) preferences.get(key);
        if (preference == null) {
            throw new IllegalArgumentException("preference with key " + key + " is not stored for session " + session.javaUuid());
        }
        return preference;
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public <T> Optional<Preference<T>> get(PreferenceKey<T> key) {
        return Optional.ofNullable((Preference<T>) preferences.get(key));
    }

    /**
     * Tell the client to hide or show the coordinates. The client's preference will be overridden if either of the
     * following are true:
     * <br><br>
     * {@link GeyserSession#isReducedDebugInfo} is enabled.<br>
     * {@link GeyserConfiguration#isShowCoordinates()} is disabled.
     */
    public void updateShowCoordinates() {
        Preference<Boolean> preference = require(ShowCoordinatesPreference.KEY);
        // preference itself won't be any different, but trigger an update anyway in case
        // reduced-debug-info has changed or the config has changed
        preference.onUpdate(session);
    }

    public boolean getEffectiveShowSkulls() {
        if (!session.getGeyser().getConfig().isAllowCustomSkulls()) {
            return false;
        }
        return require(CustomSkullsPreference.KEY).value();
    }


    public CooldownType getEffectiveCooldown() {
        if (session.getGeyser().getConfig().getShowCooldown() == CooldownType.DISABLED) {
            return CooldownType.DISABLED;
        }
        return require(CooldownPreference.KEY).value();
    }
}
