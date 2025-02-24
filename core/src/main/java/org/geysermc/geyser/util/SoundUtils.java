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

package org.geysermc.geyser.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.protocol.bedrock.data.LevelEvent;
import org.cloudburstmc.protocol.bedrock.data.SoundEvent;
import org.cloudburstmc.protocol.bedrock.packet.LevelEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.LevelSoundEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlaySoundPacket;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.registry.type.SoundMapping;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.Sound;

import java.util.Locale;

public final class SoundUtils {

    /**
     * Maps a sound name to a sound event, null if one
     * does not exist.
     *
     * @param sound the sound name
     * @return a sound event from the given sound
     */
    public static @Nullable SoundEvent toSoundEvent(String sound) {
        try {
            return SoundEvent.valueOf(sound.toUpperCase(Locale.ROOT).replace(".", "_"));
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Translates a Java Custom or Builtin Sound to its Bedrock equivalent
     *
     * @param javaIdentifier the sound to translate
     * @return a Bedrock sound
     */
    public static String translatePlaySound(String javaIdentifier) {
        String soundIdentifier = removeMinecraftNamespace(javaIdentifier);
        SoundMapping soundMapping = Registries.SOUNDS.get(soundIdentifier);
        if (soundMapping == null || soundMapping.playsound() == null) {
            // no mapping
            GeyserImpl.getInstance().getLogger().debug("[PlaySound] Defaulting to sound server gave us for " + javaIdentifier);
            return soundIdentifier;
        }
        return soundMapping.playsound();
    }

    private static String removeMinecraftNamespace(String identifier) {
        // Drop any minecraft namespace if applicable
        if (identifier.startsWith("minecraft:")) {
            return identifier.substring("minecraft:".length());
        }
        return identifier;
    }

    private static void playSound(GeyserSession session, String bedrockName, Vector3f position, float volume, float pitch) {
        PlaySoundPacket playSoundPacket = new PlaySoundPacket();
        playSoundPacket.setSound(bedrockName);
        playSoundPacket.setPosition(position);
        playSoundPacket.setVolume(volume);
        playSoundPacket.setPitch(pitch);
        session.sendUpstreamPacket(playSoundPacket);
    }

    /**
     * Translates and plays a Java Builtin Sound for a Bedrock client
     *
     * @param session the Bedrock client session.
     * @param javaSound the builtin sound to play
     * @param position the position
     * @param pitch the pitch
     */
    public static void playSound(GeyserSession session, Sound javaSound, Vector3f position, float volume, float pitch) {
        String soundIdentifier = removeMinecraftNamespace(javaSound.getName());

        SoundMapping soundMapping = Registries.SOUNDS.get(soundIdentifier);
        if (soundMapping == null) {
            session.getGeyser().getLogger().debug("[Builtin] Sound mapping for " + soundIdentifier + " not found; assuming custom.");
            playSound(session, soundIdentifier, position, volume, pitch);
            return;
        }

        if (soundMapping.playsound() != null) {
            // We always prefer the PlaySound mapping because we can control volume and pitch
            playSound(session, soundMapping.playsound(), position, volume, pitch * soundMapping.pitchAdjust());
            return;
        }

        if (soundMapping.levelEvent()) {
            LevelEventPacket levelEventPacket = new LevelEventPacket();
            levelEventPacket.setPosition(position);
            levelEventPacket.setData(0);
            levelEventPacket.setType(LevelEvent.valueOf(soundMapping.bedrock()));
            session.sendUpstreamPacket(levelEventPacket);
            return;
        }

        LevelSoundEventPacket soundPacket = new LevelSoundEventPacket();
        SoundEvent sound = SoundUtils.toSoundEvent(soundMapping.bedrock());
        if (sound == null) {
            sound = SoundUtils.toSoundEvent(soundIdentifier);
        }
        if (sound == null) {
            session.getGeyser().getLogger().debug("[Builtin] Sound for original '" + soundIdentifier + "' to mappings '" + soundMapping.bedrock()
                + "' was not a playable level sound, or has yet to be mapped to an enum in SoundEvent.");
            return;
        }

        soundPacket.setSound(sound);
        soundPacket.setPosition(position);
        soundPacket.setIdentifier(soundMapping.identifier());
        if (sound == SoundEvent.NOTE) {
            // Minecraft Wiki: 2^(x/12) = Java pitch where x is -12 to 12
            // Java sends the note value as above starting with -12 and ending at 12
            // Bedrock has a number for each type of note, then proceeds up the scale by adding to that number
            soundPacket.setExtraData(soundMapping.extraData() + (int) (Math.round((Math.log10(pitch) / Math.log10(2)) * 12)) + 12);
        } else if (sound == SoundEvent.PLACE && soundMapping.extraData() == -1) {
            if (!soundMapping.identifier().equals(":")) {
                int javaId = BlockRegistries.JAVA_IDENTIFIER_TO_ID.get().getOrDefault(soundMapping.identifier(), Block.JAVA_AIR_ID);
                soundPacket.setExtraData(session.getBlockMappings().getBedrockBlockId(javaId));
            } else {
                session.getGeyser().getLogger().debug("PLACE sound mapping identifier was invalid! Please report: " + soundMapping);
            }
            soundPacket.setIdentifier(":");
        } else {
            soundPacket.setExtraData(soundMapping.extraData());
        }

        soundPacket.setBabySound(false); // might need to adjust this in the future
        soundPacket.setRelativeVolumeDisabled(false);
        session.sendUpstreamPacket(soundPacket);
    }

    public static String readSoundEvent(NbtMap data, String context) {
        Object soundEventObject = data.get("sound_event");
        String soundEvent;
        if (soundEventObject instanceof NbtMap map) {
            soundEvent = map.getString("sound_id");
        } else if (soundEventObject instanceof String string) {
            soundEvent = string;
        } else {
            soundEvent = "";
            GeyserImpl.getInstance().getLogger().debug("Sound event for " + context + " was of an unexpected type! Expected string or NBT map, got " + soundEventObject);
        }
        return soundEvent;
    }

    private SoundUtils() {
    }
}
