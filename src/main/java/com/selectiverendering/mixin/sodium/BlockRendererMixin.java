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
 * The same hiding as the vanilla hooks, applied to Sodium's own block renderer.
 *
 * <p>Sodium builds its section geometry through a pipeline of its own, so the vanilla
 * {@code ModelBlockRenderer} is never called once it is installed, and the mod would otherwise
 * quietly do nothing. Sodium is not remapped, which is why every target here is written as it
 * appears in the jar and the mixin opts out of remapping.</p>
 *
 * <p>The alpha is worked out once per block and kept on the renderer, which Sodium gives one of
 * per build thread, so the quads of a block do not each have to ask again. A fully hidden block
 * cancels before a single quad is written.</p>
 *
 * <p>Which faces a block keeps is not decided here but in {@code AbstractBlockRenderContextMixin},
 * which is handed the level, the state and the position, where this renderer gets them as
 * arguments and does not keep them: a shadow of a field that belongs to its superclass does not
 * resolve in a mixin that is not remapped.</p>
 */
@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
	@Unique
	private int selectiveRendering$alpha = -1;

	@Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onRenderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getAlpha(state, pos);
		selectiveRendering$alpha = alpha;

		// Nothing to draw at all, so skip the block rather than buffer quads nobody will see.
		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	@Inject(method = "bufferQuad", at = @At("HEAD"))
	private void selectiveRendering$onBufferQuad(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
		int alpha = selectiveRendering$alpha;
		if (alpha < 0) {
			return;
		}

		for (int i = 0; i < 4; i++) {
			int color = quad.getColor(i);
			quad.setColor(i, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
		}
	}

	/**
	 * A quad is only blended by the pass it is buffered into, so a block that has to show through
	 * has to move to the translucent material even though it is a solid block.
	 */
	@ModifyArg(
		method = "processQuad",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
		),
		index = 2
	)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		return selectiveRendering$alpha < 0 ? material : DefaultMaterials.TRANSLUCENT;
	}

}
