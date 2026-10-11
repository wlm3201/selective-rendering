package com.selectiverendering.matchingrule;

import com.selectiverendering.util.Log;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 解析并匹配方块规则，支持方块 ID、标签、通配、状态条件和 moving 伪状态。 */
public final class BlockMatchRule {
	private static final String[] ANY = {"any", "*", "%", "?"};

	/** 表示方块是否正被活塞推动的伪状态名。 */
	public static final String MOVING = "moving";

	private final String source;
	private final @Nullable Block block;
	private final @Nullable TagKey<Block> tag;

	private final String[] stateNames;

	private final String[] stateValues;

	/** 按方块缓存已解析的状态条件，供工作线程并发读取。 */
	private final Map<Block, Resolved> resolved = new ConcurrentHashMap<>();

	private BlockMatchRule(String source, @Nullable Block block, @Nullable TagKey<Block> tag, String[] stateNames, String[] stateValues) {
		this.source = source;
		this.block = block;
		this.tag = tag;
		this.stateNames = stateNames;
		this.stateValues = stateValues;
	}

	public String source() {
		return this.source;
	}

	public boolean matches(BlockState state) {
		return matches(state, false);
	}

	/** 判断方块、标签及所有状态条件是否匹配。 */
	public boolean matches(BlockState state, boolean moving) {
		if (this.block != null && state.getBlock() != this.block) {
			return false;
		}

		if (this.tag != null && !state.typeHolder().is(this.tag)) {
			return false;
		}

		if (this.stateNames.length == 0) {
			return true;
		}

		Resolved resolved = this.resolved.computeIfAbsent(state.getBlock(), this::resolve);
		return resolved.matches(state, moving);
	}

	/** 将状态名和值解析为指定方块的属性对象。 */
	private Resolved resolve(Block owner) {
		StateDefinition<Block, BlockState> definition = owner.getStateDefinition();
		int count = this.stateNames.length;

		boolean[] movingFlags = new boolean[count];
		Property<?>[] properties = new Property<?>[count];
		Object[] expected = new Object[count];

		for (int index = 0; index < count; index++) {
			String name = this.stateNames[index];
			String value = this.stateValues[index];

			if (name.equals(MOVING)) {
				movingFlags[index] = true;
				expected[index] = Boolean.parseBoolean(value);
				continue;
			}

			Property<?> property = definition.getProperty(name);
			properties[index] = property;
			expected[index] = property == null ? null : findValue(property, value);
		}

		return new Resolved(movingFlags, properties, expected);
	}

	/** 按值的字符串形式匹配，保持规则原有的比较语义。 */
	private static Object findValue(Property<?> property, String expected) {
		for (Object value : property.getPossibleValues()) {
			if (String.valueOf(value).toLowerCase(Locale.ROOT).equals(expected)) {
				return value;
			}
		}

		return null;
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static Object valueOf(BlockState state, Property property) {
		return state.getValue(property);
	}

	private record Resolved(boolean[] movingFlags, Property<?>[] properties, Object[] expected) {
		private boolean matches(BlockState state, boolean moving) {
			for (int index = 0; index < this.properties.length; index++) {
				if (this.movingFlags[index]) {
					if (((Boolean) this.expected[index]) != moving) {
						return false;
					}

					continue;
				}

				Property<?> property = this.properties[index];
				Object wanted = this.expected[index];
				if (property == null || wanted == null) {
					return false;
				}

				Object actual = valueOf(state, property);
				if (actual != wanted && !wanted.equals(actual)) {
					return false;
				}
			}

			return true;
		}
	}

	public static boolean isValid(String input) {
		return parse(input, false) != null;
	}

	@Nullable
	public static BlockMatchRule parse(String input) {
		return parse(input, true);
	}

	@Nullable
	private static BlockMatchRule parse(String input, boolean log) {
		String source = input.trim();
		if (source.isEmpty()) {
			return null;
		}

		String[] parts = source.split("\\[", 2);
		String target = parts[0].trim().toLowerCase(Locale.ROOT);

		Block block = null;
		TagKey<Block> tag = null;

		if (isAny(target)) {
		}
		else if (target.startsWith("#")) {
			tag = parseTag(target.substring(1));
		}
		else {
			Identifier identifier = Identifier.tryParse(target.contains(":") ? target : "minecraft:" + target);
			if (identifier == null) {
				if (log) {
					Log.warn("[rule] Unknown block id, ignored: {}", source);
				}
				return null;
			}

			block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
			if (block == null) {
				if (log) {
					Log.warn("[rule] Unknown block id, ignored: {}", source);
				}
				return null;
			}
		}

		Map<String, String> states = parseStates(parts.length > 1 ? parts[1] : "");

		if (block != null && !states.isEmpty()) {
			for (String name : states.keySet()) {
				if (name.equals(MOVING)) {
					continue;
				}

				if (block.getStateDefinition().getProperty(name) == null) {
					if (log) {
						Log.warn("[rule] {} does not have the state {}, ignored: {}", target, name, source);
					}
					return null;
				}
			}
		}

		String[] names = states.keySet().toArray(new String[0]);
		String[] values = new String[names.length];
		for (int index = 0; index < names.length; index++) {
			values[index] = states.get(names[index]);
		}

		return new BlockMatchRule(source, block, tag, names, values);
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
}
