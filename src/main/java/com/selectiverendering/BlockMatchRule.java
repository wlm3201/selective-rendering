package com.selectiverendering;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One entry of the block list, parsed from a text rule.
 *
 * <p>A rule can name a block, a block tag, or nothing at all, and can narrow that down with block
 * state properties:</p>
 * <pre>
 * minecraft:stone                      a single block
 * #minecraft:planks                    everything in a tag
 * *                                    every block
 * minecraft:furnace[lit=true]          a block with a state
 * #minecraft:planks[axis=x]            a tag with a state
 * </pre>
 *
 * <p>Properties are kept as text and resolved against the state being tested rather than being
 * looked up while parsing. Rules are parsed long before a world exists, and a tag can cover blocks
 * that have nothing to do with each other, so there is no single state definition to resolve
 * against up front.</p>
 *
 * <p>Instances are immutable and are read from the section build worker threads.</p>
 *
 * @param source the text the rule was parsed from, kept so the list can be saved back and compared
 *               without turning the rule into text again
 * @param block  the block to match, or null for any block
 * @param tag    the tag to match, or null for no tag
 * @param states property name to expected value, both lower case
 */
public record BlockMatchRule(String source, @Nullable Block block, @Nullable TagKey<Block> tag, Map<String, String> states) {
	/**
	 * Targets that mean "match anything". A bare target is treated the same way, so an entry that
	 * only carries properties is not a mistake.
	 */
	private static final String[] ANY = {"*", "%", "?", "any"};

	public boolean matches(BlockState state) {
		if (block != null && state.getBlock() != block) {
			return false;
		}

		if (tag != null && !state.typeHolder().is(tag)) {
			return false;
		}

		for (Map.Entry<String, String> entry : states.entrySet()) {
			Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
			if (property == null || !valueName(state, property).equals(entry.getValue())) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Builds a rule from text, or returns null when the text cannot be read at all. An unknown
	 * block or tag still produces a rule, it just never matches, which keeps a typo visible in the
	 * list instead of silently dropping the entry.
	 */
	@Nullable
	public static BlockMatchRule parse(String input) {
		String source = input.trim();
		if (source.isEmpty()) {
			return null;
		}

		String[] parts = source.split("\\[", 2);
		String target = parts[0].trim().toLowerCase(Locale.ROOT);

		Block block = null;
		TagKey<Block> tag = null;

		if (isAny(target)) {
			// No target, the properties alone decide.
		}
		else if (target.startsWith("#")) {
			tag = parseTag(target.substring(1));
		}
		else {
			Identifier identifier = Identifier.tryParse(target.contains(":") ? target : "minecraft:" + target);
			if (identifier != null) {
				block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
			}
		}

		return new BlockMatchRule(source, block, tag, parseStates(parts.length > 1 ? parts[1] : ""));
	}

	private static boolean isAny(String target) {
		if (target.isEmpty()) {
			return true;
		}

		for (String value : ANY) {
			if (target.equals(value)) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	private static TagKey<Block> parseTag(String name) {
		Identifier identifier = Identifier.tryParse(name.contains(":") ? name : "minecraft:" + name);
		return identifier == null ? null : TagKey.create(Registries.BLOCK, identifier);
	}

	private static Map<String, String> parseStates(String text) {
		Map<String, String> states = new LinkedHashMap<>();

		for (String entry : text.replace("]", "").split(",")) {
			String[] pair = entry.split("=", 2);
			if (pair.length != 2) {
				continue;
			}

			String name = pair[0].trim().toLowerCase(Locale.ROOT);
			String value = pair[1].trim().toLowerCase(Locale.ROOT);
			if (!name.isEmpty() && !value.isEmpty()) {
				states.put(name, value);
			}
		}

		return Map.copyOf(states);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String valueName(BlockState state, Property<?> property) {
		return String.valueOf(state.getValue((Property) property)).toLowerCase(Locale.ROOT);
	}
}
