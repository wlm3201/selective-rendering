package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
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

/**
 * <b>原版渲染管线的核心注入点</b>：决定一个方块"画不画、画成多透明、要不要剔除面"。
 *
 * <p>{@code ModelBlockRenderer} 是区块网格构建时真正把模型 quad 写进顶点的类，
 * 有两条分支：{@code tesselateFlat}（无 AO）和 {@code tesselateAmbientOcclusion}（有 AO）。
 * 注入点分工如下：
 *
 * <pre>
 *  ┌ tesselateBlock HEAD ───► 每个方块算一次 alpha，存进 BLOCK_ALPHA（下面所有点都读它）
 *  │ tesselateBlock TAIL ───► 复位成 -1，防止残留到下一个方块
 *  │
 *  ├ tesselateFlat / tesselateAmbientOcclusion HEAD ──► alpha==0 就整个取消（完全隐藏）
 *  │
 *  ├ shouldRenderFace  HEAD  ──► 自己隐藏、邻居不隐藏（或反之）时强制渲染这个面
 *  │                             （否则会露出"被挖空"的破面，像 Xray 那样）
 *  │
 *  ├ putQuadWithTint HEAD ──► 把 alpha 乘进 quadInstance 的颜色
 *  └ putQuadWithTint 里的 output.put(...)  ModifyArg ──► 换个"半透明版"的材质，
 *                                                       让这个方块落到 TRANSLUCENT 层
 * </pre>
 *
 * <h2>为什么把 alpha 提到 {@code tesselateBlock} 上算</h2>
 * <p>以前每个注入点各自调 {@code SelectiveRenderingManager.getAlpha(state, pos)}，
 * 于是同一个方块在一次 tesselate 里被完整判定 <b>20 多次</b>：
 * 原版 {@code tesselateFlat/AO} 最多按 6 个方向各调一次 {@code shouldRenderFace}
 * （每次又要判"自己"和"邻居"两个位置，即 12 次），
 * 再加上每个 quad 一次的 {@code putQuadWithTint}。
 * 而这些调用问的是<b>完全相同</b>的问题（同一个 state、同一个 pos、同一份配置）。
 *
 * <p>现在 alpha 在 {@code tesselateBlock} 入口算一次，其余注入点只读 ThreadLocal；
 * 只有"邻居"仍然需要单独判定（那本来就是另一个坐标）。
 * 这也是 LiquidBounce XRay 的做法：它在 Sodium 的 {@code renderModel} 里
 * 解析一次并写进 context 字段，{@code shouldDrawSide} 只读字段。
 *
 * <h2>关于 {@code MovingBlockRenderContext.alpha() != -1} 的判空</h2>
 * <p>活塞推动中的方块会走 {@code MovingBlockFeatureRenderer}，它内部<b>复用了</b>
 * {@code ModelBlockRenderer.tesselateBlock}。那条路径由
 * {@code BlockFeatureRendererMixin} 自己管 alpha，
 * 所以这里看到上下文有值就一律跳过，避免两套逻辑打架
 * （具体做法：{@code tesselateBlock} HEAD 在这种情况下把 BLOCK_ALPHA 设成 -1）。
 *
 * <h2>线程</h2>
 * <p>区块构建在工作线程上跑，所以 alpha 用 {@link ThreadLocal} 而不是普通字段。
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
	/**
	 * 当前正在处理的方块的 alpha（{@code -1} = 不需要处理）。必须是 ThreadLocal：网格构建是多线程的。
	 *
	 * <p>在 {@code tesselateBlock} 的 HEAD/TAIL 成对维护：
	 * 任何不经过 {@code tesselateBlock} 的调用都会看到 {@code -1}（= 原样渲染），
	 * 这是一个<b>安全的默认值</b>，不会把上一次的 alpha 错用到别的方块上。
	 */
	@Unique
	private static final ThreadLocal<Integer> selectiveRendering$blockAlpha = ThreadLocal.withInitial(() -> -1);

	@Shadow
	@Final
	private QuadInstance quadInstance;

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
		// 活塞那条路径由 MovingBlockRenderContext 自己管 alpha，这里一律不参与
		selectiveRendering$blockAlpha.set(MovingBlockRenderContext.alpha() != -1 ? -1 : SelectiveRenderingManager.getAlpha(blockState, pos));
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

		if (selectiveRendering$blockAlpha.get() == 0) {
			ci.cancel();
		}
	}

	/**
	 * 面剔除的修正。原版逻辑是"邻居是不透明实体方块就不画这个面"；
	 * 但如果我们把邻居隐藏了，还按原逻辑就会看到邻居内部——所以只要
	 * "自己"和"邻居"的隐藏状态<b>不一致</b>，就强制画这个面。
	 *
	 * <p>"自己"的 alpha 直接读 {@code tesselateBlock} 算好的那份，
	 * 只有邻居需要现算（也是这里唯一的开销）。
	 */
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

		// 注意：neighbourPos 是原版自己复用的 scratchPos，不能再拿 BlockPosScratch 去覆盖它，
		// 但这里也只需要它来查一次邻居状态，查完就用完了。
		BlockState neighbourState = level.getBlockState(neighbourPos);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(neighbourState, neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}

	/** 把 alpha 乘进顶点颜色（alpha 来自 {@code tesselateBlock}，不再重算）。 */
	@Inject(method = "putQuadWithTint", at = @At("HEAD"), require = 1)
	private void selectiveRendering$putQuadWithTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		int alpha = selectiveRendering$blockAlpha.get();

		if (alpha > 0 && alpha < 255) {
			quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
		}
	}

	@ModifyArg(
		method = "putQuadWithTint",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
		index = 3,
		require = 1
	)
	/**
	 * 把 quad 的材质换成半透明版本，让顶点 alpha 真正生效。
	 * 三层保护：alpha 无效 / 已经完全不透明 / 本来就在 TRANSLUCENT 层，都原样返回。
	 */
	private BakedQuad selectiveRendering$adjustQuad(BakedQuad quad) {
		if (MovingBlockRenderContext.alpha() != -1) {
			return quad;
		}

		int alpha = selectiveRendering$blockAlpha.get();
		BakedQuad.MaterialInfo material = quad.materialInfo();
		if (alpha <= 0 || alpha >= 255 || material.layer() == ChunkSectionLayer.TRANSLUCENT) {
			return quad;
		}

		return new BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
			quad.direction(),
			Platform.translucentMaterial(material)
		);
	}
}
