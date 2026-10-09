package com.selectiverendering;

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
 *
 * <h2>为什么状态条件是"预编译"的</h2>
 * <p>{@link #matches} 处在全 Mod 最热的路径上：每个方块、每次判定、每条规则都要跑一遍。
 * 早期实现在匹配时对每个状态条件做
 * <pre>
 *   state.getBlock().getStateDefinition().getProperty("waterlogged")   // 一次字符串查表
 *   String.valueOf(state.getValue(property)).toLowerCase()             // 一次字符串转换
 *        .equals("false")                                              // 一次字符串比较
 * </pre>
 * 还要为 {@code states.entrySet()} 分配一个迭代器。
 *
 * <p>而<b>一个方块的状态定义是不变的</b>，所以这些可以在第一次遇到某个方块时算一次：
 * 把 {@code ("waterlogged", "false")} 解析成 {@code (WaterloggedProperty, Boolean.FALSE)}，
 * 之后每次匹配就只剩 {@code state.getValue(property) == 期望值}。
 * 解析结果按 {@link Block} 缓存（见 {@link #RESOLVED}），
 * 一个世界里遇到的方块种类有限，缓存本身也很小。
 *
 * <p>另外：没有任何状态条件的规则（绝大多数）在方块/标签判定之后直接返回，
 * 完全不碰缓存。
 */
public final class BlockMatchRule {
	private static final String[] ANY = {"*", "%", "?"};

	/** 伪状态名：不对应真实的 {@code Property}，表示"是否被活塞推动"。 */
	public static final String MOVING = "moving";

	private final String source;
	private final @Nullable Block block;
	private final @Nullable TagKey<Block> tag;

	/** 状态条件的名字（与 {@link #stateValues} 一一对应，顺序一致）。 */
	private final String[] stateNames;

	/** 状态条件的期望值（小写）。 */
	private final String[] stateValues;

	/**
	 * {@code Block → 解析好的状态条件}。
	 *
	 * <p>用 {@link ConcurrentHashMap} 是因为 {@link #matches} 会被区块构建线程和光照线程并发调用。
	 * 内容只增不删，且方块的状态定义在整个进程生命周期内不变，所以不需要失效机制。
	 */
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

	/**
	 * 逐个条件做 AND：方块 → 标签 → 每条状态。
	 *
	 * @param moving 该方块是否正被活塞推动，用于 {@code [moving=true]} 伪状态
	 */
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

	/** 把 {@code (状态名, 期望值名)} 按某个方块的状态定义解析成可直接比较的对象。 */
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
			// property 为 null（该方块没有这个状态）或期望值名不匹配任何可选值
			// 时都留 null —— 下面 matches 里一律判为"不匹配"，与旧实现一致。
			expected[index] = property == null ? null : findValue(property, value);
		}

		return new Resolved(movingFlags, properties, expected);
	}

	/**
	 * 在 {@code property} 的可选值里找出"字符串形式等于 {@code expected}"的那个。
	 *
	 * <p>刻意用 {@code String.valueOf(value).toLowerCase()} 而不是 {@code property.getValue(expected)}：
	 * 前者与旧实现的比较语义<b>完全一致</b>（旧实现比的就是这个字符串），
	 * 后者走的是 {@code getSerializedName()}，个别方块上两者可能不同。
	 * 这里只在第一次遇到某个方块时跑一次，慢一点无所谓。
	 */
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

	/** 一个方块上解析好的状态条件。 */
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
					// 该方块没有这个状态名，或期望值名不存在 → 永不匹配
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
