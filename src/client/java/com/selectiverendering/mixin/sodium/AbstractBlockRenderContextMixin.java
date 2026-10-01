package com.selectiverendering.mixin.sodium;

import com.selectiverendering.MovingBlockRenderContext;
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
 * Sodium 版"面剔除修正"：对应原版的 {@code ModelBlockRendererMixin#shouldRenderFace}。
 *
 * <p>Sodium 有自己的 {@code shouldDrawSide}（不走原版 {@code Block.shouldRenderFace}），
 * 所以必须单独补一刀，逻辑完全一样：自己与邻居的隐藏状态不一致 → 强制画这个面。
 *
 * <p>{@code remap = false}：Sodium 是 Mod 代码，不走 MC 的混淆映射。
 * 这些 mixin 靠 {@code defaultRequire: 0} 保证 Sodium 缺席时静默跳过。
 *
 * <p>和原版一样，当 {@code MovingBlockRenderContext} 有值（正在渲染活塞推动中的方块）时
 * 整体跳过——那条路径由 {@code BlockFeatureRendererMixin} 自己管 alpha，避免两套逻辑打架。
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

		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

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
