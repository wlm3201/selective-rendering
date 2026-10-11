package com.selectiverendering.mixin.hiding.compat.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.BlockRenderContextAccess;
import com.selectiverendering.render.hiding.MovingBlockRenderContext;
import com.selectiverendering.util.BlockPosScratch;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 修正 Sodium 面剔除，在相邻方块淡化状态不同时保留面。 */
@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class AbstractBlockRenderContextMixin implements BlockRenderContextAccess {
	@Shadow
	protected BlockAndTintGetter level;

	@Shadow
	protected BlockState state;

	@Shadow
	protected BlockPos pos;

	@Unique
	@Nullable
	private Integer selectiveRendering$alpha;

	@Unique
	private int selectiveRendering$forcedFaces;

	@Override
	public int selectiveRendering$forcedFaces() {
		return this.selectiveRendering$forcedFaces;
	}

	@Override
	public void selectiveRendering$setForcedFaces(int mask) {
		this.selectiveRendering$forcedFaces = mask;
	}

	/** 投影范围边界上朝着范围外的面：原本会被外面的方块剔除，但投影需要它，所以保留只给投影用 */
	@ModifyReturnValue(method = "shouldDrawSide", at = @At("RETURN"))
	private boolean selectiveRendering$keepProjectedFace(boolean original, Direction direction) {
		if (original || pos == null || !TerrainTranslucency.projectionBoundary(pos, BlockPosScratch.offset(pos, direction))) {
			return original;
		}

		this.selectiveRendering$forcedFaces |= 1 << direction.ordinal();
		return true;
	}

	@Override
	@Nullable
	public Integer selectiveRendering$alpha() {
		return this.selectiveRendering$alpha;
	}

	@Override
	public void selectiveRendering$setAlpha(@Nullable Integer alpha) {
		this.selectiveRendering$alpha = alpha;
	}

	@Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$shouldDrawSide(Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return;
		}

		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		if (level == null || state == null || pos == null) {
			return;
		}

		Integer cached = this.selectiveRendering$alpha;
		boolean hidden = cached != null
			? cached >= 0
			: AlphaGetter.isHidden(state, pos);

		BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
		BlockState neighbourState = level.getBlockState(neighbourPos);
		boolean neighbourHidden = AlphaGetter.isHidden(neighbourState, neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}
}
