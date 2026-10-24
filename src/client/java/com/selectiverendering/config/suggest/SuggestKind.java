package com.selectiverendering.config.suggest;

import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.selectiverendering.matchingrule.BlockMatchRule;
import com.selectiverendering.region.RegionOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 根据注册名决定用哪个命令参数的补全 */
public enum SuggestKind {
	BLOCK, ENTITY, PARTICLE;

	/** 按光标之前的文本给出补全 */
	public boolean isValid(String entry) {
		return switch (this) {
			case BLOCK -> BlockMatchRule.isValid(entry);
			case ENTITY -> RegionOptions.validEntity(entry);
			case PARTICLE -> RegionOptions.validParticle(entry);
		};
	}

	public List<Suggestion> suggest(String text, int cursor) {
		int at = Math.clamp(cursor, 0, text.length());
		SuggestionsBuilder builder = new SuggestionsBuilder(text.substring(0, at), 0);

		CompletableFuture<Suggestions> future = switch (this) {
			case BLOCK -> BlockStateParser.fillSuggestions(blocks(), builder, true, false);
			case ENTITY -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder);
			case PARTICLE -> SharedSuggestionProvider.suggestResource(BuiltInRegistries.PARTICLE_TYPE.keySet(), builder);
		};

		try {
			return future.isDone() ? future.join().getList() : List.of();
		} catch (RuntimeException e) {
			return List.of();
		}
	}

	/** 方块标签只在进入世界后才有数据，没有世界时只补全方块 id 和状态。 */
	private static HolderLookup<Block> blocks() {
		Minecraft minecraft = Minecraft.getInstance();

		if (minecraft.level != null) {
			return minecraft.level.registryAccess().lookupOrThrow(Registries.BLOCK);
		}

		return BuiltInRegistries.BLOCK;
	}
}
