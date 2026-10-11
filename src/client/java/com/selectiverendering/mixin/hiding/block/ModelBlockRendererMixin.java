package com.selectiverendering.mixin.hiding.block;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.selectiverendering.render.hiding.VanillaCapture;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 为原版方块渲染应用透明度、半透明材质和面剔除修正。 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
	/** 当前线程正在渲染的方块透明度，-1 表示正常渲染。 */
	@Unique
	private static final ThreadLocal<Integer> selectiveRendering$blockAlpha = ThreadLocal.withInitial(() -> -1);

	/** 当前线程正在渲染的方块的世界坐标。 */
	@Unique
	private static final ThreadLocal<BlockPos> selectiveRendering$blockPos = new ThreadLocal<>();

	@Unique
	private static final ThreadLocal<int[]> selectiveRendering$tint = ThreadLocal.withInitial(() -> new int[]{-1});

	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Shadow
	protected abstract int getTintColor(BlockAndTintGetter level, BlockState state, BlockPos pos, int tintIndex);

	@Inject(method = "tesselateBlock", at = @At("HEAD"), require = 1)
	private void selectiveRendering$beginBlock(
		BlockQuadOutput output,
		float x,
		float y,
		float z,
		BlockAndTintGetter level,
		BlockPos pos,
		BlockState blockState,
		BlockStateModel model,
		long seed,
		CallbackInfo ci
	) {
		selectiveRendering$blockAlpha.set(MovingBlockRenderContext.alpha() != -1 ? -1 : AlphaGetter.getAlpha(blockState, pos));
		selectiveRendering$blockPos.set(pos.immutable());
		selectiveRendering$forcedFaces.get()[0] = 0;
	}

	@Inject(method = "tesselateBlock", at = @At("TAIL"), require = 1)
	private void selectiveRendering$endBlock(
		BlockQuadOutput output,
		float x,
		float y,
		float z,
		BlockAndTintGetter level,
		BlockPos pos,
		BlockState blockState,
		BlockStateModel model,
		long seed,
		CallbackInfo ci
	) {
		selectiveRendering$blockAlpha.set(-1);
	}

	@Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$tesselate(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		if (selectiveRendering$blockAlpha.get() == 0 && !(TerrainTranslucency.capturing() && SelectiveRenderingManager.isBuildHiddenMesh()) && !TerrainTranslucency.projecting(pos)) {
			ci.cancel();
		}
	}

	/** 当前线程正在渲染的方块里，因为投影而被强制保留的面（按 Direction.ordinal 的位掩码）。 */
	@Unique
	private static final ThreadLocal<int[]> selectiveRendering$forcedFaces = ThreadLocal.withInitial(() -> new int[1]);

	/** 投影范围边界上朝着范围外的面：原本会被外面的方块剔除，投影需要它，保留，并记下这个面只给投影用。 */
	@ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"), require = 1)
	private boolean selectiveRendering$keepProjectedFace(boolean original, BlockAndTintGetter level, BlockState state, Direction direction, BlockPos neighbourPos) {
		BlockPos origin = selectiveRendering$blockPos.get();
		if (original || origin == null || MovingBlockRenderContext.alpha() != -1 || !TerrainTranslucency.projectionBoundary(origin, neighbourPos)) {
			return original;
		}

		selectiveRendering$forcedFaces.get()[0] |= 1 << direction.ordinal();
		return true;
	}

	/** 相邻方块的淡化状态不同时保留该面。 */
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$shouldRenderFace(
		BlockAndTintGetter level,
		BlockState state,
		Direction direction,
		BlockPos neighbourPos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return;
		}

		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		boolean hidden = selectiveRendering$blockAlpha.get() >= 0;

		BlockState neighbourState = level.getBlockState(neighbourPos);
		boolean neighbourHidden = AlphaGetter.isHidden(neighbourState, neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}

	@Inject(method = "putQuadWithTint", at = @At("HEAD"), require = 1)
	private void selectiveRendering$putQuadWithTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
		int tintIndex = quad.materialInfo().tintIndex();
		selectiveRendering$tint.get()[0] = tintIndex == -1 ? -1 : this.getTintColor(level, state, pos, tintIndex);

		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		int alpha = selectiveRendering$blockAlpha.get();

		if (alpha > 0 && alpha < 255) {
			quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
		}
	}

	/** 半透明方块截流到常驻缓冲；否则把需淡化的 quad 切换为半透明材质。 */
	@WrapOperation(
		method = "putQuadWithTint",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
		require = 1
	)
	private void selectiveRendering$putQuad(BlockQuadOutput output, float x, float y, float z, BakedQuad quad, QuadInstance instance, Operation<Void> original) {
		if (MovingBlockRenderContext.alpha() != -1) {
			original.call(output, x, y, z, quad, instance);
			return;
		}

		BlockPos blockPos = selectiveRendering$blockPos.get();
		if (blockPos != null && TerrainTranslucency.projecting(blockPos)) {
			// 因投影才保留的面：不用外面的方块算出来的黑色 AO，也不能进真实的网格。
			boolean forced = (selectiveRendering$forcedFaces.get()[0] & (1 << quad.direction().ordinal())) != 0;
			boolean boundary = TerrainTranslucency.projectionBoundary(blockPos, blockPos.relative(quad.direction()));

			TerrainTranslucency.addProjected(blockPos, VanillaCapture.toQuad(x, y, z, quad, instance, 255, false, boundary, selectiveRendering$tint.get()[0]));
			TerrainTranslucency.addSprite(quad.materialInfo().sprite());

			if (forced) {
				return;
			}
		}

		int alpha = selectiveRendering$blockAlpha.get();
		if (alpha == 0 && !(TerrainTranslucency.capturing() && SelectiveRenderingManager.isBuildHiddenMesh())) {
			// 被隐藏的方块只给投影用，不进真实网格。
			return;
		}

		if (alpha < 0 || alpha >= 255) {
			original.call(output, x, y, z, quad, instance);
			return;
		}

		if (TerrainTranslucency.capturing()) {
			VanillaCapture.block(blockPos, x, y, z, quad, instance, alpha);
			return;
		}

		BakedQuad.MaterialInfo material = quad.materialInfo();
		if (material.layer() != ChunkSectionLayer.TRANSLUCENT) {
			quad = new BakedQuad(
				quad.position0(), quad.position1(), quad.position2(), quad.position3(),
				quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
				quad.direction(),
				Platform.translucentMaterial(material)
			);
		}

		original.call(output, x, y, z, quad, instance);
	}
}
