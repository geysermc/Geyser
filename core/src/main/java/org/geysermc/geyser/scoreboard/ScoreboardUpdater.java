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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.session.cache.WorldCache;
import org.geysermc.geyser.text.GeyserLocale;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public final class ScoreboardUpdater extends Thread {
    public static final int FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD;
    public static final int SECOND_SCORE_PACKETS_PER_SECOND_THRESHOLD = 250;

    private static final int FIRST_MILLIS_BETWEEN_UPDATES = 250; // 4 updates per second
    private static final int SECOND_MILLIS_BETWEEN_UPDATES = 1000; // 1 update per second

    private static final boolean DEBUG_ENABLED;

    static {
        GeyserConfig config = GeyserImpl.getInstance().config();
        FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD = Math.min(config.advanced().scoreboardPacketThreshold(), SECOND_SCORE_PACKETS_PER_SECOND_THRESHOLD);
        DEBUG_ENABLED = config.debugMode();
    }

    private final GeyserImpl geyser = GeyserImpl.getInstance();

    private long lastUpdate = System.currentTimeMillis();
    private long lastPacketsPerSecondUpdate = System.currentTimeMillis();

    public static void init() {
        new ScoreboardUpdater().start();
    }

    @Override
    public void run() {
        while (!geyser.isShuttingDown() && !geyser.isReloading()) {
            try {
                long timeTillAction = getTimeTillNextAction();
                if (timeTillAction > 0) {
                    sleepFor(timeTillAction);
                    continue;
                }

                long currentTime = System.currentTimeMillis();

                // reset score-packets per second every second
                Collection<GeyserSession> sessions = geyser.getSessionManager().getSessions().values();
                if (currentTime - lastPacketsPerSecondUpdate >= 1000) {
                    lastPacketsPerSecondUpdate = currentTime;
                    for (GeyserSession session : sessions) {
                        ScoreboardSession scoreboardSession = session.getWorldCache().getScoreboardSession();

                        int oldPps = scoreboardSession.getPacketsPerSecond();
                        int newPps = scoreboardSession.getPendingPacketsPerSecond().get();

                        scoreboardSession.packetsPerSecond = newPps;
                        scoreboardSession.pendingPacketsPerSecond.set(0);

                        // just making sure that all updates are pushed before giving up control
                        if (oldPps >= FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD &&
                                newPps < FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD) {
                            session.getWorldCache().getScoreboard().onUpdate();
                        }
                    }
                }

                if (currentTime - lastUpdate >= FIRST_MILLIS_BETWEEN_UPDATES) {
                    lastUpdate = currentTime;

                    for (GeyserSession session : sessions) {
                        WorldCache worldCache = session.getWorldCache();
                        ScoreboardSession scoreboardSession = worldCache.getScoreboardSession();

                        int pps = scoreboardSession.getPacketsPerSecond();
                        if (pps >= FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD) {
                            boolean reachedSecondThreshold = pps >= SECOND_SCORE_PACKETS_PER_SECOND_THRESHOLD;

                            int millisBetweenUpdates = reachedSecondThreshold ?
                                    SECOND_MILLIS_BETWEEN_UPDATES :
                                    FIRST_MILLIS_BETWEEN_UPDATES;

                            if (currentTime - scoreboardSession.lastUpdate >= millisBetweenUpdates) {
                                worldCache.getScoreboard().onUpdate();
                                scoreboardSession.lastUpdate = currentTime;

                                if (DEBUG_ENABLED && (currentTime - scoreboardSession.lastLog >= 60000)) { // one minute
                                    int threshold = reachedSecondThreshold ?
                                            SECOND_SCORE_PACKETS_PER_SECOND_THRESHOLD :
                                            FIRST_SCORE_PACKETS_PER_SECOND_THRESHOLD;

                                    geyser.getLogger().info(
                                            GeyserLocale.getLocaleStringLog("geyser.scoreboard.updater.threshold_reached.log", session.bedrockUsername(), threshold, pps) +
                                                    GeyserLocale.getLocaleStringLog("geyser.scoreboard.updater.threshold_reached", (millisBetweenUpdates / 1000.0))
                                    );

                                    scoreboardSession.lastLog = currentTime;
                                }
                            }
                        }
                    }
                }

                if (DEBUG_ENABLED) {
                    long timeSpent = System.currentTimeMillis() - currentTime;
                    if (timeSpent > 0) {
                        geyser.getLogger().info(String.format(
                                "Scoreboard updater: took %s ms. Updated %s players",
                                timeSpent, sessions.size()
                        ));
                    }
                }

                long timeTillNextAction = getTimeTillNextAction();
                sleepFor(timeTillNextAction);
            } catch (Throwable e) {
                geyser.getLogger().error("Error while translating scoreboard information!", e);
                // Wait so we don't try to run the scoreboard immediately after this
                sleepFor(FIRST_MILLIS_BETWEEN_UPDATES);
            }
        }
    }

    private long getTimeTillNextAction() {
        long currentTime = System.currentTimeMillis();

        long timeUntilNextUpdate = FIRST_MILLIS_BETWEEN_UPDATES - (currentTime - lastUpdate);
        long timeUntilPacketReset = 1000 - (currentTime - lastPacketsPerSecondUpdate);

        return Math.min(timeUntilNextUpdate, timeUntilPacketReset);
    }

    private void sleepFor(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @RequiredArgsConstructor
    @Getter
    public static final class ScoreboardSession {
        private final GeyserSession session;
        private final AtomicInteger pendingPacketsPerSecond = new AtomicInteger(0);
        private int packetsPerSecond;
        private long lastUpdate;
        private long lastLog;
    }
}
