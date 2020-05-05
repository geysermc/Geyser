/*
 * Copyright (c) 2019-2020 GeyserMC. http://geysermc.org
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 *
 *  @author GeyserMC
 *  @link https://github.com/GeyserMC/Geyser
 *
 */

package org.geysermc.connector.network.translators.sound.block;

import com.nukkitx.math.vector.Vector3f;
import com.nukkitx.protocol.bedrock.data.SoundEvent;
import com.nukkitx.protocol.bedrock.packet.LevelSoundEventPacket;
import org.geysermc.connector.network.session.GeyserSession;
import org.geysermc.connector.network.translators.Translators;
import org.geysermc.connector.network.translators.sound.BlockSoundInteractionHandler;
import org.geysermc.connector.network.translators.sound.SoundHandler;

@SoundHandler(items = "bucket")
public class BucketSoundInteractionHandler implements BlockSoundInteractionHandler {

    @Override
    public void handleInteraction(GeyserSession session, Vector3f position, String identifier) {
        String handItemIdentifier = Translators.getItemTranslator().getItem(session.getInventory().getItemInHand()).getJavaIdentifier();
        LevelSoundEventPacket soundEventPacket = new LevelSoundEventPacket();
        soundEventPacket.setPosition(position);
        soundEventPacket.setIdentifier(":");
        soundEventPacket.setRelativeVolumeDisabled(false);
        soundEventPacket.setBabySound(false);
        soundEventPacket.setExtraData(-1);
        SoundEvent soundEvent = null;
        switch (handItemIdentifier) {
            case "minecraft:bucket":
                if (identifier.contains("water[")) {
                    soundEvent = SoundEvent.BUCKET_FILL_WATER;
                } else if (identifier.contains("lava[")) {
                    soundEvent = SoundEvent.BUCKET_FILL_LAVA;
                }
                break;
            case "minecraft:lava_bucket":
                soundEvent = SoundEvent.BUCKET_EMPTY_LAVA;
                break;
            case "minecraft:fish_bucket":
                soundEvent = SoundEvent.BUCKET_EMPTY_FISH;
                break;
            case "minecraft:water_bucket":
                soundEvent = SoundEvent.BUCKET_EMPTY_WATER;
                break;
        }
        if (soundEvent != null) {
            soundEventPacket.setSound(soundEvent);
            session.sendUpstreamPacket(soundEventPacket);
        }
    }
}
