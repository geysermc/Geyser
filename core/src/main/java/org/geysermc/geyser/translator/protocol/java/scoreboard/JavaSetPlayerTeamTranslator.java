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

package org.geysermc.geyser.translator.protocol.java.scoreboard;

import java.util.Arrays;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.scoreboard.Scoreboard;
import org.geysermc.geyser.scoreboard.ScoreboardUpdater;
import org.geysermc.geyser.scoreboard.Team;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamAction;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.scoreboard.ClientboundSetPlayerTeamPacket;

@Translator(packet = ClientboundSetPlayerTeamPacket.class)
public class JavaSetPlayerTeamTranslator extends PacketTranslator<ClientboundSetPlayerTeamPacket> {
    private final GeyserLogger logger = GeyserLogger.get();

    @Override
    public void translate(GeyserSession session, ClientboundSetPlayerTeamPacket packet) {
        if (logger.isDebug()) {
            logger.debug("Team packet " + packet.getTeamName() + " " + packet.getAction() + " " + Arrays.toString(packet.getPlayers()));
        }

        if ((packet.getAction() == TeamAction.ADD_PLAYER || packet.getAction() == TeamAction.REMOVE_PLAYER) && packet.getPlayers().length == 0) {
            return;
        }

        int pps = session.getWorldCache().increaseAndGetScoreboardPacketsPerSecond();

        Scoreboard scoreboard = session.getWorldCache().getScoreboard();

        if (packet.getAction() == TeamAction.CREATE) {
            scoreboard.registerNewTeam(
                packet.getTeamName(),
                packet.getPlayers(),
                packet.getDisplayName(),
                packet.getPrefix(),
                packet.getSuffix(),
                packet.getNameTagVisibility(),
                packet.getColor()
            );
        } else {
            Team team = scoreboard.getTeam(packet.getTeamName());
            if (team == null) {
                if (logger.isDebug()) {
                    logger.debug("Error while translating Team Packet " + packet.getAction()
                        + "! Scoreboard Team " + packet.getTeamName() + " is not registered."
                    );
                }
                return;
            }

            switch (packet.getAction()) {
                case UPDATE -> {
                    team.updateProperties(
                        packet.getDisplayName(),
                        packet.getPrefix(),
                        packet.getSuffix(),
                        packet.getNameTagVisibility(),
                        packet.getColor()
                    );
                }
                case ADD_PLAYER -> team.addEntities(packet.getPlayers());
                case REMOVE_PLAYER -> team.removeEntities(packet.getPlayers());
                case REMOVE -> scoreboard.removeTeam(packet.getTeamName());
            }
        }


        // ScoreboardUpdater will handle it for us if the packets per second
        // (for score and team packets) is higher than the first threshold
        if (pps < ScoreboardUpdater.FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD) {
            scoreboard.onUpdate();
        }
    }
}
