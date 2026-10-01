package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
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

/**
 * Sodium 版"流体淡化"，对应原版的 {@code FluidRendererMixin} + {@code FluidModelMixin}。
 *
 * <p>Sodium 的流体渲染比原版复杂，所以要补的点更多：
 * <ul>
 *   <li>{@code render} HEAD：算 alpha，为 0 就取消；</li>
 *   <li>{@code isFullBlockFluidSideVisible} / {@code getUpFaceExposureByNeighbors} /
 *       {@code isFluidSideExposed}：三个"这个面该不该画"的判定，
 *       邻居被隐藏时要改成"画"，否则液面会缺口；</li>
 *   <li>{@code writeQuad}：把 alpha 写进 {@code quadColors} 和 {@code vertices}（两个数组都要改，
 *       因为 Sodium 会分别用到）；材质换成 {@code DefaultMaterials.TRANSLUCENT}。</li>
 * </ul>
 *
 * <p>换材质的条件统一为 {@code 0 < alpha < 255}（见 {@code BlockRendererMixin} 的说明），
 * 避免透明度 0% 时白白把流体挪进半透明层。
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

	@Unique
	private BlockPos selectiveRendering$pos;

	@Inject(method = "render", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onRender(LevelSlice level, BlockState state, FluidState fluidState, BlockPos pos, BlockPos offset, TranslucentGeometryCollector collector, ChunkModelBuilder meshBuilder, Material material, ColorProvider<FluidState> colorProvider, FluidModel model, CallbackInfo ci) {
		int alpha = SelectiveRenderingManager.getFluidAlpha(state, pos);
		selectiveRendering$alpha = alpha;
		selectiveRendering$pos = pos;

		if (alpha == 0) {
			ci.cancel();
			return;
		}
	}

	@ModifyReturnValue(
		method = "isFullBlockFluidSideVisible(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/material/FluidState;)Z",
		at = @At("RETURN")
	)
	private boolean selectiveRendering$onIsFullBlockFluidSideVisible(boolean original, BlockGetter view, BlockPos selfPos, Direction facing, FluidState fluid) {
		if (original || !facing.getAxis().isHorizontal()) {
			return original;
		}

		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			return original;
		}

		BlockPos neighbour = selfPos.relative(facing);
		BlockState neighbourState = view.getBlockState(neighbour);
		if (!SelectiveRenderingManager.isHidden(neighbourState, neighbour)) {
			return original;
		}

		return !neighbourState.getFluidState().getType().isSame(fluid.getType());
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

		BlockPos above = origin.above();
		BlockState aboveState = level.getBlockState(above);
		if (aboveState.getFluidState().isSourceOfType(fluidState.getType())) {
			return original;
		}

		return SelectiveRenderingManager.isHidden(aboveState, above) ? bothExposed : original;
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
		if (pos == null || !SelectiveRenderingManager.isHiddenAt(pos.relative(facing))) {
			return original;
		}

		return !neighbourState.getFluidState().getType().isSame(ownState.getFluidState().getType());
	}

	@ModifyVariable(method = "writeQuad", at = @At("HEAD"), ordinal = 0)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		int alpha = selectiveRendering$alpha;
		return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
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
