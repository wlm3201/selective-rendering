package com.selectiverendering.mixin.sodium;

import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sodium 版"方块淡化"：对应原版的 {@code ModelBlockRendererMixin}，思路一致、
 * 实现更直接，因为 Sodium 的 quad 是可以原地改的。
 *
 * <pre>
 *   renderModel HEAD  ──► 算出 alpha；alpha==0 就取消（完全隐藏）
 *   bufferQuad  HEAD  ──► 把 alpha 直接写进四个顶点的颜色（Sodium 顶点打包成 int）
 *   processQuad → bufferQuad 的 ModifyArg ──► 材质换成 DefaultMaterials.TRANSLUCENT
 * </pre>
 *
 * <p>换材质的条件是 {@code 0 < alpha < 255}，与原版 {@code ModelBlockRendererMixin} 一致：
 * alpha == 255（透明度 0%）虽然"命中了隐藏判定"，但本来就不透明，
 * 没必要挪进半透明层白白付出排序开销。
 *
 * <p>另注：{@code selectiveRendering$alpha} 是<b>实例字段</b>而非 ThreadLocal——
 * 这是对的，因为 Sodium 的 {@code BlockRenderer} 本身就是每线程一个实例。
 */
@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
	/** 当前方块的 alpha；{@code -1} = 不处理。Sodium 的 BlockRenderer 是每线程一份，所以用实例字段即可。 */
	@Unique
	private int selectiveRendering$alpha = -1;

	@Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onRenderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getAlpha(state, pos);
		selectiveRendering$alpha = alpha;

		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	@Inject(method = "bufferQuad", at = @At("HEAD"))
	private void selectiveRendering$onBufferQuad(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
		int alpha = selectiveRendering$alpha;
		if (alpha <= 0 || alpha >= 255) {
			return;
		}

		for (int i = 0; i < 4; i++) {
			int color = quad.getColor(i);
			quad.setColor(i, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
		}
	}

	@ModifyArg(
		method = "processQuad",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
		),
		index = 2
	)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		int alpha = selectiveRendering$alpha;
		return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
	}

}
