package com.selectiverendering.mixin.hiding.compat.sodium;

import net.caffeinemc.mods.sodium.api.util.NormI8;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.util.BlockPosScratch;
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
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
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

/** 为 Sodium 流体渲染应用透明度、半透明材质和邻居面判定修正。 */
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

	@Unique
	private BlockPos selectiveRendering$pos;

	/** 这次渲染的流体里因为投影而被强制保留的面（按 Direction.ordinal 的位掩码）：只给投影用，不进真实网格。 */
	@Unique
	private int selectiveRendering$forcedFaces;

	/** 本次流体渲染使用的世界切片，供邻居判定读取。 */
	@Unique
	private BlockGetter selectiveRendering$level;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onRender(LevelSlice level, BlockState state, FluidState fluidState, BlockPos pos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, FluidModel model, CallbackInfo ci) {
		int alpha = AlphaGetter.getFluidAlpha(state, pos);
		selectiveRendering$alpha = alpha;
		selectiveRendering$pos = pos;
		selectiveRendering$level = level;
		selectiveRendering$forcedFaces = 0;

		if (alpha == 0 && !(TerrainTranslucency.capturing() && SelectiveRenderingManager.isBuildHiddenMesh()) && !TerrainTranslucency.projecting(pos)) {
			ci.cancel();
			return;
		}
	}

	@ModifyReturnValue(
		method = "isFullBlockFluidSideVisible(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$onIsFullBlockFluidSideVisible(boolean original, BlockGetter view, BlockPos selfPos, Direction facing, FluidState fluid) {
		if (original || SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return original;
		}

		BlockPos neighbour = BlockPosScratch.offset(selfPos, facing);
		BlockState neighbourState = view.getBlockState(neighbour);
		boolean sameFluid = neighbourState.getFluidState().getType().isSame(fluid.getType());
		if (!AlphaGetter.fluidNeighbourForcesFace(view, neighbour, selectiveRendering$alpha, sameFluid)) {
			return original;
		}

		return facing.getAxis().isHorizontal() || sameFluid;
	}

	@ModifyReturnValue(
		method = "isFullBlockFluidSideVisible(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$keepProjectedSide(boolean original, BlockGetter view, BlockPos selfPos, Direction facing, FluidState fluid) {
		return this.selectiveRendering$keepProjected(original, selfPos, facing);
	}

	@ModifyReturnValue(
		method = "isFluidSideExposed(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;F)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$keepProjectedExposed(boolean original, BlockAndTintGetter view, BlockState state, BlockPos selfPos, Direction facing, float height) {
		return this.selectiveRendering$keepProjected(original, selfPos, facing);
	}

	@ModifyReturnValue(
		method = "isFluidSideExposed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;F)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$keepProjectedExposedState(boolean original, BlockState ownState, BlockState neighbourState, Direction facing, float height) {
		return this.selectiveRendering$keepProjected(original, selectiveRendering$pos, facing);
	}

	/** 投影范围边界上朝着范围外的流体面：原本会被外面的方块剔除，投影需要它，保留并记下它只给投影用。 */
	@Unique
	private boolean selectiveRendering$keepProjected(boolean original, BlockPos selfPos, Direction facing) {
		if (original || selfPos == null || !TerrainTranslucency.projectionBoundary(selfPos, BlockPosScratch.offset(selfPos, facing))) {
			return original;
		}

		selectiveRendering$forcedFaces |= 1 << facing.ordinal();
		return true;
	}

	@ModifyReturnValue(
		method = "getUpFaceExposureByNeighbors(Lnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/FluidState;)I",
		at = @At("RETURN")
	)
	private int selectiveRendering$onGetUpFaceExposureByNeighbors(int original, BlockAndTintGetter level, BlockPos origin, FluidState fluidState) {
		final int bothExposed = 0b11;

		if (original == bothExposed) {
			return original;
		}

		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return original;
		}

		BlockPos above = BlockPosScratch.at(origin.getX(), origin.getY() + 1, origin.getZ());
		BlockState aboveState = level.getBlockState(above);
		if (aboveState.getFluidState().isSourceOfType(fluidState.getType())) {
			return original;
		}

		return AlphaGetter.isHidden(aboveState, above) ? bothExposed : original;
	}

	@ModifyReturnValue(
		method = "isFluidSideExposed(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/Direction;F)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$onIsFluidSideExposed(boolean original, BlockState ownState, BlockState neighbourState, Direction facing, float height) {
		if (original || facing.getAxis().isVertical()) {
			return original;
		}

		BlockPos pos = selectiveRendering$pos;
		if (pos == null) {
			return original;
		}

		BlockPos neighbour = BlockPosScratch.offset(pos, facing);
		if (!AlphaGetter.isHiddenIn(selectiveRendering$level, neighbour)) {
			return original;
		}

		return !neighbourState.getFluidState().getType().isSame(ownState.getFluidState().getType());
	}

	@ModifyVariable(method = "writeQuad", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		int alpha = selectiveRendering$alpha;
		return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
	}

	/** 光影包下半透明流体和地形一样没法靠顶点 alpha：截下四边形，改由常驻缓冲绘制。 */
	@Inject(
		method = "writeQuad",
		at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/model/quad/ModelQuadView;getSprite()Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"),
		cancellable = true
	)
	private void selectiveRendering$captureQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos pos, ModelQuadView quad, ModelQuadFacing facing, boolean shade, CallbackInfo ci) {
		BlockPos world = selectiveRendering$pos;
		if (world != null && TerrainTranslucency.projecting(world)) {
			int packed = quad.getFaceNormal();
			Direction face = TerrainTranslucency.directionOf(NormI8.unpackX(packed), NormI8.unpackY(packed), NormI8.unpackZ(packed));
			boolean boundary = TerrainTranslucency.projectionBoundary(world, BlockPosScratch.offset(world, face));

			TerrainTranslucency.addProjected(world, this.selectiveRendering$buildQuad(quad, 255, false, material.pass.isTranslucent(), boundary));
			TerrainTranslucency.addSprite(quad.getSprite());

			// 只因为投影才保留的面不进真实网格。
			if ((selectiveRendering$forcedFaces & (1 << face.ordinal())) != 0) {
				ci.cancel();
				return;
			}
		}

		int alpha = selectiveRendering$alpha;
		if (alpha == 0 && !(TerrainTranslucency.capturing() && SelectiveRenderingManager.isBuildHiddenMesh())) {
			// 被隐藏的流体只给投影用，不进真实网格。
			ci.cancel();
			return;
		}

		if (alpha < 0 || alpha >= 255 || !TerrainTranslucency.capturing() || (alpha == 0 && !SelectiveRenderingManager.isBuildHiddenMesh())) {
			return;
		}

		TerrainTranslucency.addCaptured(world != null ? world : pos, this.selectiveRendering$buildQuad(quad, alpha, true, true, false));
		TerrainTranslucency.addSprite(quad.getSprite());
		ci.cancel();
	}

	@Unique
	private TerrainTranslucency.Quad selectiveRendering$buildQuad(ModelQuadView quad, int alpha, boolean capture, boolean translucent, boolean flatShade) {
		float[] positions = new float[12];
		float[] uvs = new float[8];
		int[] colors = new int[4];
		int[] lights = new int[4];

		for (int i = 0; i < 4; i++) {
			ChunkVertexEncoder.Vertex vertex = vertices[i];

			positions[i * 3] = vertex.x;
			positions[i * 3 + 1] = vertex.y;
			positions[i * 3 + 2] = vertex.z;
			uvs[i * 2] = vertex.u;
			uvs[i * 2 + 1] = vertex.v;

			float brightness = flatShade ? TerrainTranslucency.directionalShade(TerrainTranslucency.directionOf(NormI8.unpackX(quad.getFaceNormal()), NormI8.unpackY(quad.getFaceNormal()), NormI8.unpackZ(quad.getFaceNormal()))) : Math.min(1.0F, vertex.ao);
			int abgr = vertex.color;
			int red = (int) ((abgr & 0xFF) * brightness);
			int green = (int) (((abgr >> 8) & 0xFF) * brightness);
			int blue = (int) (((abgr >> 16) & 0xFF) * brightness);
			colors[i] = (255 << 24) | (red << 16) | (green << 8) | blue;

			lights[i] = vertex.light;
		}

		int packedNormal = quad.getFaceNormal();
		return new TerrainTranslucency.Quad(positions, uvs, colors, lights, NormI8.unpackX(packedNormal), NormI8.unpackY(packedNormal), NormI8.unpackZ(packedNormal), alpha, capture && alpha == SelectiveRenderingManager.config().alpha(), translucent, null, false);
	}

	@Inject(method = "writeQuad", at = @At("HEAD"))
	private void selectiveRendering$tintQuad(ChunkModelBuilder builder, TranslucentGeometryCollector collector, Material material, BlockPos pos, ModelQuadView quad, ModelQuadFacing facing, boolean shade, CallbackInfo ci) {
		int alpha = selectiveRendering$alpha;
		if (alpha <= 0 || alpha >= 255) {
			return;
		}

		int packed = (alpha & 0xFF) << 24;
		for (int i = 0; i < quadColors.length; i++) {
			quadColors[i] = (quadColors[i] & 0x00FFFFFF) | packed;
			vertices[i].color = quadColors[i];
		}
	}
}
