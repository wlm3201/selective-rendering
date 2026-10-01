package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
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

/**
 * <b>活塞推动中方块（moving_piston）的淡化</b>：在 tesselate 之前把 alpha 放进
 * {@link MovingBlockRenderContext}（try/finally 保证清理），
 * 取 buffer 时把 RenderType 换成 {@code translucentMovingBlock()}。
 *
 * <p>{@code getAlpha(..., true)} 的 {@code moving = true} 让 manager 从
 * {@code PistonMovingBlockEntity} 取出真正被推的方块来判定，而不是拿壳去比对名单。
 */
@Mixin(MovingBlockFeatureRenderer.class)
public class BlockFeatureRendererMixin {
	@Unique
	private static final String selectiveRendering$TESSELATE_BLOCK = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V";

	@Unique
	private static final String selectiveRendering$GET_VERTEX_BUILDER = "Lnet/minecraft/client/renderer/feature/RenderTypeFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

	@WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target = selectiveRendering$TESSELATE_BLOCK), require = 0)
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
		Operation<Void> original
	) {
		MovingBlockRenderContext.set(SelectiveRenderingManager.getAlpha(state, pos, true));

		try {
			original.call(blockRenderer, output, x, y, z, level, pos, state, model, seed);
		} finally {
			MovingBlockRenderContext.clear();
		}
	}

	@WrapOperation(method = "putBakedQuad", at = @At(value = "INVOKE", target = selectiveRendering$GET_VERTEX_BUILDER), require = 0)
	private VertexConsumer selectiveRendering$translucentBuffer(RenderType renderType, Operation<VertexConsumer> original) {
		int alpha = MovingBlockRenderContext.alpha();
		if (alpha <= 0 || alpha >= 255) {
			return original.call(renderType);
		}

		return original.call(RenderTypes.translucentMovingBlock());
	}
}
