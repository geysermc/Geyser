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

package org.geysermc.geyser.api.pack.option;

import org.geysermc.geyser.api.GeyserApi;

/**
 * Allows specifying a pack priority that decides the order on how packs are sent to the client.
 * Multiple resource packs can override each other. The higher the priority, the "higher" in the stack
 * a pack is, and the more a pack can override other packs.
 * @since 2.6.2
 */
public interface PriorityOption extends ResourcePackOption<Double> {

    PriorityOption HIGHEST = PriorityOption.priority(10);
    PriorityOption HIGH = PriorityOption.priority(8);
    PriorityOption NORMAL = PriorityOption.priority(5);
    PriorityOption LOW = PriorityOption.priority(3);
    PriorityOption LOWEST = PriorityOption.priority(0);

    /**
     * Constructs a priority option based on a value between 0 and 10.
     * The higher the number, the higher will this pack appear in the resource pack stack.
     *
     * @param priority an integer that is above 0, but smaller than 10
     * @return the priority option
     * @since 2.6.2
     */
    static PriorityOption priority(double priority) {
        if (priority < 0 || priority > 10) {
            throw new IllegalArgumentException("Priority must be between 0 and 10 inclusive!");
        }
        return GeyserApi.api().provider(PriorityOption.class, priority);
    }
}
