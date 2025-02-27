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

package org.geysermc.geyser.platform.standalone;

import lombok.extern.slf4j.Slf4j;
import net.minecrell.terminalconsole.SimpleTerminalConsole;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.io.IoBuilder;
import org.geysermc.geyser.GeyserImpl;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.command.GeyserCommandSource;
import org.geysermc.geyser.text.ChatColor;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;

@Slf4j
public class GeyserStandaloneLogger extends SimpleTerminalConsole implements GeyserLogger, GeyserCommandSource {
    private static final Logger logger = LogManager.getLogger("GeyserConsole");

    /**
     * Sets up {@code System.out} and {@code System.err} to redirect to log4j.
     */
    public static void setupStreams() {
        System.setOut(IoBuilder.forLogger(logger).setLevel(Level.INFO).buildPrintStream());
        System.setErr(IoBuilder.forLogger(logger).setLevel(Level.ERROR).buildPrintStream());
    }

    @Override
    protected LineReader buildReader(LineReaderBuilder builder) {
        builder.completer((reader, line, candidates) -> {
            var suggestions = GeyserImpl.getInstance().commandRegistry().suggestionsFor(this, line.line());
            for (var suggestion : suggestions.list()) {
                candidates.add(new Candidate(suggestion.suggestion()));
            }
        });
        return super.buildReader(builder);
    }

    @Override
    protected boolean isRunning() {
        return !GeyserImpl.getInstance().isShuttingDown();
    }

    @Override
    protected void runCommand(String line) {
        // don't block the terminal!
        GeyserImpl geyser = GeyserImpl.getInstance();
        geyser.getScheduledThread().execute(() -> geyser.commandRegistry().runCommand(this, line));
    }

    @Override
    protected void shutdown() {
        GeyserImpl.getInstance().getBootstrap().onGeyserShutdown();
    }

    @Override
    public void severe(String message) {
        log.error(ChatColor.DARK_RED + message);
    }

    @Override
    public void severe(String message, Throwable error) {
        log.error(ChatColor.DARK_RED + message, error);
    }

    @Override
    public void error(String message) {
        log.error(ChatColor.RED + message);
    }

    @Override
    public void error(String message, Throwable error) {
        log.error(ChatColor.RED + message, error);
    }

    @Override
    public void warning(String message) {
        log.warn(ChatColor.YELLOW + message);
    }

    @Override
    public void info(String message) {
        log.info(ChatColor.RESET + message);
    }

    @Override
    public void debug(String message) {
        log.debug(ChatColor.GRAY + message);
    }

    @Override
    public void debug(String message, Object... arguments) {
        log.debug(ChatColor.GRAY + message, arguments);
    }

    @Override
    public void setDebug(boolean debug) {
        Configurator.setLevel(log.getName(), debug ? Level.DEBUG : Level.INFO);
    }

    @Override
    public boolean isDebug() {
        return log.isDebugEnabled();
    }
}
