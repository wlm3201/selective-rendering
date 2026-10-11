package com.selectiverendering.mixin.hiding.compat.ImmediatelyFast;

import org.spongepowered.asm.mixin.Mixin;
//? if <26.2 {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.blockentity.BufferSourceHooks;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
import net.minecraft.client.Minecraft;
//?}

// 26.1.2 的 ImmediatelyFast 缓冲入口。
//? if <26.2 {
/*@Mixin(targets = "net.raphimc.immediatelyfast.feature.core.BatchableBufferSource", remap = false)
public class ImmediatelyFastBufferSourceMixin {
	@Inject(method = "getBuffer", at = @At("RETURN"), cancellable = true, remap = false)
	private void selectiveRendering$wrap(RenderType renderType, CallbackInfoReturnable<VertexConsumer> cir) {
		cir.setReturnValue(BufferSourceHooks.wrap(renderType, cir.getReturnValue()));
	}
}
*///?} else {
@Mixin(Minecraft.class)
public class ImmediatelyFastBufferSourceMixin {
}
//?}
