package com.selectiverendering.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.render.hiding.AlphaGetter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.Mesh;
import net.fabricmc.fabric.api.client.renderer.v1.render.FabricOrderedSubmitNodeCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
//? if <26.2 {
/*import net.minecraft.client.model.geom.ModelPart;
*///?}
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 转发渲染提交，并将 RenderType 替换为半透明变体。 */
abstract class TranslucentSubmitForwarder implements OrderedSubmitNodeCollector {
	protected abstract OrderedSubmitNodeCollector target();

	protected abstract int alpha();

	/** 将物品窜改进半透明管道 */
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
		int outlineColor) {
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
		ModelFeatureRenderer.@NonNull CrumblingOverlay crumblingOverlay) {
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
	public void submitBlockModel(@NonNull PoseStack poseStack, @NonNull RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords,
		int outlineColor) {
		target().submitBlockModel(poseStack, TranslucentRenderTypes.translucentVariant(renderType), parts, tintLayers, lightCoords, overlayCoords, outlineColor);
	}

	/**
	 * Fabric Renderer API（fabric-renderer-api-v1，Fabric API 始终带着它）用接口注入给
	 * {@link OrderedSubmitNodeCollector} 加了这一个带 {@link Mesh} 的重载。装了这个 API 之后，
	 * 实体内的方块模型（点燃的 TNT、漏斗矿车里的漏斗、雪傀儡的南瓜头、哞菇的蘑菇……）的四边形
	 * 全部走 Mesh 而不是 modelParts：{@code BlockModelRenderState.submitModel} 会被 Fabric 的
	 * {@code submitMesh} 拦截，改调这个重载后取消原版提交。
	 * <p>
	 * 不覆写它就会落到接口默认实现：Mesh 被整个丢掉、RenderType 也被换回
	 * {@code cutoutBlockItemSheet}，交到我们手里的 modelParts 还是空的
	 * （{@code setupMesh} 清过）——方块模型不是变淡，而是整个消失。
	 * 这里把 RenderType 函数包成半透明变体（照原样标记进 {@code HiddenRenderTypes}），
	 * Mesh 和 parts 一起转发，绘制时由 {@code RenderTypeFeatureRendererMixin} 套 alpha。
	 * <p>
	 * 没有装 Fabric Renderer API 时这个方法永远不会被调用（重载只存在于接口注入），
	 * 方块模型走上面那个原版签名的重载，行为不变。
	 */
	// 故意不加 @Override：编译期面向原版 OrderedSubmitNodeCollector，没有这个重载；
	// 运行时的接口注入会让本方法覆盖默认实现（类方法优先于接口默认方法）。
	public void submitBlockModel(
		@NonNull PoseStack poseStack,
		Function<ChunkSectionLayer, RenderType> renderTypeFunction,
		boolean translucent,
		List<BlockStateModelPart> parts,
		@Nullable Mesh mesh,
		int[] tintLayers,
		int lightCoords,
		int overlayCoords,
		int outlineColor) {
		((FabricOrderedSubmitNodeCollector) target()).submitBlockModel(
			poseStack,
			layer -> {
				RenderType renderType = renderTypeFunction.apply(layer);
				return renderType == null ? null : TranslucentRenderTypes.translucentVariant(renderType);
			},
			true,
			parts,
			mesh,
			tintLayers,
			lightCoords,
			overlayCoords,
			outlineColor);
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
		int outlineColor) {
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
		ItemStackRenderState.@NonNull FoilType foilType) {
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
