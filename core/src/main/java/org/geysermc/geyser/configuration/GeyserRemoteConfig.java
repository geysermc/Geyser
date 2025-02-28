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

import org.geysermc.geyser.text.AsteriskSerializer;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultNumeric;
import org.spongepowered.configurate.interfaces.meta.defaults.DefaultString;
import org.spongepowered.configurate.interfaces.meta.range.NumericRange;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;

/**
 * Used for any instance where the Java server is detached from Geyser.
 */
@ConfigSerializable
public interface GeyserRemoteConfig extends GeyserConfig {
    @Override
    RemoteConfig java();

    @ConfigSerializable
    interface RemoteConfig extends JavaConfig {
        @Override
        @Comment("The IP address of the Java Edition server.")
        @DefaultString("127.0.0.1")
        @AsteriskSerializer.Asterisk
        String address();

        @Override
        @Comment("The port of the Java Edition server.")
        @DefaultNumeric(25565)
        @NumericRange(from = 0, to = 65535)
        int port();

        @Override
        @Comment("""
                Forward the hostname that the Bedrock client used to connect over to the Java server
                This is designed to be used for forced hosts on proxies""")
        boolean forwardHostname();
    }
}
