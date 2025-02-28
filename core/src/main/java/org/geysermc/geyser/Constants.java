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

package org.geysermc.geyser;

import java.net.URI;
import java.net.URISyntaxException;

public final class Constants {
    public static final URI GLOBAL_API_WS_URI;

    public static final String NEWS_OVERVIEW_URL = "https://api.geysermc.org/v2/news/";
    public static final String NEWS_PROJECT_NAME = "geyser";

    public static final String FLOODGATE_DOWNLOAD_LOCATION = "https://geysermc.org/download#floodgate";
    public static final String GEYSER_DOWNLOAD_LOCATION = "https://geysermc.org/download";
    static final String SAVED_AUTH_CHAINS_FILE = "saved-auth-chains.json";

    public static final String GEYSER_CUSTOM_NAMESPACE = "geyser_custom";

    public static final String MINECRAFT_SKIN_SERVER_URL = "https://textures.minecraft.net/texture/";

    public static final int CONFIG_VERSION = 5;
    public static final int ADVANCED_CONFIG_VERSION = 1;

    public static final int BSTATS_ID = 5273;

    static {
        URI wsUri = null;
        try {
            wsUri = new URI("wss://api.geysermc.org/ws");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        GLOBAL_API_WS_URI = wsUri;
    }
}
