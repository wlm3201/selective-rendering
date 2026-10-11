package com.selectiverendering.mixin.hiding.blockentity;

import com.selectiverendering.render.hiding.ProjectedMovingBlock;
import com.selectiverendering.render.hiding.ProjectionPass;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 记下这个移动方块的渲染状态是不是在提交投影时创建的 */
@Mixin(MovingBlockRenderState.class)
public class MovingBlockRenderStateMixin implements ProjectedMovingBlock {
	@Unique
	private boolean selectiveRendering$projected;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void selectiveRendering$mark(CallbackInfo ci) {
		this.selectiveRendering$projected = ProjectionPass.active();
	}

	@Override
	public boolean selectiveRendering$projected() {
		return this.selectiveRendering$projected;
	}
}
