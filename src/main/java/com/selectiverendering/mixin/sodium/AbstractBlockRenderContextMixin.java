package com.selectiverendering.mixin.sodium;

import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Decides faces between a visible and a hidden block under Sodium, which is the decision vanilla
 * has no opinion on. This mirrors {@code ModelBlockRendererMixin}, so the two renderers agree.
 *
 * <p>Vanilla drops a face when the neighbour covers it completely. A hidden neighbour is drawn
 * translucent, so it covers nothing any more and the face of the visible block has to come back or
 * there is a hole: that is what makes an opaque block show through the translucent one in front of
 * it. The reverse holds too, a hidden block that sits against an opaque one needs the face between
 * them or it goes invisible.</p>
 *
 * <p>So when the two sides disagree both faces stay, the visible block's and the hidden one's,
 * which is what either of them would have had if the hidden one were the air it stands in for.
 * They meet in one and the same plane, one turned each way, and that is not something to work
 * around: a face is drawn from the side it faces and from no other, so each side of the pair
 * sees the one surface that belongs to it. Two blocks that agree, hidden or not, are left to
 * Sodium untouched, which is what keeps two adjacent hidden blocks from growing a wall of faces
 * between them.</p>
 */
@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public class AbstractBlockRenderContextMixin {
	@Shadow
	protected BlockAndTintGetter level;

	@Shadow
	protected BlockState state;

	@Shadow
	protected BlockPos pos;

	@Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$shouldDrawSide(Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return;
		}

		// Not every context is terrain: the same class renders held items and block entities, where
		// the state or the level can be missing and there is no block to ask about.
		if (level == null || state == null || pos == null) {
			return;
		}

		BlockPos neighbourPos = pos.relative(direction);
		BlockState neighbourState = level.getBlockState(neighbourPos);
		boolean hidden = SelectiveRenderingManager.isHidden(state, pos);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(neighbourState, neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}
}
