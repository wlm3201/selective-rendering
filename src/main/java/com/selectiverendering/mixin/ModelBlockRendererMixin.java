package com.selectiverendering.mixin;

import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.MovingBlockRenderContext;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Writes the alpha chosen in {@link SelectiveRenderingManager} into the quads while section
 * geometry is compiled.
 *
 * <p>Two things have to happen for a block to look translucent: the quad colours need an alpha
 * below full, and the quad has to be moved onto the {@link ChunkSectionLayer#TRANSLUCENT} layer,
 * because the opaque layers are drawn without blending.</p>
 *
 * <p>Static blocks reach this class through the section compiler, or through Sodium's renderer
 * where the Sodium hooks take over. Moving blocks - pistons and falling blocks - pass through on
 * their way as well when nothing redirects them elsewhere, but they carry their own alpha by then
 * and every hook below steps aside for them; see {@code BlockFeatureRendererMixin} for where the
 * transparency of a moving block is written.</p>
 *
 * <p>The faces a block loses are decided here too, in {@code shouldRenderFace}, which is the same
 * decision Sodium leaves to its own context. Both have to answer the same way or a face that is
 * kept under one renderer is a hole under the other.</p>
 */
@Mixin(ModelBlockRenderer.class)
public class ModelBlockRendererMixin {
	/**
	 * Alpha of the block currently being tesselated, or {@code -1} when it renders normally. Written
	 * on the section build worker threads, so it has to be thread local.
	 */
	@Unique
	private static final ThreadLocal<Integer> selectiveRendering$alpha = ThreadLocal.withInitial(() -> -1);

	@Shadow
	@Final
	private QuadInstance quadInstance;

	@Inject(method = {"tesselateFlat", "tesselateAmbientOcclusion"}, at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$tesselate(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts, BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
		// A moving block is dropped where it is submitted, before it ever reaches here, and
		// judging one here would go by the cell it left behind rather than by the block.
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		if (SelectiveRenderingManager.getAlpha(state, pos) == 0) {
			ci.cancel();
		}
	}

	/**
	 * Decides the faces between a visible and a hidden block, which is the decision vanilla has no
	 * opinion on. This mirrors {@code AbstractBlockRenderContextMixin} under Sodium, so the two
	 * renderers agree.
	 *
	 * <p>Vanilla drops a face when the neighbour covers it completely. A hidden neighbour is drawn
	 * see-through, so it covers nothing any more and the face of the visible block has to come back
	 * or there is a hole: that is what makes an opaque block show through the translucent one in
	 * front of it. The reverse holds too, a hidden block against an opaque one needs the face
	 * between them or it goes invisible.</p>
	 *
	 * <p>So when the two sides disagree both faces stay, the visible block's and the hidden one's,
	 * which is what either of them would have had if the hidden one were the air it stands in for.
	 * They meet in one and the same plane, one turned each way, and that is not something to work
	 * around: a face is drawn from the side it faces and from no other, so each side of the pair
	 * sees the one surface that belongs to it. Two blocks that agree are left to vanilla, which is
	 * what keeps two adjacent hidden blocks from growing a wall of faces between them.</p>
	 *
	 * @param neighbourPos where the block on the other side of the face is, which is what this
	 *                     method is handed rather than the position of the block being rendered.
	 */
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, require = 1)
	private void selectiveRendering$shouldRenderFace(
		BlockAndTintGetter level,
		BlockState state,
		Direction direction,
		BlockPos neighbourPos,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return;
		}

		// A moving block carries its own alpha, and the position here belongs to the piston head
		// rather than to the block that is travelling.
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		BlockPos pos = neighbourPos.relative(direction.getOpposite());
		boolean hidden = SelectiveRenderingManager.isHidden(state, pos);
		boolean neighbourHidden = SelectiveRenderingManager.isHidden(level.getBlockState(neighbourPos), neighbourPos);

		if (hidden == neighbourHidden) {
			return;
		}

		cir.setReturnValue(true);
	}

	@Inject(method = "putQuadWithTint", at = @At("HEAD"), require = 1)
	private void selectiveRendering$putQuadWithTint(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockState state, BlockPos pos, BakedQuad quad, CallbackInfo ci) {
		// On the moving block path the alpha is written in one place, in BlockFeatureRendererMixin;
		// doing it here as well would scale it twice.
		if (MovingBlockRenderContext.alpha() != -1) {
			return;
		}

		int alpha = SelectiveRenderingManager.getAlpha(state, pos);
		selectiveRendering$alpha.set(alpha);

		// White with a lower alpha only scales the alpha channel, the colours are left alone.
		if (alpha > 0 && alpha < 255) {
			quadInstance.multiplyColor(ARGB.color(alpha, 255, 255, 255));
		}
	}

	@ModifyArg(
		method = "putQuadWithTint",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/block/BlockQuadOutput;put(FFFLnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"),
		index = 3,
		require = 1
	)
	private BakedQuad selectiveRendering$adjustQuad(BakedQuad quad) {
		// The moving block path moves the layer where the quads are written, in
		// BlockFeatureRendererMixin.
		if (MovingBlockRenderContext.alpha() != -1) {
			return quad;
		}

		int alpha = selectiveRendering$alpha.get();
		BakedQuad.MaterialInfo material = quad.materialInfo();
		if (alpha <= 0 || alpha >= 255 || material.layer() == ChunkSectionLayer.TRANSLUCENT) {
			return quad;
		}

		// The layer is the only thing that changes. Where a face sits is left alone: the plane it
		// shares with the block beside it belongs to that block from that block's side of it, and
		// moving either of the two out of it opens a gap along the edges of the block instead.
		return new BakedQuad(
			quad.position0(), quad.position1(), quad.position2(), quad.position3(),
			quad.packedUV0(), quad.packedUV1(), quad.packedUV2(), quad.packedUV3(),
			quad.direction(),
			new BakedQuad.MaterialInfo(
				material.sprite(),
				ChunkSectionLayer.TRANSLUCENT,
				material.itemRenderType(),
				material.tintIndex(),
				material.shade(),
				material.lightEmission()
			)
		);
	}
}
