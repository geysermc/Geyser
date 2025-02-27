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

package org.geysermc.geyser.translator.protocol.java.entity;

import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.AnimateEntityPacket;
import org.cloudburstmc.protocol.bedrock.packet.AnimatePacket;
import org.cloudburstmc.protocol.bedrock.packet.EntityEventPacket;
import org.cloudburstmc.protocol.bedrock.packet.SpawnParticleEffectPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.LivingEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.DimensionUtils;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundAnimatePacket;

import java.util.Optional;

@Translator(packet = ClientboundAnimatePacket.class)
public class JavaAnimateTranslator extends PacketTranslator<ClientboundAnimatePacket> {

    @Override
    public void translate(GeyserSession session, ClientboundAnimatePacket packet) {
        Animation animation = packet.getAnimation();
        if (animation == null) {
            return;
        }
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());
        if (entity == null) {
            return;
        }

        AnimatePacket animatePacket = new AnimatePacket();
        animatePacket.setRuntimeEntityId(entity.getGeyserId());
        switch (animation) {
            case SWING_ARM -> {
                if (entity instanceof LivingEntity livingEntity && livingEntity.useArmSwingAttack()) {
                    EntityEventPacket entityEventPacket = new EntityEventPacket();
                    entityEventPacket.setRuntimeEntityId(entity.getGeyserId());
                    entityEventPacket.setType(EntityEventType.ATTACK_START);
                    session.sendUpstreamPacket(entityEventPacket);
                    return;
                }

                animatePacket.setAction(AnimatePacket.Action.SWING_ARM);
                if (entity.getEntityId() == session.getPlayerEntity().getEntityId()) {
                    session.activateArmAnimationTicking();
                }
            }
            case SWING_OFFHAND -> {
                // Use the OptionalPack to trigger the animation
                AnimateEntityPacket offHandPacket = new AnimateEntityPacket();
                offHandPacket.setAnimation("animation.player.attack.rotations.offhand");
                offHandPacket.setNextState("default");
                offHandPacket.setBlendOutTime(0.0f);
                offHandPacket.setStopExpression("query.any_animation_finished");
                offHandPacket.setController("__runtime_controller");
                offHandPacket.getRuntimeEntityIds().add(entity.getGeyserId());
                session.sendUpstreamPacket(offHandPacket);
                return;
            }
            case CRITICAL_HIT -> animatePacket.setAction(AnimatePacket.Action.CRITICAL_HIT);
            case ENCHANTMENT_CRITICAL_HIT -> {
                animatePacket.setAction(AnimatePacket.Action.MAGIC_CRITICAL_HIT); // Unsure if this does anything

                // Spawn custom particle
                SpawnParticleEffectPacket stringPacket = new SpawnParticleEffectPacket();
                stringPacket.setIdentifier("geyseropt:enchanted_hit_multiple");
                stringPacket.setDimensionId(DimensionUtils.javaToBedrock(session));
                stringPacket.setPosition(Vector3f.ZERO);
                stringPacket.setUniqueEntityId(entity.getGeyserId());
                stringPacket.setMolangVariablesJson(Optional.empty());
                session.sendUpstreamPacket(stringPacket);
            }
            case LEAVE_BED -> animatePacket.setAction(AnimatePacket.Action.WAKE_UP);
            default -> {
                GeyserLogger.get().debug("Unhandled java animation: " + animation);
                return;
            }
        }

        session.sendUpstreamPacket(animatePacket);
    }
}
