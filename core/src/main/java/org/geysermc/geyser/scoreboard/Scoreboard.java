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

package org.geysermc.geyser.scoreboard;

import static org.geysermc.geyser.scoreboard.UpdateType.REMOVE;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.cloudburstmc.protocol.bedrock.data.ScoreInfo;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.packet.SetScorePacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.scoreboard.display.slot.BelownameDisplaySlot;
import org.geysermc.geyser.scoreboard.display.slot.DisplaySlot;
import org.geysermc.geyser.scoreboard.display.slot.PlayerlistDisplaySlot;
import org.geysermc.geyser.scoreboard.display.slot.SidebarDisplaySlot;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.GeyserLocale;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.NameTagVisibility;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.ScoreboardPosition;
import org.geysermc.mcprotocollib.protocol.data.game.scoreboard.TeamColor;
import org.jetbrains.annotations.Contract;

/**
 * Here follows some information about how scoreboards work in Java Edition, that is related to the workings of this
 * class:
 * <p>
 * Objectives can be divided in two states: inactive and active.
 * Inactive objectives is the default state for objectives that have been created using the SetObjective packet.
 * Scores can be added, updated and removed, but as long as they're inactive they aren't shown to the player.
 * An objective becomes active when a SetDisplayObjective packet is received, which contains the slot that
 * the objective should be displayed at.
 * <p>
 * While Bedrock can handle showing one objective on multiple slots at the same time, we have to help Bedrock a bit
 * for example by limiting the amount of sidebar scores to the amount of lines that can be shown
 * (otherwise Bedrock may lag) and only showing online players in the playerlist (otherwise it's too cluttered.)
 * This fact is the biggest contributor for the class being structured like it is.
 */
public final class Scoreboard {
    private static final boolean SHOW_SCOREBOARD_LOGS = Boolean.parseBoolean(System.getProperty("Geyser.ShowScoreboardLogs", "true"));
    private static final boolean ADD_TEAM_SUGGESTIONS = Boolean.parseBoolean(System.getProperty("Geyser.AddTeamSuggestions", "true"));

    private final GeyserSession session;
    private final GeyserLogger logger;
    private final AtomicLong nextId = new AtomicLong(0);

    private final Map<String, Objective> objectives = new ConcurrentHashMap<>();
    @Getter
    private final Map<ScoreboardPosition, DisplaySlot> objectiveSlots = Collections.synchronizedMap(new EnumMap<>(ScoreboardPosition.class));
    private final List<DisplaySlot> removedSlots = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Team> teams = new ConcurrentHashMap<>(); // updated on multiple threads
    /**
     * Required to preserve vanilla behavior, which also uses a map.
     * Otherwise, for example, if TAB has a team for a player and vanilla has a team, "race conditions" that do not
     * match vanilla could occur.
     */
    @Getter
    private final Map<String, Team> playerToTeam = new Object2ObjectOpenHashMap<>();

    private final AtomicBoolean updateLockActive = new AtomicBoolean(false);
    private int lastAddScoreCount = 0;
    private int lastRemoveScoreCount = 0;

    public Scoreboard(GeyserSession session) {
        this.session = session;
        this.logger = GeyserLogger.get();
    }

    public void removeScoreboard() {
        var copy = new HashMap<>(objectiveSlots);
        objectiveSlots.clear();

        for (DisplaySlot slot : copy.values()) {
            slot.remove();
        }
    }

    public @Nullable Objective registerNewObjective(String objectiveId) {
        Objective objective = objectives.get(objectiveId);
        if (objective != null) {
            // matches vanilla behaviour
            if (SHOW_SCOREBOARD_LOGS) {
                logger.warning("An objective with the same name '" + objectiveId + "' already exists! Ignoring new objective!");
            }
            return null;
        }

        objective = new Objective(this, objectiveId);
        objectives.put(objectiveId, objective);
        return objective;
    }

    public void displayObjective(String objectiveId, ScoreboardPosition slot) {
        if (objectiveId.isEmpty()) {
            // matches vanilla behaviour
            var display = objectiveSlots.get(slot);
            if (display != null) {
                removedSlots.add(display);
                objectiveSlots.remove(slot, display);
                var objective = display.objective();
                objective.removeDisplaySlot(display);
            }
            return;
        }

        Objective objective = objectives.get(objectiveId);
        if (objective == null) {
            return;
        }

        var display = objectiveSlots.get(slot);
        if (display != null && display.objective() != objective) {
            removedSlots.add(display);
        }

        display = switch (DisplaySlot.slotCategory(slot)) {
            case SIDEBAR -> new SidebarDisplaySlot(session, objective, slot);
            case BELOW_NAME -> new BelownameDisplaySlot(session, objective);
            case PLAYER_LIST -> new PlayerlistDisplaySlot(session, objective);
            default -> throw new IllegalStateException("Unexpected value: " + slot);
        };
        objectiveSlots.put(slot, display);
        objective.addDisplaySlot(display);
    }

    public void registerNewTeam(
        String teamName,
        String[] players,
        Component name,
        Component prefix,
        Component suffix,
        NameTagVisibility visibility,
        TeamColor color
    ) {
        Team team = teams.get(teamName);
        if (team != null) {
            if (SHOW_SCOREBOARD_LOGS) {
                logger.info("Ignoring team %s for %s. It overrides without removing old team.".formatted(teamName, session.javaUsername()));
            }
            return;
        }

        team = new Team(this, teamName, players, name, prefix, suffix, visibility, color);
        teams.put(teamName, team);

        // Update command parameters - is safe to send even if the command enum doesn't exist on the client (as of 1.19.51)
        if (ADD_TEAM_SUGGESTIONS) {
            session.addCommandEnum("Geyser_Teams", team.id());
        }
    }

    public void onUpdate() {
        // if an update is already running, let it finish
        if (updateLockActive.getAndSet(true)) {
            return;
        }

        List<ScoreInfo> addScores = new ArrayList<>(lastAddScoreCount);
        List<ScoreInfo> removeScores = new ArrayList<>(lastRemoveScoreCount);

        Team playerTeam = getTeamFor(session.getPlayerEntity().getUsername());
        DisplaySlot correctSidebarSlot = null;

        for (DisplaySlot slot : objectiveSlots.values()) {
            // slot has been removed
            if (slot.updateType() == REMOVE) {
                continue;
            }

            if (playerTeam != null && playerTeam.color() == slot.teamColor()) {
                correctSidebarSlot = slot;
            }
        }

        if (correctSidebarSlot == null) {
            correctSidebarSlot = objectiveSlots.get(ScoreboardPosition.SIDEBAR);
        }

        var actualRemovedSlots = new ArrayList<>(removedSlots);
        for (var slot : actualRemovedSlots) {
            // Deletion must be handled before the active objectives are handled - otherwise if a scoreboard display is changed before the current
            // scoreboard is removed, the client can crash
            slot.remove();
        }
        removedSlots.removeAll(actualRemovedSlots);

        handleDisplaySlot(objectiveSlots.get(ScoreboardPosition.PLAYER_LIST), addScores, removeScores);
        handleDisplaySlot(correctSidebarSlot, addScores, removeScores);
        handleDisplaySlot(objectiveSlots.get(ScoreboardPosition.BELOW_NAME), addScores, removeScores);

        if (!removeScores.isEmpty()) {
            SetScorePacket packet = new SetScorePacket();
            packet.setAction(SetScorePacket.Action.REMOVE);
            packet.setInfos(removeScores);
            session.sendUpstreamPacket(packet);
        }

        if (!addScores.isEmpty()) {
            SetScorePacket packet = new SetScorePacket();
            packet.setAction(SetScorePacket.Action.SET);
            packet.setInfos(addScores);
            session.sendUpstreamPacket(packet);
        }

        lastAddScoreCount = addScores.size();
        lastRemoveScoreCount = removeScores.size();
        updateLockActive.set(false);
    }

    private void handleDisplaySlot(DisplaySlot slot, List<ScoreInfo> addScores, List<ScoreInfo> removeScores) {
        if (slot != null) {
            slot.render(addScores, removeScores);
        }
    }

    public Objective getObjective(String objectiveName) {
        return objectives.get(objectiveName);
    }

    public void removeObjective(Objective objective) {
        objectives.remove(objective.getObjectiveName());
        for (DisplaySlot slot : objective.getActiveSlots()) {
            objectiveSlots.remove(slot.position(), slot);
            removedSlots.add(slot);
        }
    }

    public void resetPlayerScores(String playerNameOrEntityUuid) {
        for (Objective objective : objectives.values()) {
            objective.removeScore(playerNameOrEntityUuid);
        }
    }

    public Team getTeam(String teamName) {
        return teams.get(teamName);
    }

    public Team getTeamFor(String playerNameOrEntityUuid) {
        return playerToTeam.get(playerNameOrEntityUuid);
    }

    public void removeTeam(String teamName) {
        Team remove = teams.remove(teamName);
        if (remove == null) {
            return;
        }
        remove.remove();
        session.removeCommandEnum("Geyser_Teams", remove.id());
    }

    @Contract("-> new")
    public Map<String, Set<CommandEnumConstraint>> getTeamNames() {
        return teams.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), o -> EnumSet.noneOf(CommandEnumConstraint.class),
                        (o1, o2) -> o1, LinkedHashMap::new));
    }

    public void playerRegistered(PlayerEntity player) {
        for (DisplaySlot slot : objectiveSlots.values()) {
            slot.playerRegistered(player);
        }
    }

    public void playerRemoved(PlayerEntity player) {
        for (DisplaySlot slot : objectiveSlots.values()) {
            slot.playerRemoved(player);
        }
    }

    public void entityRegistered(Entity entity) {
        var team = getTeamFor(entity.teamIdentifier());
        if (team != null) {
            team.onEntitySpawn(entity);
        }
    }

    public void entityRemoved(Entity entity) {
        var team = getTeamFor(entity.teamIdentifier());
        if (team != null) {
            team.onEntityRemove(entity);
        }
    }

    public void setTeamFor(Team team, Set<String> entities) {
        for (DisplaySlot slot : objectiveSlots.values()) {
            // only sidebar slots use teams
            if (slot instanceof SidebarDisplaySlot sidebar) {
                sidebar.setTeamFor(team, entities);
            }
        }
    }

    public long nextId() {
        return nextId.getAndIncrement();
    }

    public GeyserSession session() {
        return session;
    }
}
