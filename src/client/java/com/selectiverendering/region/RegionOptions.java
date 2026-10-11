package com.selectiverendering.region;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selectiverendering.matchingrule.BlockMatchRule;
import com.selectiverendering.matchingrule.EntityNbt;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 选区自己的隐藏对象开关和三份本地名单（方块、实体、粒子）；不可变。
 * 名单保留用户写的每一项，无法解析的项只是不参与匹配，由界面标出来。
 */
public final class RegionOptions {
	public static final RegionOptions DEFAULT = new RegionOptions(true, false, false, List.of(), List.of(), List.of());

	private static final Map<EntityType<?>, String> ENTITY_IDS = new ConcurrentHashMap<>();
	private static final Map<ParticleType<?>, String> PARTICLE_IDS = new ConcurrentHashMap<>();

	private final boolean blocks;
	private final boolean entities;
	private final boolean particles;
	private final List<String> ruleSources;
	private final List<String> entityTypes;
	private final List<String> particleTypes;
	private final int alphaOverride;
	private final int offsetX;
	private final int offsetY;
	private final int offsetZ;

	private final List<BlockMatchRule> rules;
	private final List<EntityRule> entityRules;

	public RegionOptions(boolean blocks, boolean entities, boolean particles, List<String> ruleSources, List<String> entityTypes, List<String> particleTypes) {
		this(blocks, entities, particles, ruleSources, entityTypes, particleTypes, -1);
	}

	public RegionOptions(boolean blocks, boolean entities, boolean particles, List<String> ruleSources, List<String> entityTypes, List<String> particleTypes, int alphaOverride) {
		this(blocks, entities, particles, ruleSources, entityTypes, particleTypes, alphaOverride, 0, 0, 0);
	}

	/**
	 * alphaOverride 为 -1 时跟随全局透明度，0..255 时是这个选区自己的 alpha（0 完全透明，255 不透明）；
	 * offset 是投影位移：选区的内容会被投影到偏移后的位置，全 0 表示没有投影。
	 */
	public RegionOptions(boolean blocks, boolean entities, boolean particles, List<String> ruleSources, List<String> entityTypes, List<String> particleTypes, int alphaOverride, int offsetX, int offsetY, int offsetZ) {
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.offsetZ = offsetZ;
		this.blocks = blocks;
		this.alphaOverride = alphaOverride < 0 ? -1 : Math.min(alphaOverride, 255);
		this.entities = entities;
		this.particles = particles;
		this.ruleSources = clean(ruleSources, false);
		this.entityTypes = clean(entityTypes, true);
		this.particleTypes = clean(particleTypes, true);

		List<BlockMatchRule> parsedRules = new ArrayList<>();
		for (String source : this.ruleSources) {
			BlockMatchRule rule = BlockMatchRule.parse(source);
			if (rule != null) {
				parsedRules.add(rule);
			}
		}

		List<EntityRule> parsedEntities = new ArrayList<>();
		for (String entry : this.entityTypes) {
			EntityRule rule = EntityRule.parse(entry);
			if (rule != null) {
				parsedEntities.add(rule);
			}
		}

		this.rules = List.copyOf(parsedRules);
		this.entityRules = List.copyOf(parsedEntities);
	}

	public boolean blocks() {
		return blocks;
	}

	public boolean entities() {
		return entities;
	}

	public boolean particles() {
		return particles;
	}

	/** 能解析的方块规则；原始写法见 {@link #ruleSources()}。 */
	public List<BlockMatchRule> rules() {
		return rules;
	}

	public List<String> ruleSources() {
		return ruleSources;
	}

	public List<String> entityTypes() {
		return entityTypes;
	}

	public List<String> particleTypes() {
		return particleTypes;
	}

	public int alphaOverride() {
		return alphaOverride;
	}

	public int offsetX() {
		return offsetX;
	}

	public int offsetY() {
		return offsetY;
	}

	public int offsetZ() {
		return offsetZ;
	}

	/** 是否有投影位移。 */
	public boolean hasOffset() {
		return offsetX != 0 || offsetY != 0 || offsetZ != 0;
	}

	public RegionOptions withOffset(int x, int y, int z) {
		return new RegionOptions(blocks, entities, particles, ruleSources, entityTypes, particleTypes, alphaOverride, x, y, z);
	}

	public RegionOptions withBlocks(boolean value) {
		return new RegionOptions(value, entities, particles, ruleSources, entityTypes, particleTypes, alphaOverride, offsetX, offsetY, offsetZ);
	}

	public RegionOptions withEntities(boolean value) {
		return new RegionOptions(blocks, value, particles, ruleSources, entityTypes, particleTypes, alphaOverride, offsetX, offsetY, offsetZ);
	}

	public RegionOptions withParticles(boolean value) {
		return new RegionOptions(blocks, entities, value, ruleSources, entityTypes, particleTypes, alphaOverride, offsetX, offsetY, offsetZ);
	}

	public RegionOptions withRuleSources(List<String> value) {
		return new RegionOptions(blocks, entities, particles, value, entityTypes, particleTypes, alphaOverride, offsetX, offsetY, offsetZ);
	}

	public RegionOptions withEntityTypes(List<String> value) {
		return new RegionOptions(blocks, entities, particles, ruleSources, value, particleTypes, alphaOverride, offsetX, offsetY, offsetZ);
	}

	public RegionOptions withParticleTypes(List<String> value) {
		return new RegionOptions(blocks, entities, particles, ruleSources, entityTypes, value, alphaOverride);
	}

	public RegionOptions withAlphaOverride(int value) {
		return new RegionOptions(blocks, entities, particles, ruleSources, entityTypes, particleTypes, value, offsetX, offsetY, offsetZ);
	}

	/** 以 this 为目标把 other 并进来：勾选项和透明度覆盖以 this 为准，三份名单取并集（this 的在前）。 */
	public RegionOptions mergedWith(RegionOptions other) {
		return new RegionOptions(
			blocks, entities, particles,
			concat(ruleSources, other.ruleSources),
			concat(entityTypes, other.entityTypes),
			concat(particleTypes, other.particleTypes),
			alphaOverride,
			offsetX, offsetY, offsetZ
		);
	}

	private static List<String> concat(List<String> first, List<String> second) {
		List<String> all = new ArrayList<>(first);
		all.addAll(second);
		return all;
	}

	/** 实体是否在这份名单里；名单项可以是 id，也可以在 id 后面跟 {nbt} 只匹配 NBT 包含它的实体。名单为空时谁都不在名单里。 */
	public boolean listsEntity(EntityType<?> type, int entityId) {
		if (entityRules.isEmpty()) {
			return false;
		}

		String id = ENTITY_IDS.computeIfAbsent(type, key -> BuiltInRegistries.ENTITY_TYPE.getKey(key).toString());

		for (EntityRule rule : entityRules) {
			if (rule.id.equals(id) && (rule.nbt == null || NbtUtils.compareNbt(rule.nbt, EntityNbt.of(entityId), true))) {
				return true;
			}
		}

		return false;
	}

	/** 粒子类型是否在这份名单里；类型未知的粒子不在名单里。 */
	public boolean listsParticle(ParticleType<?> type) {
		return type != null && !particleTypes.isEmpty()
			&& particleTypes.contains(PARTICLE_IDS.computeIfAbsent(type, key -> BuiltInRegistries.PARTICLE_TYPE.getKey(key).toString()));
	}

	/** 实体名单项能否解析：id 是已注册的实体类型，可选的 {nbt} 语法正确。 */
	public static boolean validEntity(String entry) {
		EntityRule rule = EntityRule.parse(entry.trim());
		return rule != null && BuiltInRegistries.ENTITY_TYPE.containsKey(Identifier.parse(rule.id));
	}

	public static boolean validParticle(String entry) {
		Identifier id = Identifier.tryParse(entry.trim().toLowerCase());
		return id != null && BuiltInRegistries.PARTICLE_TYPE.containsKey(id);
	}

	/** 只写与默认值不同的项 */
	public CompoundTag toTag() {
		CompoundTag tag = new CompoundTag();

		if (!blocks) {
			tag.putBoolean("blocks", false);
		}

		if (entities) {
			tag.putBoolean("entities", true);
		}

		if (particles) {
			tag.putBoolean("particles", true);
		}

		if (!ruleSources.isEmpty()) {
			tag.put("rules", strings(ruleSources));
		}

		if (!entityTypes.isEmpty()) {
			tag.put("entity_types", strings(entityTypes));
		}

		if (!particleTypes.isEmpty()) {
			tag.put("particle_types", strings(particleTypes));
		}

		if (alphaOverride >= 0) {
			tag.putInt("alpha", alphaOverride);
		}

		if (hasOffset()) {
			tag.putIntArray("offset", new int[]{offsetX, offsetY, offsetZ});
		}

		return tag;
	}

	public static RegionOptions fromTag(CompoundTag tag) {
		int[] offset = tag.getIntArray("offset").orElse(new int[3]);
		if (offset.length != 3) {
			offset = new int[3];
		}

		return new RegionOptions(
			tag.getBooleanOr("blocks", true),
			tag.getBooleanOr("entities", false),
			tag.getBooleanOr("particles", false),
			strings(tag.getListOrEmpty("rules")),
			strings(tag.getListOrEmpty("entity_types")),
			strings(tag.getListOrEmpty("particle_types")),
			tag.getIntOr("alpha", -1),
			offset[0], offset[1], offset[2]
		);
	}

	/** 去掉首尾空白、空项和重复项；registry 项在能识别 id 时规范成带命名空间的小写形式，识别不了的原样保留。 */
	private static List<String> clean(@Nullable List<String> entries, boolean registry) {
		LinkedHashSet<String> cleaned = new LinkedHashSet<>();

		if (entries != null) {
			for (String entry : entries) {
				String text = entry == null ? "" : entry.trim();

				if (!text.isEmpty()) {
					cleaned.add(registry ? normalize(text) : text);
				}
			}
		}

		return List.copyOf(cleaned);
	}

	private static String normalize(String text) {
		int brace = text.indexOf('{');
		Identifier id = Identifier.tryParse((brace < 0 ? text : text.substring(0, brace)).trim().toLowerCase());

		if (id == null) {
			return text;
		}

		if (brace < 0) {
			return id.toString();
		}

		try {
			return id + TagParser.parseCompoundFully(text.substring(brace)).toString();
		}
		catch (CommandSyntaxException e) {
			return text;
		}
	}

	private static ListTag strings(List<String> values) {
		ListTag list = new ListTag();

		for (String value : values) {
			list.add(StringTag.valueOf(value));
		}

		return list;
	}

	private static List<String> strings(ListTag list) {
		List<String> values = new ArrayList<>();

		for (Tag element : list) {
			element.asString().ifPresent(values::add);
		}

		return values;
	}

	private record EntityRule(String id, @Nullable CompoundTag nbt) {
		@Nullable
		static EntityRule parse(String entry) {
			int brace = entry.indexOf('{');
			Identifier id = Identifier.tryParse((brace < 0 ? entry : entry.substring(0, brace)).trim().toLowerCase());

			if (id == null) {
				return null;
			}

			if (brace < 0) {
				return new EntityRule(id.toString(), null);
			}

			try {
				return new EntityRule(id.toString(), TagParser.parseCompoundFully(entry.substring(brace)));
			}
			catch (CommandSyntaxException e) {
				return null;
			}
		}
	}
}
