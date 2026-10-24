package com.selectiverendering.matchingrule;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 客户端实体的 NBT，序列化有开销，所以每个实体隔半秒重新取一次 */
public final class EntityNbt {
	private static final int REFRESH_TICKS = 10;
	private static final int LIMIT = 4096;

	private static final Map<Integer, Entry> CACHE = new ConcurrentHashMap<>();

	private EntityNbt() {
	}

	public static CompoundTag of(int id) {
		ClientLevel level = Minecraft.getInstance().level;
		Entity entity = level == null || id < 0 ? null : level.getEntity(id);

		if (entity == null) {
			return new CompoundTag();
		}

		long now = level.getGameTime();
		Entry cached = CACHE.get(id);

		if (cached != null && now >= cached.tick && now - cached.tick < REFRESH_TICKS) {
			return cached.tag;
		}

		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());

		try {
			entity.saveWithoutId(output);
		} catch (RuntimeException ignored) {
		}

		if (CACHE.size() >= LIMIT) {
			CACHE.clear();
		}

		CompoundTag tag = output.buildResult();
		CACHE.put(id, new Entry(now, tag));
		return tag;
	}

	private record Entry(long tick, CompoundTag tag) {
	}
}
