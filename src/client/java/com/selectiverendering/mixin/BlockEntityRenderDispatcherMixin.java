package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.SelectiveSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让方块实体（箱子、告示牌、信标……）跟随它所在位置的隐藏状态。
 *
 * <p>方块实体的几何不进区块网格，是每帧单独提交的，所以要单独处理：
 * <ul>
 *   <li>{@code alpha == 0}：整个不提交（箱子跟着墙一起消失）；</li>
 *   <li>{@code alpha > 0}：把 {@code SubmitNodeCollector} 换成
 *       {@link SelectiveSubmitNodeCollector}，由它在提交时把 RenderType 换成半透明变体。</li>
 * </ul>
 *
 * <p>用 {@code @WrapOperation} 而非 {@code @Redirect}，避免独占调用点与其它 Mod 冲突。
 * 每个 BE 每帧都会 new 一个包装对象，BE 密集时有额外 GC 压力（可接受）。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public class BlockEntityRenderDispatcherMixin {
	@WrapOperation(
		method = "submit",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderer;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"
		)
	)
	private <S extends BlockEntityRenderState> void selectiveRendering$submit(
		BlockEntityRenderer<?, S> renderer,
		S renderState,
		PoseStack poseStack,
		SubmitNodeCollector collector,
		CameraRenderState cameraRenderState,
		Operation<Void> original
	) {
		int alpha = SelectiveRenderingManager.getAlphaAt(renderState.blockPos);
		if (alpha == 0) {
			return;
		}

		if (alpha > 0) {
			collector = new SelectiveSubmitNodeCollector(collector);
		}

		original.call(renderer, renderState, poseStack, collector, cameraRenderState);
	}
}
