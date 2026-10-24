package com.selectiverendering.mixin.hiding.block;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.Minecraft;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.render.hiding.VanillaCapture;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.util.BlockPosScratch;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为原版流体应用透明度，并修正隐藏邻居导致的面剔除。 */
@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
	@Unique
	private static final ThreadLocal<Integer> ALPHA = ThreadLocal.withInitial(() -> -1);

	@Unique
	private static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

	/** 当前流体是否属于半透明，投影时用来选管线。 */
	@Unique
	private static final ThreadLocal<Boolean> TRANSLUCENT_LAYER = ThreadLocal.withInitial(() -> false);

	/** 当前流体渲染使用的世界数据，供邻居判定读取。 */
	@Unique
	private static final ThreadLocal<BlockGetter> LEVEL = new ThreadLocal<>();

	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onTesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState state, FluidState fluidState, CallbackInfo ci) {
		int alpha = MovingBlockRenderContext.alpha() != -1 ? -1 : AlphaGetter.getFluidAlpha(state, pos);
		ALPHA.set(alpha);
		POS.set(pos);
		TRANSLUCENT_LAYER.set(Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluidState).layer() == ChunkSectionLayer.TRANSLUCENT);
		LEVEL.set(level);

		if (alpha == 0 && !TerrainTranslucency.projecting(pos)) {
			ci.cancel();
		}
	}

	@WrapOperation(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;isFaceOccludedByNeighbor(Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"))
	private static boolean selectiveRendering$isFaceOccludedByNeighbor(
		Direction direction,
		float height,
		BlockState neighborState,
		Operation<Boolean> original) {
		if (!original.call(direction, height, neighborState)) {
			return false;
		}

		// 投影范围边界上朝着范围外的面：投影需要它，不能被外面的方块遮掉。
		BlockPos boundaryPos = POS.get();
		if (boundaryPos != null && TerrainTranslucency.projectionBoundary(boundaryPos, BlockPosScratch.offset(boundaryPos, direction))) {
			return false;
		}

		if (direction.getAxis().isVertical()) {
			return true;
		}

		BlockPos pos = POS.get();
		if (pos == null) {
			return true;
		}

		BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
		return !AlphaGetter.isHiddenIn(LEVEL.get(), neighbourPos);
	}

	@WrapOperation(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;shouldRenderFace(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z"))
	private static boolean selectiveRendering$shouldRenderFace(
		FluidState fluidState,
		BlockState blockState,
		Direction direction,
		FluidState neighborFluidState,
		Operation<Boolean> original) {
		if (original.call(fluidState, blockState, direction, neighborFluidState)) {
			return true;
		}

		BlockPos boundaryPos = POS.get();
		if (boundaryPos != null && TerrainTranslucency.projectionBoundary(boundaryPos, BlockPosScratch.offset(boundaryPos, direction))) {
			return true;
		}

		return selectiveRendering$neighbourForcesFace(direction, neighborFluidState.getType().isSame(fluidState.getType()))
			&& original.call(fluidState, blockState, direction, Fluids.EMPTY.defaultFluidState());
	}

	@WrapOperation(method = "tesselate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/FluidRenderer;isNeighborSameFluid(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/material/FluidState;)Z"))
	private static boolean selectiveRendering$isNeighborSameFluid(
		FluidState fluidState,
		FluidState neighborFluidState,
		Operation<Boolean> original) {
		return original.call(fluidState, neighborFluidState) && !selectiveRendering$neighbourForcesFace(Direction.UP, true);
	}

	@Unique
	private static boolean selectiveRendering$neighbourForcesFace(Direction direction, boolean sameFluid) {
		BlockPos pos = POS.get();
		return pos != null && AlphaGetter.fluidNeighbourForcesFace(LEVEL.get(), BlockPosScratch.offset(pos, direction), ALPHA.get(), sameFluid);
	}

	/** 半透明流体截流：顶点交给常驻缓冲的采集，不写进区块。 */
	@Inject(method = "vertex", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$captureVertex(VertexConsumer consumer, float x, float y, float z, int color, float u, float v, int light, CallbackInfo ci) {
		BlockPos fluidPos = POS.get();
		if (fluidPos != null && MovingBlockRenderContext.alpha() == -1 && TerrainTranslucency.projecting(fluidPos)) {
			VanillaCapture.projectedFluidVertex(fluidPos, x, y, z, color, u, v, light, TRANSLUCENT_LAYER.get());
		}

		int alpha = ALPHA.get();
		if (alpha == 0) {
			// 被隐藏的流体只给投影用，不进真实网格。
			ci.cancel();
			return;
		}

		if (alpha > 0 && alpha < 255 && TerrainTranslucency.capturing()) {
			VanillaCapture.fluidVertex(fluidPos, x, y, z, color, u, v, light, alpha);
			ci.cancel();
		}
	}

	@ModifyVariable(method = "vertex", at = @At("HEAD"), ordinal = 0)
	private int selectiveRendering$applyAlpha(int color) {
		int alpha = ALPHA.get();
		return alpha < 0 ? color : (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}
}
