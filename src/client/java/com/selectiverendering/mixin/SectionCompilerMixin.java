package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 让"可见性图"（{@link VisGraph}）把被隐藏的方块当成<b>不挡视线的</b>。
 *
 * <p>{@code VisGraph} 决定一个 section 的哪些面组合起来能"看到外面"，
 * 是区块剔除（occlusion culling）的依据。如果我们把一整片石头变透明，
 * 但 VisGraph 仍然认为它们是不透明实体，那藏在石头后面的 section 会被误剔除，
 * 表现为"转头时后面的东西不出现"。所以这里要把隐藏方块从"不透明"里摘掉。
 *
 * <p>用的是 {@code @WrapOperation} 而非 {@code @Redirect}：
 * {@code @Redirect} 会独占调用点，别的 Mod 也改这里就会冲突；
 * {@code @WrapOperation} 可以叠加。
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	@WrapOperation(
		method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V")
	)
	private void selectiveRendering$setOpaque(VisGraph instance, BlockPos pos, Operation<Void> original) {
		if (!SelectiveRenderingManager.isHiddenAt(pos)) {
			original.call(instance, pos);
		}
	}
}
