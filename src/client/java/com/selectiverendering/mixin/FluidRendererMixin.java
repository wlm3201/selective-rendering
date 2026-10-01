package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 流体（水/熔岩）的淡化处理，和方块用的是同一套 alpha，但注入点不同。
 *
 * <p>三个注入点：
 * <ul>
 *   <li>{@code tesselate} HEAD：算出 alpha 存进 ThreadLocal，alpha==0 就整个取消；</li>
 *   <li>{@code isFaceOccludedByNeighbor}：邻居被隐藏时<b>不要</b>剔掉这个侧面
 *       （否则会看到液面缺一块）；只处理水平方向，垂直方向原样处理；</li>
 *   <li>{@code vertex} 的 {@code color} 参数：把 alpha 写进顶点色。</li>
 * </ul>
 *
 * <p>⚠ 两个 Mixin 细节：
 * <ol>
 *   <li>{@code @ModifyVariable(ordinal = 0)} 的 ordinal 是<b>在同类型的局部变量里数</b>的，
 *       不是"第 0 个参数"。这里 {@code int} 类型的局部变量里第 0 个正好是 {@code color}
 *       （x/y/z 是 float，被跳过了）。这个语义很反直觉，改方法签名时一定要重新核对。</li>
 *   <li>alpha==0 提前 return 时没有清掉 {@code ALPHA}/{@code POS}，
 *       会残留到下一次 {@code tesselate}（下一次开头会覆盖，所以目前无害）。</li>
 * </ol>
 *
 * <p>另见 {@code FluidModelMixin}：那里负责把流体整体挪到 TRANSLUCENT 层。
 */
@Mixin(FluidRenderer.class)
public class FluidRendererMixin {
	/** 当前正在渲染的流体的 alpha；{@code -1} = 不用处理。 */
	private static final ThreadLocal<Integer> ALPHA = ThreadLocal.withInitial(() -> -1);

	/** 当前正在渲染的流体所在坐标，供 {@code isFaceOccludedByNeighbor} 反推邻居位置。 */
	private static final ThreadLocal<BlockPos> POS = new ThreadLocal<>();

	@Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onTesselate(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output, BlockState state, FluidState fluidState, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getFluidAlpha(state, pos);
		ALPHA.set(alpha);
		POS.set(pos);

		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	@WrapOperation(
		method = "tesselate",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/FluidRenderer;isFaceOccludedByNeighbor(Lnet/minecraft/core/Direction;FLnet/minecraft/world/level/block/state/BlockState;)Z"
		)
	)
	private static boolean selectiveRendering$isFaceOccludedByNeighbor(
		Direction direction,
		float height,
		BlockState neighbour,
		Operation<Boolean> original
	) {
		if (!original.call(direction, height, neighbour)) {
			return false;
		}

		if (direction.getAxis().isVertical()) {
			return true;
		}

		BlockPos pos = POS.get();
		if (pos == null) {
			return true;
		}

		return !SelectiveRenderingManager.isHiddenAt(pos.relative(direction));
	}

	@ModifyVariable(method = "vertex", at = @At("HEAD"), ordinal = 0)
	private int selectiveRendering$applyAlpha(int color) {
		int alpha = ALPHA.get();
		return alpha < 0 ? color : (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}
}
