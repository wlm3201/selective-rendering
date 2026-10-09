package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.BlockRenderContextAccess;
import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
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
 *   renderModel（整个方法包一层）  ──► 算 alpha；写进 render context；
 *                                    alpha==0 就不调原方法（完全隐藏）
 *   bufferQuad  HEAD  ──► 把 alpha 直接写进四个顶点的颜色（Sodium 顶点打包成 int）
 *   processQuad → bufferQuad 的 ModifyArg ──► 材质换成 DefaultMaterials.TRANSLUCENT
 * </pre>
 *
 * <p>换材质的条件是 {@code 0 < alpha < 255}，与原版 {@code ModelBlockRendererMixin} 一致：
 * alpha == 255（透明度 0%）虽然"命中了隐藏判定"，但本来就不透明，
 * 没必要挪进半透明层白白付出排序开销。
 *
 * <h2>为什么改成 {@code @WrapMethod}</h2>
 * <p>原来用 {@code @Inject(HEAD)} + {@code ci.cancel()}，alpha 存在本类的实例字段上，
 * 但那个字段从来没有被复位——一旦 {@code renderModel} 抛异常或提前返回，
 * 残留的 alpha 会跟着这个（每线程唯一的）实例带到下一个方块。
 * 改成 {@code @WrapMethod} 后可以用 {@code try/finally} 保证复位，
 * 语义也更清楚：alpha 的作用域就是"这一次 renderModel"。
 *
 * <p>alpha 同时写进 {@link BlockRenderContextAccess}，供
 * {@code AbstractBlockRenderContextMixin#shouldDrawSide} 读取——那里每个面都要问一次
 * "这个方块是不是被隐藏"，靠这个字段就能省掉一半的完整判定。
 */
@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
	/**
	 * 缓存下来的 {@code BlockRenderContextAccess} 视图。
	 *
	 * <p>Sodium 的 {@code BlockRenderer} 是<b>每线程一个实例</b>，
	 * 而这个视图只是 {@code this} 的另一个类型，缓存下来可以省掉每个方块一次类型转换。
	 */
	@Unique
	@Nullable
	private BlockRenderContextAccess selectiveRendering$context;

	@WrapMethod(method = "renderModel")
	private void selectiveRendering$renderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, Operation<Void> original) {
		BlockRenderContextAccess context = this.selectiveRendering$context();
		int alpha = SelectiveRenderingManager.getAlpha(state, pos);

		context.selectiveRendering$setAlpha(alpha);

		try {
			if (alpha != 0) {
				original.call(model, state, pos, origin);
			}
		} finally {
			// 必须复位：this 是每线程复用的实例，残留会污染下一个方块
			context.selectiveRendering$setAlpha(null);
		}
	}

	@Unique
	private BlockRenderContextAccess selectiveRendering$context() {
		BlockRenderContextAccess context = this.selectiveRendering$context;
		if (context == null) {
			context = (BlockRenderContextAccess) (Object) this;
			this.selectiveRendering$context = context;
		}

		return context;
	}

	@Inject(method = "bufferQuad", at = @At("HEAD"))
	private void selectiveRendering$onBufferQuad(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
		int alpha = this.selectiveRendering$alpha();
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
		int alpha = this.selectiveRendering$alpha();
		return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
	}

	@Unique
	private int selectiveRendering$alpha() {
		Integer alpha = this.selectiveRendering$context().selectiveRendering$alpha();
		return alpha == null ? -1 : alpha;
	}
}
