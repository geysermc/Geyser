/*
 * Copyright (c) 2024 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.platform.velocity;

import org.bstats.config.MetricsConfig;
import org.geysermc.geyser.util.metrics.MetricsPlatform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class VelocityMetrics implements MetricsPlatform {
    private final MetricsConfig config;

    public VelocityMetrics(Path dataDirectory) throws IOException {
        // https://github.com/Bastian/bstats-metrics/blob/master/velocity/src/main/java/org/bstats/velocity/Metrics.java
        File configFile = dataDirectory.getParent().resolve("bStats").resolve("config.txt").toFile();
        this.config = new MetricsConfig(configFile, true);
        // No logger message is implemented as Velocity should print its own before we do.
    }

    @Override
    public boolean enabled() {
        return config.isEnabled();
    }

    @Override
    public String serverUuid() {
        return config.getServerUUID();
    }

    @Override
    public boolean logFailedRequests() {
        return config.isLogErrorsEnabled();
    }

    @Override
    public boolean logSentData() {
        return config.isLogSentDataEnabled();
    }

    @Override
    public boolean logResponseStatusText() {
        return config.isLogResponseStatusTextEnabled();
    }
}
