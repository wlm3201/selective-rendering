package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * 四个注入点分工如下：
 *
 * <pre>
 *  ┌ tesselateFlat / tesselateAmbientOcclusion  HEAD  ──► alpha==0 就整个取消（完全隐藏）
 *  │
 *  ├ shouldRenderFace  HEAD  ──► 自己隐藏、邻居不隐藏（或反之）时强制渲染这个面
 *  │                             （否则会露出"被挖空"的破面，像 Xray 那样）
 *  │
 *  ├ putQuadWithTint HEAD ──► 记下当前 alpha，并把 alpha 乘进 quadInstance 的颜色
 *  └ putQuadWithTint 里的 output.put(...)  ModifyArg ──► 换个"半透明版"的材质，
 *                                                       让这个方块落到 TRANSLUCENT 层
 * </pre>
 *
 * <p>为什么要换材质：顶点 alpha 只有在带 blending 的渲染层才生效。
 * 原版把石头放在 {@code SOLID} 层，就算把 alpha 写成 64 也不会透明。
 * 所以必须连 {@code BakedQuad.MaterialInfo} 一起换掉。
 *
 * <h2>关于 {@code MovingBlockRenderContext.alpha() != -1} 的判空</h2>
 * <p>活塞推动中的方块会走 {@code MovingBlockFeatureRenderer}，它内部<b>复用了</b>
 * {@code ModelBlockRenderer.tesselateBlock}。那条路径由
 * {@code BlockFeatureRendererMixin} 自己管 alpha，
 * 所以这里看到上下文有值就一律跳过，避免两套逻辑打架。
 *
 * <h2>线程</h2>
 * <p>区块构建在工作线程上跑，所以 alpha 用 {@link ThreadLocal} 而不是普通字段。
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
	/** 当前正在处理的方块的 alpha（{@code -1} = 不需要处理）。必须是 ThreadLocal：网格构建是多线程的。 */
	@Unique
	private static final ThreadLocal<Integer> selectiveRendering$alpha = ThreadLocal.withInitial(() -> -1);

	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$tesselate(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		if (SelectiveRenderingManager.getAlpha(state, pos) == 0) {
			ci.cancel();
		}
	}

	/**
	 * 面剔除的修正。原版逻辑是"邻居是不透明实体方块就不画这个面"；
	 * 但如果我们把邻居隐藏了，还按原逻辑就会看到邻居内部——所以只要
	 * "自己"和"邻居"的隐藏状态<b>不一致</b>，就强制画这个面。
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

		BlockPos pos = neighbourPos.relative(direction.getOpposite());
		boolean hidden = SelectiveRenderingManager.isHidden(state, pos);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(level.getBlockState(neighbourPos), neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}

	/** 记下 alpha（供下面 {@code adjustQuad} 用），并把 alpha 乘进顶点颜色。 */
	@Inject(method = "putQuadWithTint", at = @At("HEAD"), require = 1)
	private void selectiveRendering$putQuadWithTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		int alpha = SelectiveRenderingManager.getAlpha(state, pos);
		selectiveRendering$alpha.set(alpha);

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

		int alpha = selectiveRendering$alpha.get();
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
