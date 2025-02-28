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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.ConfigurationVisitor;

/**
 * Moves comments from a different node and puts them on this node
 */
public final class ConfigurationCommentMover implements ConfigurationVisitor.Stateless<RuntimeException> {

    private final CommentedConfigurationNode otherRoot;

    private ConfigurationCommentMover(@NonNull CommentedConfigurationNode otherNode) {
        this.otherRoot = otherNode;
    }

    @Override
    public void enterNode(final ConfigurationNode node) {
        if (!(node instanceof CommentedConfigurationNode destination)) {
            // Should not occur because all nodes in a tree are the same type,
            // and our static method below ensures this visitor is only used on CommentedConfigurationNodes
            throw new IllegalStateException(node.path() + " is not a CommentedConfigurationNode");
        }
        // Node with the same path
        CommentedConfigurationNode source = otherRoot.node(node.path());

        moveSingle(source, destination);
    }

    private static void moveSingle(@NonNull CommentedConfigurationNode source, @NonNull CommentedConfigurationNode destination) {
        // Only transfer the comment, overriding if necessary
        String comment = source.comment();
        if (comment != null) {
            destination.comment(comment);
        }
    }

    /**
     * Moves comments from a source node and its children to a destination node and its children (of a different tree), overriding if necessary.
     * Comments are only moved to the destination node and its children which exist.
     * Comments are only moved to and from nodes with the exact same path.
     *
     * @param source the source of the comments, which must be the topmost parent of a tree
     * @param destination the destination of the comments, any node in a different tree
     */
    public static void moveComments(@NonNull CommentedConfigurationNode source, @NonNull CommentedConfigurationNode destination) {
        if (source.parent() != null) {
            throw new IllegalArgumentException("source is not the base of the tree it is within: " + source.path());
        }

        if (source.isNull()) {
            // It has no value(s), but may still have a comment on it. Don't both traversing the whole destination tree.
            moveSingle(source, destination);
        } else {
            destination.visit(new ConfigurationCommentMover(source));
        }
    }
}
