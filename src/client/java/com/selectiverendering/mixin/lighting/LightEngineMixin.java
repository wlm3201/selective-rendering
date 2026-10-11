package com.selectiverendering.mixin.lighting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** 仅在客户端光照引擎中将隐藏方块视为空气。 */
@Mixin(LightEngine.class)
public class LightEngineMixin {
	@Shadow
	@Final
	protected LightChunkGetter chunkSource;

	@Unique
	private Boolean selectiveRendering$clientEngine;

	@WrapMethod(method = "getState")
	private BlockState selectiveRendering$hideBlock(BlockPos pos, Operation<BlockState> original) {
		if (!this.selectiveRendering$isClientEngine()) {
			return original.call(pos);
		}

		return LightSuppression.airIfHidden(original.call(pos), pos);
	}

	@Unique
	private boolean selectiveRendering$isClientEngine() {
		Boolean cached = this.selectiveRendering$clientEngine;
		if (cached != null) {
			return cached;
		}

		boolean client = this.chunkSource instanceof ClientChunkCache;
		this.selectiveRendering$clientEngine = client;
		return client;
	}
}
