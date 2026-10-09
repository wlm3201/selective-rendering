package com.selectiverendering.mixin.sodium;

import com.selectiverendering.BlockPosScratch;
import com.selectiverendering.BlockRenderContextAccess;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
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

/**
 * Sodium 版"面剔除修正"：对应原版的 {@code ModelBlockRendererMixin#shouldRenderFace}。
 *
 * <p>Sodium 有自己的 {@code shouldDrawSide}（不走原版 {@code Block.shouldRenderFace}），
 * 所以必须单独补一刀，逻辑完全一样：自己与邻居的隐藏状态不一致 → 强制画这个面。
 *
 * <p>{@code remap = false}：Sodium 是 Mod 代码，不走 MC 的混淆映射。
 * 这些 mixin 靠 {@code defaultRequire: 0} 保证 Sodium 缺席时静默跳过。
 *
 * <h2>alpha 从哪来</h2>
 * <p>本类<b>不</b>自己算当前方块的 alpha，而是读 {@code BlockRenderer.renderModel}
 * 在方块入口处算好并写进来的那份（见 {@link BlockRenderContextAccess}）。
 * 以前这里每次都调两次完整判定（自己 + 邻居），而"自己"那份其实在同一个方块的
 * 6 个面上被重复算了 6 遍。
 *
 * <p>只有 {@link BlockRenderContextAccess#selectiveRendering$alpha()} 返回 {@code null}
 * （不在方块作用域里，例如 {@code NonTerrainBlockRenderContext}）时才退回现算。
 *
 * <p>和原版一样，当 {@code MovingBlockRenderContext} 有值（正在渲染活塞推动中的方块）时
 * 整体跳过——那条路径由 {@code BlockFeatureRendererMixin} 自己管 alpha，避免两套逻辑打架。
 */
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
			: SelectiveRenderingManager.isHidden(state, pos);

		BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
		BlockState neighbourState = level.getBlockState(neighbourPos);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(neighbourState, neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}
}
