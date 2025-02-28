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

package org.geysermc.geyser.configuration;

import org.spongepowered.configurate.interfaces.meta.Exclude;
import org.spongepowered.configurate.interfaces.meta.Field;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

@ConfigSerializable
public interface GeyserPluginConfig extends GeyserConfig {
    @Override
    IntegratedJavaConfig java();

    @ConfigSerializable
    interface IntegratedJavaConfig extends JavaConfig {
        @Override
        @Field
        String address();

        @Override
        void address(String address);

        @Override
        @Field
        int port();

        @Override
        void port(int port);

        @Override
        @Exclude
        default boolean forwardHostname() {
            return true; // No need to worry about suspicious behavior flagging the server.
        }
    }

    @Comment("""
            How often to ping the Java server to refresh MOTD and player count, in seconds.
            Only relevant if integrated-ping-passthrough is disabled.""")
    @Override
    int pingPassthroughInterval();
}
