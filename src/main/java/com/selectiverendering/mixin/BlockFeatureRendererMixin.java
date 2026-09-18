package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Decides, per moving block, whether that block should be drawn see-through, and keeps that
 * decision alive for as long as the block is being rendered.
 *
 * <p>Moving blocks - pistons carrying them, or them falling - do not go through the section
 * compiler like static ones do. Whatever renders them is called once per block, and the pair of
 * injections around that call is where the block is still known: the render state sits in a
 * local. The alpha goes into {@link MovingBlockRenderContext}, from where the writer of the
 * vertices picks it up below.</p>
 *
 * <p>The block and the cell are taken from the render state, and never read back from the world.
 * A block a piston carries is one cell off in the level: the level already holds where it is
 * going - destination has the moving piston, the cell it left is empty - so asking the world at
 * {@code blockPos} answers for the neighbour and lands the transparency one block along. The
 * render state carries the state the block really is and the cell it is on its way out of, which
 * is also what {@link SelectiveRenderingManager#getAlpha} resolves a moving piston back to, so
 * moving blocks and the mesh around them agree.</p>
 *
 * <p>The injections are plain {@code @Inject}s and not a redirect on purpose: Fabric Renderer
 * API redirects this very call to route blocks through its own emitter, and Sodium pulls that
 * API in, so a redirect here collides with it no matter which of the two wins the application
 * order. An injection before and after the call works with either renderer underneath.</p>
 */
@Mixin(BlockFeatureRenderer.class)
public class BlockFeatureRendererMixin {
	@Unique
	private static final String selectiveRendering$TESSELATE_BLOCK = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V";

	@Unique
	private static final String selectiveRendering$GET_BUFFER = "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;";

	@Inject(
		method = "renderMovingBlockSubmits",
		at = @At(value = "INVOKE", target = selectiveRendering$TESSELATE_BLOCK),
		require = 1
	)
	private void selectiveRendering$beforeTesselateBlock(CallbackInfo ci, @Local MovingBlockRenderState renderState) {
		int alpha = SelectiveRenderingManager.getAlpha(renderState.blockState, renderState.blockPos);
		MovingBlockRenderContext.set(alpha);
	}

	@Inject(
		method = "renderMovingBlockSubmits",
		at = @At(value = "INVOKE", target = selectiveRendering$TESSELATE_BLOCK, shift = At.Shift.AFTER),
		require = 1
	)
	private void selectiveRendering$afterTesselateBlock(CallbackInfo ci) {
		MovingBlockRenderContext.clear();
	}

	/**
	 * Asks for a buffer that blends instead of the one this block would have been drawn with.
	 *
	 * <p>An opaque block is written onto {@code SOLID} here whichever layer its quads carry:
	 * vanilla picks the output for the block before it is tesselated and an opaque one is handed
	 * the layer it is going to be drawn on as a constant, so there is nothing on the quad to
	 * change that would move it. The only place left is the moment the buffer is asked for, and
	 * this is that moment. The opaque layers are drawn without blending, where an alpha in the
	 * vertices shows nothing at all, so the translucent moving block type is asked for instead.</p>
	 *
	 * <p>What is asked for is all this changes. The alpha is written into the consumer that comes
	 * back, in {@code BufferSourceHooks}, which is also where it is written on the path Fabric
	 * Renderer API redirects this one onto; see {@code FabricRendererApiMovingBlockMixin}.</p>
	 *
	 * <p>It is the call site that is wrapped and not the method: a buffer source that overrides
	 * {@code getBuffer} - ImmediatelyFast's batching one does - is what gets called, and an
	 * injection into the method it overrides never runs for it. Whoever answers, this is the
	 * instruction that asks.</p>
	 */
	@WrapOperation(
		method = "putBakedQuad",
		at = @At(value = "INVOKE", target = selectiveRendering$GET_BUFFER),
		require = 0
	)
	private static VertexConsumer selectiveRendering$translucentBuffer(
		MultiBufferSource.BufferSource bufferSource,
		RenderType renderType,
		Operation<VertexConsumer> original
	) {
		int alpha = MovingBlockRenderContext.alpha();
		if (alpha <= 0 || alpha >= 255) {
			return original.call(bufferSource, renderType);
		}

		return original.call(bufferSource, RenderTypes.translucentMovingBlock());
	}
}
