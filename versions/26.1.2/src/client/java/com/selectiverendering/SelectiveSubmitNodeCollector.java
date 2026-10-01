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
 * 1.21.9+ 新增渲染架构（{@code SubmitNodeCollector}）下的方块实体淡化方案。
 *
 * <p>新版渲染里 BE 的顶点不是我们直接写进去的，而是"提交一个节点"，
 * 唯一能改的就是提交时用的 RenderType。所以这里把所有 {@code submitXxx} 转发给
 * {@code order(0)} 得到的 {@link SelectiveOrderedSubmitNodeCollector}，
 * 由后者把 RenderType 换成 {@link TranslucentRenderTypes#translucentVariant} 的半透明变体。
 *
 * <p>原版 {@code SubmitNodeStorage} 自己的 {@code submitXxx} 实现就是
 * {@code order(0).submitXxx(...)}，这里照抄它的分派方式，行为等价。
 *
 * <p>⚠ 本类绝大部分是"把所有接口方法转发一遍"的样板代码，
 * {@code SubmitNodeCollector} 每次增删方法都要跟着改，
 * 漏实现的方法会静默地不被包装（表现为那个类型不变淡）。
 * 26.2 / 26.3 目录里各有一份同名实现，改的时候三份都要改。
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
