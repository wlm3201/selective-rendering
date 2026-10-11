package com.selectiverendering.mixin.hiding.block;

import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.render.hiding.AlphaGetter;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;

/** 使用构建任务的区块快照，将隐藏方块从原版遮挡图中排除。 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	/** 本次编译使用的区块快照。 */
	@Unique
	private static final ThreadLocal<RenderSectionRegion> selectiveRendering$region = new ThreadLocal<>();

	// 要求注入成功，避免遮挡图修正静默失效。
	@Inject(method = "compile", at = @At("HEAD"), require = 1)
	private void selectiveRendering$beginCompile(
		SectionPos sectionPos,
		RenderSectionRegion region,
		VertexSorting vertexSorting,
		SectionBufferBuilderPack builders,
		CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		selectiveRendering$region.set(region);
		TerrainTranslucency.begin(sectionPos.asLong());
	}

	@Inject(method = "compile", at = @At("TAIL"), require = 1)
	private void selectiveRendering$endCompile(
		SectionPos sectionPos,
		RenderSectionRegion region,
		VertexSorting vertexSorting,
		SectionBufferBuilderPack builders,
		CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		selectiveRendering$region.remove();
		TerrainTranslucency.finish(true);
	}

	@WrapOperation(
		method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"),
		require = 1
	)
	private void selectiveRendering$setOpaque(VisGraph instance, BlockPos pos, Operation<Void> original) {
		if (AlphaGetter.isHiddenIn(selectiveRendering$region.get(), pos)) {
			return;
		}

		original.call(instance, pos);
	}
}
