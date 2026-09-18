package com.selectiverendering.mixin;

import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the same hiding to fluids, which the block hooks never see.
 *
 * <p>A fluid is not a block model, so it is drawn by {@code FluidRenderer} rather than through
 * {@code ModelBlockRenderer}, and a hidden block would otherwise still show a full surface of
 * water or lava over it. The fluid follows the block it belongs to: if that block is hidden, so
 * is the fluid in it, at the same alpha.</p>
 *
 * <p>Fluids are baked on the same worker threads as blocks, and {@code tesselate} hands over the
 * position, so the alpha can be worked out once and kept per thread for the vertices that follow.
 * Renamed from {@code LiquidBlockRenderer} in 26.1.</p>
 */
@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
	private static final ThreadLocal<Integer> ALPHA = ThreadLocal.withInitial(() -> -1);

	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onTesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState state, FluidState fluidState, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getFluidAlpha(state, pos);
		ALPHA.set(alpha);

		// Fully hidden, so there is no point building the faces at all.
		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	/**
	 * The colour arrives as a packed argb, so only its top byte is touched and the tint the fluid
	 * already had is kept.
	 */
	@ModifyVariable(method = "vertex", at = @At("HEAD"), ordinal = 0)
	private int selectiveRendering$applyAlpha(int color) {
		int alpha = ALPHA.get();
		return alpha < 0 ? color : (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}
}
