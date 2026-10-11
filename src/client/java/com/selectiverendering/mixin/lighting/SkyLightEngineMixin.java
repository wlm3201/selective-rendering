package com.selectiverendering.mixin.lighting;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.SkyLightEngine;
import org.spongepowered.asm.mixin.Mixin;

/**
 *  让隐藏方块在天空光源头计算里也是空气。
 */
@Mixin(SkyLightEngine.class)
public class SkyLightEngineMixin {
	@WrapMethod(method = "getLowestSourceY")
	private int selectiveRendering$hiddenAsAir(int x, int z, int defaultValue, Operation<Integer> original) {
		int real = original.call(x, z, defaultValue);
		if (real == defaultValue) {
			return real;
		}

		LightChunkGetter getter = ((LightEngineAccessor) this).selectiveRendering$chunkSource();
		if (!(getter instanceof ClientChunkCache)) {
			return real;
		}

		LightChunk chunk = getter.getChunkForLighting(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
		return chunk == null ? real : LightSuppression.lowestSourceY(chunk, x, z, real);
	}
}
