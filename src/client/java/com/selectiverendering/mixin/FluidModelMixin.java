package com.selectiverendering.mixin;

import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把所有流体强制塞进 {@code TRANSLUCENT} 层。
 *
 * <p>原因：流体默认可能落在 {@code CUTOUT} 之类的层，那些层不开启混合（blending），
 * 顶点 alpha 写了也没用。想让水变淡就必须让它走半透明管线。
 *
 * <p>⚠ 这是<b>全局无条件</b>的（只要开着 Mod 且透明度 &lt; 100%），
 * 世界上所有水都会进半透明层，排序开销上升。详见
 * {@link SelectiveRenderingManager#shouldRenderFluidsTranslucent()}。
 */
@Mixin(FluidModel.class)
public class FluidModelMixin {
	@Inject(method = "layer", at = @At("RETURN"), cancellable = true)
	private void selectiveRendering$fluidLayer(CallbackInfoReturnable<ChunkSectionLayer> cir) {
		if (SelectiveRenderingManager.shouldRenderFluidsTranslucent()) {
			cir.setReturnValue(ChunkSectionLayer.TRANSLUCENT);
		}
	}
}
