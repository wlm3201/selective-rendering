package com.selectiverendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;

/**
 * Wraps the collector a hidden block entity is handed, so that everything it submits ends up
 * translucent instead of opaque.
 *
 * <p>The block entity renderers pass a render type with every model, model part and custom geometry
 * they submit, and that type decides both whether blending is on and which bucket the submit lands
 * in when the frame is drawn. Swapping it for a translucent stand-in here - see
 * {@link TranslucentRenderTypes} - is the whole trick; the alpha itself is written later, when the
 * feature renderers build vertices and ask the buffer source for the marked types.</p>
 *
 * <p>Names, text, items and every other submit that is not the geometry of the block entity itself
 * are passed through untouched, because readable text beats half-transparent text and an item is
 * not something a player looks through. The short convenience overloads on the interface funnel
 * into the long methods, and both the outer collector and the ordered view it hands out do the
 * same swapping, so whichever way a renderer reaches the submit it gets the same treatment.</p>
 */
public class SelectiveSubmitNodeCollector implements SubmitNodeCollector {
	private final SubmitNodeCollector base;

	public SelectiveSubmitNodeCollector(SubmitNodeCollector base) {
		this.base = base;
	}

	@Override
	public OrderedSubmitNodeCollector order(int order) {
		return new SelectiveOrderedSubmitNodeCollector(base.order(order));
	}

	@Override
	public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int fov, @Nullable TextureAtlasSprite sprite, int arg, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
		order(0).submitModel(model, state, poseStack, renderType, light, overlay, fov, sprite, arg, crumblingOverlay);
	}

	@Override
	public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int light, int overlay, @Nullable TextureAtlasSprite sprite, boolean cullNear, boolean cullFar, int arg, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int extra) {
		order(0).submitModelPart(modelPart, poseStack, renderType, light, overlay, sprite, cullNear, cullFar, arg, crumblingOverlay, extra);
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int fov) {
		order(0).submitBlockModel(poseStack, renderType, parts, tints, light, overlay, fov);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		order(0).submitCustomGeometry(poseStack, renderType, renderer);
	}

	@Override
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState) {
		order(0).submitMovingBlock(poseStack, renderState);
	}

	@Override
	public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int arg) {
		order(0).submitBreakingBlockModel(poseStack, model, seed, arg);
	}

	@Override
	public void submitItem(PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int fov, int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType foil) {
		order(0).submitItem(poseStack, context, light, overlay, fov, tints, quads, foil);
	}

	@Override
	public void submitShadow(PoseStack poseStack, float scale, List<EntityRenderState.ShadowPiece> pieces) {
		order(0).submitShadow(poseStack, scale, pieces);
	}

	@Override
	public void submitNameTag(PoseStack poseStack, @Nullable Vec3 offset, int backgroundColor, Component text, boolean transparent, int light, double scale, CameraRenderState cameraRenderState) {
		order(0).submitNameTag(poseStack, offset, backgroundColor, text, transparent, light, scale, cameraRenderState);
	}

	@Override
	public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode displayMode, int backgroundColor, int light, int arg1, int arg2) {
		order(0).submitText(poseStack, x, y, text, shadow, displayMode, backgroundColor, light, arg1, arg2);
	}

	@Override
	public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
		order(0).submitFlame(poseStack, renderState, rotation);
	}

	@Override
	public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
		order(0).submitLeash(poseStack, leashState);
	}

	@Override
	public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
		order(0).submitParticleGroup(renderer);
	}

	private static final class SelectiveOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {
		private final OrderedSubmitNodeCollector base;

		SelectiveOrderedSubmitNodeCollector(OrderedSubmitNodeCollector base) {
			this.base = base;
		}

		@Override
		public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int light, int overlay, int fov, @Nullable TextureAtlasSprite sprite, int arg, @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
			base.submitModel(model, state, poseStack, TranslucentRenderTypes.translucentVariant(renderType), light, overlay, fov, sprite, arg, crumblingOverlay);
		}

		@Override
		public void submitModelPart(ModelPart modelPart, PoseStack poseStack, RenderType renderType, int light, int overlay, @Nullable TextureAtlasSprite sprite, boolean cullNear, boolean cullFar, int arg, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, int extra) {
			base.submitModelPart(modelPart, poseStack, TranslucentRenderTypes.translucentVariant(renderType), light, overlay, sprite, cullNear, cullFar, arg, crumblingOverlay, extra);
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tints, int light, int overlay, int fov) {
			base.submitBlockModel(poseStack, TranslucentRenderTypes.translucentVariant(renderType), parts, tints, light, overlay, fov);
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
			base.submitCustomGeometry(poseStack, TranslucentRenderTypes.translucentVariant(renderType), renderer);
		}

		// Moving blocks carry no render type of their own; their transparency is handled where
		// their quads are written. Breaking models are already translucent through the block
		// renderer hooks, and items lost their render type argument in 26.1.

		@Override
		public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState renderState) {
			base.submitMovingBlock(poseStack, renderState);
		}

		@Override
		public void submitBreakingBlockModel(PoseStack poseStack, BlockStateModel model, long seed, int arg) {
			base.submitBreakingBlockModel(poseStack, model, seed, arg);
		}

		@Override
		public void submitItem(PoseStack poseStack, ItemDisplayContext context, int light, int overlay, int fov, int[] tints, List<BakedQuad> quads, ItemStackRenderState.FoilType foil) {
			base.submitItem(poseStack, context, light, overlay, fov, tints, quads, foil);
		}

		@Override
		public void submitShadow(PoseStack poseStack, float scale, List<EntityRenderState.ShadowPiece> pieces) {
			base.submitShadow(poseStack, scale, pieces);
		}

		@Override
		public void submitNameTag(PoseStack poseStack, @Nullable Vec3 offset, int backgroundColor, Component text, boolean transparent, int light, double scale, CameraRenderState cameraRenderState) {
			base.submitNameTag(poseStack, offset, backgroundColor, text, transparent, light, scale, cameraRenderState);
		}

		@Override
		public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence text, boolean shadow, Font.DisplayMode displayMode, int backgroundColor, int light, int arg1, int arg2) {
			base.submitText(poseStack, x, y, text, shadow, displayMode, backgroundColor, light, arg1, arg2);
		}

		@Override
		public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
			base.submitFlame(poseStack, renderState, rotation);
		}

		@Override
		public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
			base.submitLeash(poseStack, leashState);
		}

		@Override
		public void submitParticleGroup(SubmitNodeCollector.ParticleGroupRenderer renderer) {
			base.submitParticleGroup(renderer);
		}
	}
}
