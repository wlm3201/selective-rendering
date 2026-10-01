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
 * 一条"方块匹配规则"，由玩家在配置里写的字符串解析而来。
 *
 * <h2>语法</h2>
 * <pre>
 *   minecraft:stone                 指定方块
 *   stone                           无命名空间时自动补 minecraft:
 *   #minecraft:planks               方块标签
 *   *  /  %  /  ?                   通配（只按状态匹配，不限制方块）
 *   minecraft:chest[waterlogged=false]  带状态；方括号内 k=v，逗号分隔，忽略大小写
 *   *[moving=true]                  特殊伪状态：只匹配"正被活塞推动"的方块
 * </pre>
 *
 * <h2>解析失败时的行为</h2>
 * <p>{@link #parse} 遇到下面两种情况会返回 {@code null}（调用方应当拒绝这条规则，
 * 它不会被加进名单，也不会被写回配置）：
 * <ul>
 *   <li><b>方块 id 无法解析或不存在</b>（比如 {@code stonee}）。
 *       早期版本会把它变成一条"不限定方块且无状态"的规则，
 *       而这样的规则 <b>匹配世界上所有方块</b>——黑名单模式下等于"整个世界消失"。现在直接拒绝。</li>
 *   <li><b>指定了具体方块，但状态名在该方块上不存在</b>（比如 {@code facingg=north}）。
 *       这类规则会永远匹配不上，与其让玩家困惑"加了规则没反应"，不如明确拒绝。</li>
 * </ul>
 * 被拒绝的规则会打一条日志，方便排查旧配置里遗留的坏条目。
 *
 * <p>注意：状态名只在"明确指定了方块"时才校验。
 * 标签规则（{@code #...}）和通配规则（{@code *}）无法校验，
 * 因为一个标签下各方块的状态集合并不相同——这正是 {@code *[moving=true]} 这类规则想要的弹性。
 */
public record BlockMatchRule(String source, @Nullable Block block, @Nullable TagKey<Block> tag, Map<String, String> states) {
	private static final String[] ANY = {"*", "%", "?"};

	/** 伪状态名：不对应真实的 {@code Property}，表示"是否被活塞推动"。 */
	public static final String MOVING = "moving";

	public boolean matches(BlockState state) {
		return matches(state, false);
	}

	/**
	 * 逐个条件做 AND：方块 → 标签 → 每条状态。
	 *
	 * @param moving 该方块是否正被活塞推动，用于 {@code [moving=true]} 伪状态
	 */
	public boolean matches(BlockState state, boolean moving) {
		if (block != null && state.getBlock() != block) {
			return false;
		}

		if (tag != null && !state.typeHolder().is(tag)) {
			return false;
		}

		for (Map.Entry<String, String> entry : states.entrySet()) {
			if (entry.getKey().equals(MOVING)) {
				if (Boolean.parseBoolean(entry.getValue()) != moving) {
					return false;
				}

				continue;
			}

			Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey());
			if (property == null || !valueName(state, property).equals(entry.getValue())) {
				return false;
			}
		}

		return true;
	}

	@Nullable
	public static BlockMatchRule parse(String input) {
		String source = input.trim();
		if (source.isEmpty()) {
			return null;
		}

		// 以第一个 '[' 切开：左边是"方块/标签"，右边是"状态表"
		String[] parts = source.split("\\[", 2);
		String target = parts[0].trim().toLowerCase(Locale.ROOT);

		Block block = null;
		TagKey<Block> tag = null;

		if (isAny(target)) {
			// 通配（* / % / ? / 空）：block 和 tag 都保持 null，即"不限定方块"
		}
		else if (target.startsWith("#")) {
			tag = parseTag(target.substring(1));
		}
		else {
			Identifier identifier = Identifier.tryParse(target.contains(":") ? target : "minecraft:" + target);
			if (identifier == null) {
				Log.say("[rule] 无法解析的方块 id，已忽略: {}", source);
				return null;
			}

			block = BuiltInRegistries.BLOCK.getOptional(identifier).orElse(null);
			if (block == null) {
				Log.say("[rule] 未知的方块 id，已忽略（否则它会匹配所有方块）: {}", source);
				return null;
			}
		}

		Map<String, String> states = parseStates(parts.length > 1 ? parts[1] : "");

		// 指定了具体方块时校验状态名：拼错的状态名会让规则永远匹配不上，早失败比静默失效好。
		if (block != null && !states.isEmpty()) {
			for (String name : states.keySet()) {
				if (name.equals(MOVING)) {
					continue;
				}

				if (block.getStateDefinition().getProperty(name) == null) {
					Log.say("[rule] {} 上不存在状态 {}，已忽略: {}", target, name, source);
					return null;
				}
			}
		}

		return new BlockMatchRule(source, block, tag, states);
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
