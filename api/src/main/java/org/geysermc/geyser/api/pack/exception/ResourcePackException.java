/*
 * Copyright (c) 2025 GeyserMC. http://geysermc.org
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

package org.geysermc.geyser.api.pack.exception;

import java.io.Serial;

/**
 * Used to indicate an exception that occurred while handling resource pack registration,
 * or during resource pack option validation.
 * @since 2.6.2
 */
public class ResourcePackException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The {@link Cause} of this exception.
     */
    private final Cause cause;

    /**
     * @param cause The cause of this exception
     * @since 2.6.2
     */
    public ResourcePackException(Cause cause) {
        super(cause.message());
        this.cause = cause;
    }

    /**
     * @param cause the cause of this exception
     * @param message an additional, more in-depth message about the issue.
     * @since 2.6.2
     */
    public ResourcePackException(Cause cause, String message) {
        super(message);
        this.cause = cause;
    }

    /**
     * @return the cause of this exception
     * @since 2.6.2
     */
    public Cause cause() {
        return cause;
    }

    /**
     * Represents different causes with explanatory messages stating which issue occurred.
     * @since 2.6.2
     */
    public enum Cause {
        DUPLICATE("A resource pack with this UUID was already registered!"),
        INVALID_PACK("This resource pack is not a valid Bedrock edition resource pack!"),
        INVALID_PACK_OPTION("Attempted to register an invalid resource pack option!"),
        PACK_NOT_FOUND("No resource pack was found!"),
        UNKNOWN_IMPLEMENTATION("Use the resource pack codecs to create resource packs.");

        private final String message;

        /**
         * @return the message of this cause
         * @since 2.6.2
         */
        public String message() {
            return message;
        }

        Cause(String message) {
            this.message = message;
        }
    }
}
