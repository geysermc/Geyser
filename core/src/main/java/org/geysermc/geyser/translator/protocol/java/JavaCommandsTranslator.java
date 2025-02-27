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

package org.geysermc.geyser.translator.protocol.java;

import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.Hash;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap;
import lombok.Getter;
import lombok.ToString;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.cloudburstmc.protocol.bedrock.data.command.CommandData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumConstraint;
import org.cloudburstmc.protocol.bedrock.data.command.CommandEnumData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandOverloadData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParam;
import org.cloudburstmc.protocol.bedrock.data.command.CommandParamData;
import org.cloudburstmc.protocol.bedrock.data.command.CommandPermission;
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.api.event.java.ServerDefineCommandsEvent;
import org.geysermc.geyser.api.util.PlatformType;
import org.geysermc.geyser.command.CommandRegistry;
import org.geysermc.geyser.item.enchantment.Enchantment;
import org.geysermc.geyser.registry.BlockRegistries;
import org.geysermc.geyser.registry.Registries;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.EntityUtils;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandNode;
import org.geysermc.mcprotocollib.protocol.data.game.command.CommandParser;
import org.geysermc.mcprotocollib.protocol.data.game.command.properties.ResourceProperties;
import org.geysermc.mcprotocollib.protocol.data.game.entity.attribute.AttributeType;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundCommandsPacket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@SuppressWarnings("removal") // We know. This is our doing.
@Translator(packet = ClientboundCommandsPacket.class)
public class JavaCommandsTranslator extends PacketTranslator<ClientboundCommandsPacket> {

    /**
     * Wait until the registries load before getting all the block names.
     */
    private static final Supplier<String[]> ALL_BLOCK_NAMES = Suppliers.memoize(() -> BlockRegistries.JAVA_BLOCKS.get().stream().map(block -> block.javaIdentifier().toString()).toArray(String[]::new));
    private static final String[] ALL_EFFECT_IDENTIFIERS = EntityUtils.getAllEffectIdentifiers();
    private static final String[] ATTRIBUTES = AttributeType.Builtin.BUILTIN.values().stream().map(type -> type.getIdentifier().asString()).toList().toArray(new String[0]);
    private static final String[] ENUM_BOOLEAN = {"true", "false"};
    private static final String[] VALID_COLORS;
    private static final String[] VALID_SCOREBOARD_SLOTS;

    private static final Hash.Strategy<BedrockCommandInfo> PARAM_STRATEGY = new Hash.Strategy<>() {
        @Override
        public int hashCode(BedrockCommandInfo o) {
            int paramHash = Arrays.deepHashCode(o.paramData());
            if ("help".equals(o.name())) {
                paramHash = 31 * paramHash + 1;
            }
            return 31 * paramHash + o.description().hashCode();
        }

        @Override
        public boolean equals(BedrockCommandInfo a, BedrockCommandInfo b) {
            if (a == b) return true;
            if (a == null || b == null) return false;
            if ("help".equals(a.name()) && !"help".equals(b.name())) {
                // Merging this causes Bedrock to fallback to its own help command
                // Tested on Paper 1.20.4 with Essentials and Bedrock 1.21
                // https://github.com/GeyserMC/Geyser/issues/2573
                return false;
            }
            if (!a.description().equals(b.description())) return false;
            if (a.paramData().length != b.paramData().length) return false;
            for (int i = 0; i < a.paramData().length; i++) {
                CommandParamData[] a1 = a.paramData()[i].getOverloads();
                CommandParamData[] b1 = b.paramData()[i].getOverloads();
                if (a1.length != b1.length) return false;

                for (int j = 0; j < a1.length; j++) {
                    if (!a1[j].equals(b1[j])) return false;
                }
            }
            return true;
        }
    };

    static {
        List<String> validColors = new ArrayList<>(NamedTextColor.NAMES.keys());
        validColors.add("reset");
        VALID_COLORS = validColors.toArray(new String[0]);

        List<String> teamOptions = new ArrayList<>(Arrays.asList("list", "sidebar", "belowName"));
        for (String color : NamedTextColor.NAMES.keys()) {
            teamOptions.add("sidebar.team." + color);
        }
        VALID_SCOREBOARD_SLOTS = teamOptions.toArray(new String[0]);
    }

    @Override
    public void translate(GeyserSession session, ClientboundCommandsPacket packet) {
        // Don't send command suggestions if they are disabled
        if (!session.getGeyser().getConfig().isCommandSuggestions()) {
            GeyserLogger.get().debug("Not sending translated command suggestions as they are disabled.");

            // Send a mostly empty packet so Bedrock doesn't override /help with its own, built-in help command.
            AvailableCommandsPacket emptyPacket = new AvailableCommandsPacket();
            emptyPacket.getCommands().add(createFakeHelpCommand());
            session.sendUpstreamPacket(emptyPacket);
            return;
        }

        CommandRegistry registry = session.getGeyser().commandRegistry();
        CommandNode[] nodes = packet.getNodes();
        List<CommandData> commandData = new ArrayList<>();
        IntSet commandNodes = new IntOpenHashSet();
        Set<String> knownAliases = new HashSet<>();
        Map<BedrockCommandInfo, Set<String>> commands = new Object2ObjectOpenCustomHashMap<>(PARAM_STRATEGY);
        Int2ObjectMap<List<CommandNode>> commandArgs = new Int2ObjectOpenHashMap<>();

        // Get the first node, it should be a root node
        CommandNode rootNode = nodes[packet.getFirstNodeIndex()];

        // Loop through the root nodes to get all commands
        for (int nodeIndex : rootNode.getChildIndices()) {
            CommandNode node = nodes[nodeIndex];

            // Make sure we don't have duplicated commands (happens if there is more than 1 root node)
            if (!commandNodes.add(nodeIndex) || !knownAliases.add(node.getName().toLowerCase(Locale.ROOT))) continue;

            // Get and update the commandArgs list with the found arguments
            if (node.getChildIndices().length >= 1) {
                for (int childIndex : node.getChildIndices()) {
                    commandArgs.computeIfAbsent(nodeIndex, ($) -> new ArrayList<>()).add(nodes[childIndex]);
                }
            }

            // Get and parse all params
            CommandOverloadData[] params = getParams(session, nodes[nodeIndex], nodes);

            // Insert the alias name into the command list
            String name = node.getName().toLowerCase(Locale.ROOT);
            String description = registry.description(name, session.locale());
            BedrockCommandInfo info = new BedrockCommandInfo(name, description, params);
            commands.computeIfAbsent(info, $ -> new HashSet<>()).add(name);
        }

        var eventBus = session.getGeyser().eventBus();

        var event = new ServerDefineCommandsEvent(session, commands.keySet());
        eventBus.fire(event);
        if (event.isCancelled()) {
            return;
        }

        var oldEvent = new org.geysermc.geyser.api.event.downstream.ServerDefineCommandsEvent(session, commands.keySet());
        eventBus.fire(oldEvent);
        if (oldEvent.isCancelled()) {
            return;
        }

        // The command flags, set to NOT_CHEAT so known commands can be used while achievements are enabled.
        Set<CommandData.Flag> flags = Set.of(CommandData.Flag.NOT_CHEAT);

        boolean helpAdded = false;

        // Loop through all the found commands
        for (Map.Entry<BedrockCommandInfo, Set<String>> entry : commands.entrySet()) {
            String commandName = entry.getValue().iterator().next(); // We know this has a value

            LinkedHashMap<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>();
            // Is this right?
            for (String s : entry.getValue()) {
                values.put(s, EnumSet.of(CommandEnumConstraint.ALLOW_ALIASES));
            }

            // Create a basic alias
            CommandEnumData aliases = new CommandEnumData(commandName + "Aliases", values, false);

            // Build the completed command and add it to the final list
            CommandData data = new CommandData(commandName, entry.getKey().description(), flags, CommandPermission.ANY, aliases, Collections.emptyList(), entry.getKey().paramData());
            commandData.add(data);

            if (commandName.equals("help")) {
                helpAdded = true;
            }
        }

        if (!helpAdded) {
            // https://github.com/GeyserMC/Geyser/issues/2573 if Brigadier does not send the help command.
            commandData.add(createFakeHelpCommand());
        }

        if (session.getGeyser().platformType() == PlatformType.STANDALONE) {
            session.getGeyser().commandRegistry().export(session, commandData, knownAliases);
        }

        // Add our commands to the AvailableCommandsPacket for the bedrock client
        AvailableCommandsPacket availableCommandsPacket = new AvailableCommandsPacket();
        availableCommandsPacket.getCommands().addAll(commandData);

        GeyserLogger.get().debug("Sending command packet of " + commandData.size() + " commands");

        // Finally, send the commands to the client
        session.sendUpstreamPacket(availableCommandsPacket);
    }

    /**
     * Build the command parameter array for the given command
     *
     * @param session the session
     * @param commandNode The command to build the parameters for
     * @param allNodes    Every command node
     * @return An array of parameter option arrays
     */
    private static CommandOverloadData[] getParams(GeyserSession session, CommandNode commandNode, CommandNode[] allNodes) {
        // Check if the command is an alias and redirect it
        if (commandNode.getRedirectIndex().isPresent()) {
            int redirectIndex = commandNode.getRedirectIndex().getAsInt();
            GeyserLogger.get().debug("Redirecting command " + commandNode.getName() + " to " + allNodes[redirectIndex].getName());
            commandNode = allNodes[redirectIndex];
        }

        if (commandNode.getChildIndices().length >= 1) {
            // Create the root param node and build all the children
            ParamInfo rootParam = new ParamInfo(commandNode, null);
            rootParam.buildChildren(new CommandBuilderContext(session), allNodes);

            List<CommandOverloadData> treeData = rootParam.getTree();

            return treeData.toArray(new CommandOverloadData[0]);
        }

        return new CommandOverloadData[0];
    }

    /**
     * Convert Java edition command types to Bedrock edition
     *
     * @param context the session's command context
     * @param node Command type to convert
     * @return Bedrock parameter data type
     */
    private static Object mapCommandType(CommandBuilderContext context, CommandNode node) {
        CommandParser parser = node.getParser();
        if (parser == null) {
            return CommandParam.STRING;
        }

        return switch (parser) {
            case FLOAT, ROTATION, DOUBLE -> CommandParam.FLOAT;
            case INTEGER, LONG -> CommandParam.INT;
            case ENTITY, GAME_PROFILE -> CommandParam.TARGET;
            case BLOCK_POS -> CommandParam.BLOCK_POSITION;
            case COLUMN_POS, VEC3 -> CommandParam.POSITION;
            case MESSAGE -> CommandParam.MESSAGE;
            case NBT_COMPOUND_TAG, NBT_TAG, NBT_PATH -> CommandParam.JSON; //TODO NBT was removed
            case RESOURCE_LOCATION, FUNCTION -> CommandParam.FILE_PATH;
            case BOOL -> ENUM_BOOLEAN;
            case OPERATION -> CommandParam.OPERATOR; // ">=", "==", etc
            case BLOCK_STATE -> ALL_BLOCK_NAMES.get();
            case ITEM_STACK -> context.getItemNames();
            case COLOR -> VALID_COLORS;
            case SCOREBOARD_SLOT -> VALID_SCOREBOARD_SLOTS;
            case RESOURCE -> handleResource(context, ((ResourceProperties) node.getProperties()).getRegistryKey(), false);
            case RESOURCE_OR_TAG -> handleResource(context, ((ResourceProperties) node.getProperties()).getRegistryKey(), true);
            case DIMENSION -> context.session.getLevels();
            case TEAM -> context.getTeams(); // Note: as of Java 1.19.3, objectives are currently parsed from the server
            default -> CommandParam.STRING;
        };
    }

    private static Object handleResource(CommandBuilderContext context, Key resource, boolean tags) {
        return switch (resource.asString()) {
            case "minecraft:attribute" -> ATTRIBUTES;
            case "minecraft:enchantment" -> context.getEnchantments();
            case "minecraft:entity_type" -> context.getEntityTypes();
            case "minecraft:mob_effect" -> ALL_EFFECT_IDENTIFIERS;
            case "minecraft:worldgen/biome" -> tags ? context.getBiomesWithTags() : context.getBiomes();
            default -> CommandParam.STRING;
        };
    }

    private CommandData createFakeHelpCommand() {
        CommandEnumData aliases = new CommandEnumData("helpAliases", Map.of("help", EnumSet.of(CommandEnumConstraint.ALLOW_ALIASES)), false);
        return new CommandData("help", "", Set.of(CommandData.Flag.NOT_CHEAT), CommandPermission.ANY, aliases, Collections.emptyList(), new CommandOverloadData[0]);
    }

    /**
     * Stores the command description and parameter data for best optimizing the Bedrock commands packet.
     */
    private record BedrockCommandInfo(String name, String description, CommandOverloadData[] paramData) implements
            org.geysermc.geyser.api.event.downstream.ServerDefineCommandsEvent.CommandInfo,
            ServerDefineCommandsEvent.CommandInfo
    {
    }

    /**
     * Stores command completions so we don't have to rebuild the same values multiple times.
     */
    @MonotonicNonNull
    private static class CommandBuilderContext {
        private final GeyserSession session;
        private Object biomesWithTags;
        private Object biomesNoTags;
        private String[] enchantments;
        private String[] entityTypes;
        private String[] itemNames;
        private CommandEnumData teams;

        CommandBuilderContext(GeyserSession session) {
            this.session = session;
        }

        private Object getBiomes() {
            if (biomesNoTags != null) {
                return biomesNoTags;
            }

            String[] identifiers = session.getGeyser().getWorldManager().getBiomeIdentifiers(false);
            return (biomesNoTags = identifiers != null ? identifiers : CommandParam.STRING);
        }

        private Object getBiomesWithTags() {
            if (biomesWithTags != null) {
                return biomesWithTags;
            }

            String[] identifiers = session.getGeyser().getWorldManager().getBiomeIdentifiers(true);
            return (biomesWithTags = identifiers != null ? identifiers : CommandParam.STRING);
        }

        private String[] getEnchantments() {
            if (enchantments != null) {
                return enchantments;
            }
            return (enchantments = session.getRegistryCache().enchantments().values().stream()
                    .map(Enchantment::identifier).toArray(String[]::new));
        }

        private String[] getEntityTypes() {
            if (entityTypes != null) {
                return entityTypes;
            }
            return (entityTypes = Registries.JAVA_ENTITY_IDENTIFIERS.get().keySet().toArray(new String[0]));
        }

        public String[] getItemNames() {
            if (itemNames != null) {
                return itemNames;
            }
            return (itemNames = Registries.JAVA_ITEM_IDENTIFIERS.get().keySet().toArray(new String[0]));
        }

        private CommandEnumData getTeams() {
            if (teams != null) {
                return teams;
            }
            return (teams = new CommandEnumData("Geyser_Teams",
                    session.getWorldCache().getScoreboard().getTeamNames(), true
            ));
        }
    }

    @Getter
    @ToString
    private static class ParamInfo {
        private final CommandNode paramNode;
        private final CommandParamData paramData;
        private final List<ParamInfo> children;

        /**
         * Create a new parameter info object
         *
         * @param paramNode CommandNode the parameter is for
         * @param paramData The existing parameters for the command
         */
        public ParamInfo(CommandNode paramNode, CommandParamData paramData) {
            this.paramNode = paramNode;
            this.paramData = paramData;
            this.children = new ArrayList<>();
        }

        /**
         * Build the array of all the child parameters (recursive)
         *
         * @param context the session's command builder context
         * @param allNodes Every command node
         */
        public void buildChildren(CommandBuilderContext context, CommandNode[] allNodes) {
            for (int paramID : paramNode.getChildIndices()) {
                CommandNode paramNode = allNodes[paramID];

                if (paramNode == this.paramNode) {
                    // Fixes a StackOverflowError when an argument has itself as a child
                    continue;
                }

                if (paramNode.getParser() == null) {
                    boolean foundCompatible = false;
                    for (int i = 0; i < children.size(); i++) {
                        ParamInfo enumParamInfo = children.get(i);
                        // Check to make sure all descending nodes of this command are compatible - otherwise, create a new overload
                        if (isCompatible(allNodes, enumParamInfo.getParamNode(), paramNode)) {
                            foundCompatible = true;
                            // TODO: Check this
                            // Extend the current list of enum values
                            // String[] enumOptions = Arrays.copyOf(enumParamInfo.getParamData().getEnumData().getValues(), enumParamInfo.getParamData().getEnumData().getValues().size() + 1);
                            // enumOptions[enumOptions.length - 1] = paramNode.getName();

                            Map<String, Set<CommandEnumConstraint>> values = new LinkedHashMap<>(enumParamInfo.getParamData().getEnumData().getValues());
                            values.put(paramNode.getName(), Set.of());

                            // Re-create the command using the updated values
                            CommandEnumData enumData = new CommandEnumData(enumParamInfo.getParamData().getEnumData().getName(), values, false);
                            CommandParamData commandParamData = new CommandParamData();
                            commandParamData.setName(enumParamInfo.getParamData().getName());
                            commandParamData.setOptional(this.paramNode.isExecutable());
                            commandParamData.setEnumData(enumData);

                            children.set(i, new ParamInfo(enumParamInfo.getParamNode(), commandParamData));
                            break;
                        }
                    }

                    if (!foundCompatible) {
                        // Create a new subcommand with this exact type
                        LinkedHashMap<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
                        map.put(paramNode.getName(), Set.of());
                        CommandEnumData enumData = new CommandEnumData(paramNode.getName(), map, false);

                        // On setting optional:
                        // isExecutable is defined as a node "constitutes a valid command."
                        // Therefore, any children of the parameter must simply be optional.
                        CommandParamData commandParamData = new CommandParamData();
                        commandParamData.setName(paramNode.getName());
                        commandParamData.setOptional(this.paramNode.isExecutable());
                        commandParamData.setEnumData(enumData);

                        children.add(new ParamInfo(paramNode, commandParamData));
                    }
                } else {
                    // Put the non-enum param into the list
                    Object mappedType = mapCommandType(context, paramNode);
                    CommandEnumData enumData = null;
                    CommandParam type = null;
                    boolean optional = this.paramNode.isExecutable();
                    if (mappedType instanceof CommandEnumData) {
                        // Likely to specify isSoft, to be possibly updated later.
                        enumData = (CommandEnumData) mappedType;
                    } else if (mappedType instanceof String[]) {
                        LinkedHashMap<String, Set<CommandEnumConstraint>> map = new LinkedHashMap<>();
                        for (String s : (String[]) mappedType) {
                            map.put(s, Set.of());
                        }

                        enumData = new CommandEnumData(getEnumDataName(paramNode).toLowerCase(Locale.ROOT), map, false);
                    } else {
                        type = (CommandParam) mappedType;
                        // Bedrock throws a fit if an optional message comes after a string or target
                        // Example vanilla commands: ban-ip, ban, and kick
                        if (optional && type == CommandParam.MESSAGE && paramData != null && (paramData.getType() == CommandParam.STRING || paramData.getType() == CommandParam.TARGET)) {
                            optional = false;
                        }
                    }
                    // IF enumData != null:
                    // In game, this will show up like <paramNode.getName(): enumData.getName()>
                    // So if paramNode.getName() == "value" and enumData.getName() == "bool": <value: bool>
                    CommandParamData commandParamData = new CommandParamData();
                    commandParamData.setName(paramNode.getName());
                    commandParamData.setOptional(optional);
                    commandParamData.setEnumData(enumData);
                    commandParamData.setType(type);

                    children.add(new ParamInfo(paramNode, commandParamData));
                }
            }

            // Recursively build all child options
            for (ParamInfo child : children) {
                child.buildChildren(context, allNodes);
            }
        }

        /**
         * Mitigates <a href="https://github.com/GeyserMC/Geyser/issues/3411">issue 3411</a>. Not a perfect solution.
         */
        private static String getEnumDataName(CommandNode node) {
            if (node.getProperties() instanceof ResourceProperties properties) {
                Key registryKey = properties.getRegistryKey();
                return registryKey.value();
            }
            return node.getParser().name();
        }

        /**
         * Comparing CommandNode type a and b, determine if they are in the same overload.
         * <p>
         * Take the <code>gamerule</code> command, and let's present three "subcommands" you can perform:
         *
         * <ul>
         *     <li><code>gamerule doDaylightCycle true</code></li>
         *     <li><code>gamerule announceAdvancements false</code></li>
         *     <li><code>gamerule randomTickSpeed 3</code></li>
         * </ul>
         *
         * While all three of them are indeed part of the same command, the command setting randomTickSpeed parses an int,
         * while the others use boolean. In Bedrock, this should be presented as a separate overload to indicate that this
         * does something a little different.
         * <p>
         * Therefore, this function will return <code>true</code> if the first two are compared, as they use the same
         * parsers. If the third is compared with either of the others, this function will return <code>false</code>.
         * <p>
         * Here's an example of how the above would be presented to Bedrock (as of 1.16.200). Notice how the top two <code>CommandParamData</code>
         * classes of each array are identical in type, but the following class is different:
         * <pre>
         *     overloads=[
         *         [
         *            CommandParamData(name=doDaylightCycle, optional=false, enumData=CommandEnumData(name=announceAdvancements, values=[announceAdvancements, doDaylightCycle], isSoft=false), type=STRING, postfix=null, options=[])
         *            CommandParamData(name=value, optional=false, enumData=CommandEnumData(name=value, values=[true, false], isSoft=false), type=null, postfix=null, options=[])
         *         ]
         *         [
         *            CommandParamData(name=randomTickSpeed, optional=false, enumData=CommandEnumData(name=randomTickSpeed, values=[randomTickSpeed], isSoft=false), type=STRING, postfix=null, options=[])
         *            CommandParamData(name=value, optional=false, enumData=null, type=INT, postfix=null, options=[])
         *         ]
         *     ]
         * </pre>
         *
         * @return if these two can be merged into one overload.
         */
        private boolean isCompatible(CommandNode[] allNodes, CommandNode a, CommandNode b) {
            if (a == b) return true;
            if (a.getParser() != b.getParser()) return false;
            if (a.getChildIndices().length != b.getChildIndices().length) return false;

            for (int i = 0; i < a.getChildIndices().length; i++) {
                boolean hasSimilarity = false;
                CommandNode a1 = allNodes[a.getChildIndices()[i]];
                // Search "b" until we find a child that matches this one
                for (int j = 0; j < b.getChildIndices().length; j++) {
                    if (isCompatible(allNodes, a1, allNodes[b.getChildIndices()[j]])) {
                        hasSimilarity = true;
                        break;
                    }
                }

                if (!hasSimilarity) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Get the tree of every parameter node (recursive)
         *
         * @return List of parameter options arrays for the command
         */
        public List<CommandOverloadData> getTree() {
            List<CommandOverloadData> treeParamData = new ArrayList<>();

            for (ParamInfo child : children) {
                // Get the tree from the child
                List<CommandOverloadData> childTree = child.getTree();

                // Un-pack the tree append the child node to it and push into the list
                for (CommandOverloadData subChildData : childTree) {
                    CommandParamData[] subChild = subChildData.getOverloads();
                    CommandParamData[] tmpTree = new CommandParamData[subChild.length + 1];
                    tmpTree[0] = child.getParamData();
                    System.arraycopy(subChild, 0, tmpTree, 1, subChild.length);

                    treeParamData.add(new CommandOverloadData(false, tmpTree));
                }

                // If we have no more child parameters just the child
                if (childTree.size() == 0) {
                    treeParamData.add(new CommandOverloadData(false, new CommandParamData[] { child.getParamData() }));
                }
            }

            return treeParamData;
        }
    }
}
