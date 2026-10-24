package com.selectiverendering.mixin.hiding.blockentity;

//? if >=26.2 {
import com.selectiverendering.render.hiding.ProjectedMovingBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** 为活塞移动方块设置渲染透明度和半透明 RenderType。 */
@Mixin(MovingBlockFeatureRenderer.class)
public class BlockFeatureRendererMixin {

	@WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;" +
		"tesselateBlock(Lnet/minecraft/client/renderer/block/" +
		"BlockQuadOutput;FFFLnet/minecraft/client/renderer/block" +
		"/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet" +
		"/minecraft/world/level/block/state/BlockState;" +
		"Lnet/minecraft/client/renderer/block/dispatch" +
		"/BlockStateModel;J)V"), require = 0)
	private void selectiveRendering$aroundTesselateBlock(
		ModelBlockRenderer blockRenderer,
		BlockQuadOutput output,
		float x,
		float y,
		float z,
		BlockAndTintGetter level,
		BlockPos pos,
		BlockState state,
		BlockStateModel model,
		long seed,
		Operation<Void> original) {
		MovingBlockRenderContext.set(level instanceof ProjectedMovingBlock projected && projected.selectiveRendering$projected() ? 255 : AlphaGetter.getAlpha(state, pos, true));

		try {
			original.call(blockRenderer, output, x, y, z, level, pos, state, model, seed);
		} finally {
			MovingBlockRenderContext.clear();
		}
	}

	/*@WrapOperation(method = "putBakedQuad", at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer;" +
					"getVertexBuilder(Lnet/minecraft/client/renderer/rendertype" +
					"/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;")
			, require = 0)
	private VertexConsumer selectiveRendering$translucentBuffer(RenderType renderType, Operation<VertexConsumer> original) {
			*/
	@WrapOperation(method = "putBakedQuad", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/feature/MovingBlockFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"), require = 0)
	private VertexConsumer selectiveRendering$translucentBuffer(MovingBlockFeatureRenderer instance, RenderType renderType, Operation<VertexConsumer> original) {
		int alpha = MovingBlockRenderContext.alpha();
		if (alpha <= 0 || alpha >= 255) {
			return original.call(instance, renderType);
		}

		return original.call(instance, RenderTypes.translucentMovingBlock());
	}
}
//?} else {
/*import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.ProjectedMovingBlock;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockFeatureRenderer.class)
public class BlockFeatureRendererMixin {
	@WrapOperation(
		method = "renderMovingBlockSubmits",
		at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/renderer/block/ModelBlockRenderer;" +
					"tesselateBlock(Lnet/minecraft/client/renderer/block/" +
					"BlockQuadOutput;FFFLnet/minecraft/client/renderer/block" +
					"/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet" +
					"/minecraft/world/level/block/state/BlockState;" +
					"Lnet/minecraft/client/renderer/block/dispatch" +
					"/BlockStateModel;J)V"),
		require = 1
	)
	private void selectiveRendering$aroundTesselateBlock(
		ModelBlockRenderer blockRenderer,
		BlockQuadOutput output,
		float x,
		float y,
		float z,
		BlockAndTintGetter level,
		BlockPos pos,
		BlockState state,
		BlockStateModel model,
		long seed,
		Operation<Void> original,
		@Local MovingBlockRenderState renderState
	) {
		MovingBlockRenderContext.set(renderState instanceof ProjectedMovingBlock projected && projected.selectiveRendering$projected() ? 255 : AlphaGetter.getAlpha(renderState.blockState, renderState.blockPos, true));

		try {
			original.call(blockRenderer, output, x, y, z, level, pos, state, model, seed);
		} finally {
			MovingBlockRenderContext.clear();
		}
	}

	@WrapOperation(
		method = "renderMovingBlockSubmits",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;hasMaterialFlag(I)Z"
		),
		require = 1
	)
	private boolean selectiveRendering$translucentPass(
		BlockStateModel model,
		int flag,
		Operation<Boolean> original,
		@Local MovingBlockRenderState renderState
	) {
		boolean hasTranslucentMaterial = original.call(model, flag);
		if (hasTranslucentMaterial || flag != BakedQuad.FLAG_TRANSLUCENT) {
			return hasTranslucentMaterial;
		}

		int alpha = AlphaGetter.getAlpha(renderState.blockState, renderState.blockPos, true);
		return alpha > 0 && alpha < 255;
	}

	@WrapOperation(
		method = "putBakedQuad",
		at = @At(value = "INVOKE", target =
			"Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;" +
					"getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)" +
					"Lcom/mojang/blaze3d/vertex/VertexConsumer;"),
		require = 0
	)
	private static VertexConsumer selectiveRendering$translucentBuffer(
		MultiBufferSource.BufferSource bufferSource,
		RenderType renderType,
		Operation<VertexConsumer> original
	) {
		int alpha = MovingBlockRenderContext.alpha();
		if (alpha <= 0 || alpha >= 255) {
			return original.call(bufferSource, renderType);
		}

		return original.call(bufferSource, RenderTypes.translucentMovingBlock());
	}
}
*///?}
