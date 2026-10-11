package com.selectiverendering.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.render.hiding.AlphaGetter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
//? if <26.2 {
/*import net.minecraft.client.model.geom.ModelPart;
*///?}
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
//? if <26.2 {
/*import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
*///?}
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
//? if >=26.2 {
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
//?}
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
//? if >=26.2 {
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
//?}
import net.minecraft.client.resources.model.geometry.BakedQuad;
//? if >=26.3 {
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
//?} else {
/*import net.minecraft.client.renderer.texture.TextureAtlasSprite;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
//? if >=26.2 {
import net.minecraft.world.phys.shapes.VoxelShape;
//?}
import org.joml.Quaternionf;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 转发渲染提交，并将 RenderType 替换为半透明变体。 */
abstract class TranslucentSubmitForwarder implements OrderedSubmitNodeCollector {
	protected abstract OrderedSubmitNodeCollector target();

	protected abstract int alpha();

	/**将物品窜改进半透明管道 */
	private void submitFadedItem(PoseStack poseStack, int lightCoords, int overlayCoords, int[] tintLayers, List<BakedQuad> quads) {
		Map<RenderType, List<BakedQuad>> groups = new LinkedHashMap<>();

		for (BakedQuad quad : quads) {
			groups.computeIfAbsent(TranslucentRenderTypes.variant(quad.materialInfo().itemRenderType()), type -> new ArrayList<>()).add(quad);
		}

		groups.forEach((renderType, group) -> target().submitCustomGeometry(poseStack, renderType, (pose, consumer) -> {
			QuadInstance instance = new QuadInstance();
			instance.setLightCoords(lightCoords);
			instance.setOverlayCoords(overlayCoords);

			for (BakedQuad quad : group) {
				BakedQuad.MaterialInfo info = quad.materialInfo();
				boolean tinted = info.isTinted() && info.tintIndex() >= 0 && info.tintIndex() < tintLayers.length;

				instance.setColor(tinted(tinted ? tintLayers[info.tintIndex()] : -1));
				consumer.putBakedQuad(pose, quad, instance);
			}
		}));
	}

	/** 把隐藏透明度乘进颜色的 alpha；调用方传入没有 alpha 的颜色时按不透明处理。 */
	private int tinted(int color) {
		int existing = color >>> 24;
		int scaled = (existing == 0 ? 255 : existing) * alpha() / 255;

		return (color & 0x00FFFFFF) | (scaled << 24);
	}


	//? if >=26.3 {
	@Override
	public <S> void submitModel(
            @NonNull Model<? super S> model,
            @NonNull S state,
            @NonNull PoseStack poseStack,
            @NonNull RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tintedColor,
            UvMapping uvMapping,
            int outlineColor
	) {
		target().submitModel(model, state, poseStack, TranslucentRenderTypes.variant(renderType), lightCoords, overlayCoords, tinted(tintedColor), uvMapping, outlineColor);
	}

	@Override
	public <S> void submitCrumblingOverlay(
			@NonNull Model<? super S> model,
			@NonNull S state,
			@NonNull PoseStack poseStack,
			@NonNull RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.@NonNull CrumblingOverlay crumblingOverlay
	) {
		target().submitCrumblingOverlay(model, state, poseStack, TranslucentRenderTypes.translucentVariant(renderType), lightCoords, overlayCoords, outlineColor, crumblingOverlay);
	}
	//?} else {
	/*@Override
	public <S> void submitModel(
		Model<? super S> model,
		S state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		TextureAtlasSprite sprite,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		target().submitModel(model, state, poseStack, TranslucentRenderTypes.variant(renderType), lightCoords, overlayCoords, tinted(tintedColor), sprite, outlineColor, crumblingOverlay);
	}
	*///?}

	//? if <26.2 {
	/*@Override
	public void submitModelPart(
		ModelPart modelPart,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		TextureAtlasSprite sprite,
		boolean cullNear,
		boolean cullFar,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
		int extra
	) {
		target().submitModelPart(modelPart, poseStack, TranslucentRenderTypes.translucentVariant(renderType), lightCoords, overlayCoords, sprite, cullNear, cullFar, outlineColor, crumblingOverlay, extra);
	}
	*///?}

	@Override
	public void submitBlockModel(@NonNull PoseStack poseStack, @NonNull RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
		target().submitBlockModel(poseStack, TranslucentRenderTypes.translucentVariant(renderType), parts, tintLayers, lightCoords, overlayCoords, outlineColor);
	}

	@Override
	public void submitCustomGeometry(@NonNull PoseStack poseStack, @NonNull RenderType renderType, SubmitNodeCollector.@NonNull CustomGeometryRenderer renderer) {
		target().submitCustomGeometry(poseStack, TranslucentRenderTypes.variant(renderType),
			(pose, consumer) -> renderer.render(pose, new AlphaVertexConsumer(consumer, alpha())));
	}


	@Override
	public void submitShadow(@NonNull PoseStack poseStack, float radius, @NonNull List<EntityRenderState.ShadowPiece> pieces) {
		target().submitShadow(poseStack, radius, pieces);
	}

	@Override
	public void submitText(
		@NonNull PoseStack poseStack,
		float x,
		float y,
		@NonNull FormattedCharSequence string,
		boolean dropShadow,
		Font.@NonNull DisplayMode displayMode,
		int lightCoords,
		int color,
		int backgroundColor,
		int outlineColor
	) {
		target().submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
	}

	@Override
	public void submitFlame(@NonNull PoseStack poseStack, @NonNull EntityRenderState renderState, @NonNull Quaternionf rotation) {
		target().submitFlame(poseStack, renderState, rotation);
	}

	@Override
	public void submitLeash(@NonNull PoseStack poseStack, EntityRenderState.@NonNull LeashState leashState) {
		target().submitLeash(poseStack, leashState);
	}


	//? if >=26.2 {
	@Override
	public void submitNameTag(@NonNull PoseStack poseStack, Vec3 nameTagAttachment, int offset, @NonNull Component name, boolean seeThrough, int lightCoords, @NonNull CameraRenderState camera) {
		target().submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
	}
	//?} else {
	/*@Override
	public void submitNameTag(PoseStack poseStack, Vec3 offset, int backgroundColor, Component text, boolean transparent, int lightCoords, double scale, CameraRenderState cameraRenderState) {
		target().submitNameTag(poseStack, offset, backgroundColor, text, transparent, lightCoords, scale, cameraRenderState);
	}
	*///?}

	//? if >=26.3 {
	@Override
	public void submitTextBackground(@NonNull PoseStack poseStack, float x, float y, float width, float height, int color, Font.@NonNull DisplayMode displayMode, int lightCoords) {
		target().submitTextBackground(poseStack, x, y, width, height, color, displayMode, lightCoords);
	}
	//?}

	//? if >=26.2 {
	@Override
	public void submitMovingBlock(@NonNull PoseStack poseStack, @NonNull MovingBlockRenderState movingBlockRenderState, int outlineColor) {
		target().submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
	}
	//?} else {
	/*@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState) {
		target().submitMovingBlock(poseStack, renderState);
	}
	*///?}

	//? if >=26.3 {
	@Override
	public void submitBreakingBlockModel(@NonNull PoseStack poseStack, @NonNull List<BlockStateModelPart> parts, int progress, boolean afterTerrain) {
		target().submitBreakingBlockModel(poseStack, parts, progress, afterTerrain);
	}
	//?} else if >=26.2 {
	/*@Override
	public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
		target().submitBreakingBlockModel(poseStack, parts, progress);
	}
	*///?} else {
	/*@Override
	public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int progress) {
		target().submitBreakingBlockModel(poseStack, model, seed, progress);
	}
	*///?}

	//? if >=26.2 {
	@Override
	public void submitShapeOutline(@NonNull PoseStack poseStack, @NonNull VoxelShape shape, @NonNull RenderType renderType, int color, float width, boolean afterTerrain) {
		target().submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
	}
	//?}

	//? if >=26.3 {
	@Override
	public void submitItem(
            @NonNull PoseStack poseStack,
            @NonNull ItemDisplayContext displayContext,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            int @NonNull [] tintLayers,
            @NonNull ItemQuads quads,
            ItemStackRenderState.@NonNull FoilType foilType
	) {
		submitFadedItem(poseStack, lightCoords, overlayCoords, tintLayers, quads.all());
	}
	//?} else {
	/*@Override
	public void submitItem(
		PoseStack poseStack,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		List<BakedQuad> quads,
		ItemStackRenderState.FoilType foilType
	) {
		submitFadedItem(poseStack, lightCoords, overlayCoords, tintLayers, quads);
	}
	*///?}

	//? if >=26.2 {
	@Override
	public void submitQuadParticleGroup(@NonNull QuadParticleRenderState particles) {
		target().submitQuadParticleGroup(particles);
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.@NonNull Group group, @NonNull CameraRenderState camera, boolean onTop) {
		target().submitGizmoPrimitives(group, camera, onTop);
	}
	//?} else {
	/*@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
		target().submitParticleGroup(renderer);
	}
	*///?}
}
