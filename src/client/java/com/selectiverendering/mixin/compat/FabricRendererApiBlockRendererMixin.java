package com.selectiverendering.mixin.compat;

import com.selectiverendering.BlockPosScratch;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * <b>FRAPI / Indigo（Fabric Renderer API）兼容补丁</b>：对应原版的 {@code ModelBlockRendererMixin}。
 *
 * <p>Indigo 是 Fabric API 提供的另一套方块渲染实现（{@code AltModelBlockRendererImpl}），
 * 一些 Mod 会强制走它。它内部类和 Mojang 的 {@code ModelBlockRenderer} 完全不同，
 * 所以要单独打一套：{@code tesselateBlock} 前后保存/清理上下文，
 * {@code transform} 里改 alpha 和层，{@code shouldCullFace} 里修面剔除。
 *
 * <p>这里用<b>实例字段</b>而不是 ThreadLocal 存上下文：
 * Indigo 的渲染器是按线程持有的，所以可行。
 *
 * <p>本文件与另外两个 {@code FabricRendererApi*} 都用
 * {@code require = 0} + {@code remap = false} + 字符串 {@code targets}：
 * 三个条件缺一不可，这样在没装 Fabric API 时才不会启动崩溃。
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl", remap = false)
public class FabricRendererApiBlockRendererMixin {
	@Unique
	private int selectiveRendering$alpha = -1;

	@Unique
	private BlockAndTintGetter selectiveRendering$level;

	@Unique
	private BlockPos selectiveRendering$pos;

	@Unique
	private BlockState selectiveRendering$blockState;

	@Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void selectiveRendering$beforeTesselateBlock(
		QuadEmitter output,
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
		selectiveRendering$level = level;
		selectiveRendering$pos = pos;
		selectiveRendering$blockState = blockState;
		selectiveRendering$alpha = SelectiveRenderingManager.getAlpha(blockState, pos);

		if (selectiveRendering$alpha == 0) {
			ci.cancel();
		}
	}

	@Inject(method = "tesselateBlock", at = @At("TAIL"), remap = false, require = 0)
	private void selectiveRendering$afterTesselateBlock(CallbackInfo ci) {
		selectiveRendering$alpha = -1;
		selectiveRendering$level = null;
		selectiveRendering$pos = null;
		selectiveRendering$blockState = null;
	}

	@Inject(method = "transform", at = @At("RETURN"), remap = false, require = 0)
	private void selectiveRendering$onTransform(MutableQuadView quad, CallbackInfoReturnable<Boolean> cir) {
		if (!cir.getReturnValue()) {
			return;
		}

		int alpha = selectiveRendering$alpha;
		if (alpha <= 0 || alpha >= 255) {
			return;
		}

		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		if (quad.chunkLayer() != ChunkSectionLayer.TRANSLUCENT) {
			quad.chunkLayer(ChunkSectionLayer.TRANSLUCENT);
		}

		quad.multiplyColor(ARGB.color(alpha, 255, 255, 255));
	}

	@Inject(method = "shouldCullFace", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
	private void selectiveRendering$onShouldCullFace(Direction direction, CallbackInfoReturnable<Boolean> cir) {
		if (direction == null || SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return;
		}

		BlockAndTintGetter level = selectiveRendering$level;
		BlockPos pos = selectiveRendering$pos;
		BlockState blockState = selectiveRendering$blockState;
		if (level == null || pos == null || blockState == null) {
			return;
		}

		// "自己"的 alpha 直接读 tesselateBlock 入口算好的那份，不要再判一遍：
		// shouldCullFace 每个面都要问一次，而同一个方块的 6 个面答案完全一样。
		boolean hidden = selectiveRendering$alpha >= 0;

		BlockPos neighbourPos = BlockPosScratch.offset(pos, direction);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(level.getBlockState(neighbourPos), neighbourPos);

		if (hidden != neighbourHidden) {
			cir.setReturnValue(false);
		}
	}
}
