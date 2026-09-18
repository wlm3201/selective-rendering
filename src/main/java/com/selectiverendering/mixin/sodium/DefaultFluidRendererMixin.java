package com.selectiverendering.mixin.sodium;

import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The same fluid hiding, on Sodium's own fluid renderer.
 *
 * <p>Sodium keeps a scratch array of quad colours and the vertices built from it, so the alpha is
 * written into both before the quad is buffered. Fluids already go through a translucent pass in
 * most cases, but a fluid covering a hidden block still has to be told to blend, which is what
 * swapping the material does.</p>
 */
@Mixin(value = DefaultFluidRenderer.class, remap = false)
public class DefaultFluidRendererMixin {
	@Shadow
	@Final
	private int[] quadColors;

	@Shadow
	@Final
	private ChunkVertexEncoder.Vertex[] vertices;

	@Unique
	private int selectiveRendering$alpha = -1;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onRender(LevelSlice level, BlockState state, FluidState fluidState, BlockPos pos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, FluidModel model, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getFluidAlpha(state, pos);
		selectiveRendering$alpha = alpha;

		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	@ModifyVariable(method = "writeQuad", at = @At("HEAD"), ordinal = 0)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		return selectiveRendering$alpha < 0 ? material : DefaultMaterials.TRANSLUCENT;
	}

	@Inject(method = "writeQuad", at = @At("HEAD"))
	private void selectiveRendering$tintQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos pos, ModelQuadView quad, ModelQuadFacing facing, boolean shade, CallbackInfo ci) {
		int alpha = selectiveRendering$alpha;
		if (alpha < 0) {
			return;
		}

		int packed = (alpha & 0xFF) << 24;
		for (int i = 0; i < quadColors.length; i++) {
			quadColors[i] = (quadColors[i] & 0x00FFFFFF) | packed;
			vertices[i].color = quadColors[i];
		}
	}
}
