package com.selectiverendering;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

import java.util.List;

/**
 * 1.21.9+ 新增渲染架构（{@code SubmitNodeCollector}）下的方块实体淡化方案。
 *
 * <h2>它解决什么问题</h2>
 * <p>新版渲染里，BE 的顶点不是我们直接写进去的，而是"提交 ({@code submit}) 一个节点"，
 * 由引擎统一处理。想给 BE 上透明度，唯一能改的就是<b>提交时用的 RenderType</b>。
 *
 * <h2>做法</h2>
 * <ol>
 *   <li>{@code BlockEntityRenderDispatcherMixin} 把这个包装器套在真正的 collector 外面；</li>
 *   <li>所有 {@code submitXxx(...)} 都转发给 {@code order(0)} 拿到的
 *       {@link SelectiveOrderedSubmitNodeCollector}；</li>
 *   <li>后者在转发前把 {@code RenderType} 换成
 *       {@link TranslucentRenderTypes#translucentVariant} 的半透明变体。</li>
 * </ol>
 *
 * <p>为什么都走 {@code order(0)}：原版 {@code SubmitNodeStorage} 自己的
 * {@code submitXxx} 实现就是 {@code order(0).submitXxx(...)}，这里只是照抄它的分派方式，
 * 所以行为等价。<b>如果原版以后改了这个约定，这里要同步改。</b>
 *
 * <h2>⚠ 维护成本警告</h2>
 * <p>本类有 280 多行，其中绝大部分是"把所有接口方法转发一遍"的样板代码。
 * {@code SubmitNodeCollector} 每次增删方法，这里都得跟着改，
 * 而且漏实现的方法会静默地"不被包装"（表现为那个类型不变淡）。
 * 这也是它被放在 {@code versions/} 目录、按版本各存一份的原因。
 *
 * <p>顺带一提：只有走 {@link net.minecraft.client.renderer.SubmitNodeCollector}
 * 的提交会被改写；{@code submitShadow} / {@code submitNameTag} / {@code submitText} 等
 * 与方块材质无关的提交保持原样。
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
	public <S> void submitModel(
		Model<? super S> model,
		S state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int tintedColor,
		UvMapping uvMapping,
		int outlineColor
	) {
		order(0).submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
	}

	@Override
	public <S> void submitCrumblingOverlay(
		Model<? super S> model,
		S state,
		PoseStack poseStack,
		RenderType renderType,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
	) {
		order(0).submitCrumblingOverlay(model, state, poseStack, renderType, lightCoords, overlayCoords, outlineColor, crumblingOverlay);
	}

	@Override
	public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
		order(0).submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
	}

	@Override
	public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
		order(0).submitCustomGeometry(poseStack, renderType, renderer);
	}

	@Override
	public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
		order(0).submitShadow(poseStack, radius, pieces);
	}

	@Override
	public void submitNameTag(PoseStack poseStack, Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera) {
		order(0).submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
	}

	@Override
	public void submitText(
		PoseStack poseStack,
		float x,
		float y,
		FormattedCharSequence string,
		boolean dropShadow,
		Font.DisplayMode displayMode,
		int lightCoords,
		int color,
		int backgroundColor,
		int outlineColor
	) {
		order(0).submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
	}

	@Override
	public void submitTextBackground(PoseStack poseStack, float x, float y, float width, float height, int color, Font.DisplayMode displayMode, int lightCoords) {
		order(0).submitTextBackground(poseStack, x, y, width, height, color, displayMode, lightCoords);
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
	public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
		order(0).submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
	}

	@Override
	public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean afterTerrain) {
		order(0).submitBreakingBlockModel(poseStack, parts, progress, afterTerrain);
	}

	@Override
	public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
		order(0).submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
	}

	@Override
	public void submitItem(
		PoseStack poseStack,
		ItemDisplayContext displayContext,
		int lightCoords,
		int overlayCoords,
		int outlineColor,
		int[] tintLayers,
		ItemQuads quads,
		ItemStackRenderState.FoilType foilType
	) {
		order(0).submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
	}

	@Override
	public void submitQuadParticleGroup(QuadParticleRenderState particles) {
		order(0).submitQuadParticleGroup(particles);
	}

	@Override
	public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
		order(0).submitGizmoPrimitives(group, camera, onTop);
	}

	/**
	 * 真正干活的那一层：把 {@code RenderType} 换成半透明变体后转发。
	 *
	 * <p>注意并不是所有方法都需要换 RenderType——只有那些真的用 RenderType 画几何的
	 * （{@code submitModel} / {@code submitCrumblingOverlay} / {@code submitBlockModel} /
	 * {@code submitCustomGeometry}）才需要。
	 */
	private static final class SelectiveOrderedSubmitNodeCollector implements OrderedSubmitNodeCollector {
		private final OrderedSubmitNodeCollector base;

		SelectiveOrderedSubmitNodeCollector(OrderedSubmitNodeCollector base) {
			this.base = base;
		}

		@Override
		public <S> void submitModel(
			Model<? super S> model,
			S state,
			PoseStack poseStack,
			RenderType renderType,
			int lightCoords,
			int overlayCoords,
			int tintedColor,
			UvMapping uvMapping,
			int outlineColor
		) {
			base.submitModel(model, state, poseStack, TranslucentRenderTypes.translucentVariant(renderType), lightCoords, overlayCoords, tintedColor, uvMapping, outlineColor);
		}

		@Override
		public <S> void submitCrumblingOverlay(
			Model<? super S> model,
			S state,
			PoseStack poseStack,
			RenderType renderType,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			ModelFeatureRenderer.CrumblingOverlay crumblingOverlay
		) {
			base.submitCrumblingOverlay(model, state, poseStack, TranslucentRenderTypes.translucentVariant(renderType), lightCoords, overlayCoords, outlineColor, crumblingOverlay);
		}

		@Override
		public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts, int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
			base.submitBlockModel(poseStack, TranslucentRenderTypes.translucentVariant(renderType), parts, tintLayers, lightCoords, overlayCoords, outlineColor);
		}

		@Override
		public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, SubmitNodeCollector.CustomGeometryRenderer renderer) {
			base.submitCustomGeometry(poseStack, TranslucentRenderTypes.translucentVariant(renderType), renderer);
		}

		@Override
		public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
			base.submitShadow(poseStack, radius, pieces);
		}

		@Override
		public void submitNameTag(PoseStack poseStack, Vec3 nameTagAttachment, int offset, Component name, boolean seeThrough, int lightCoords, CameraRenderState camera) {
			base.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
		}

		@Override
		public void submitText(
			PoseStack poseStack,
			float x,
			float y,
			FormattedCharSequence string,
			boolean dropShadow,
			Font.DisplayMode displayMode,
			int lightCoords,
			int color,
			int backgroundColor,
			int outlineColor
		) {
			base.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color, backgroundColor, outlineColor);
		}

		@Override
		public void submitTextBackground(PoseStack poseStack, float x, float y, float width, float height, int color, Font.DisplayMode displayMode, int lightCoords) {
			base.submitTextBackground(poseStack, x, y, width, height, color, displayMode, lightCoords);
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
		public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
			base.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
		}

		@Override
		public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean afterTerrain) {
			base.submitBreakingBlockModel(poseStack, parts, progress, afterTerrain);
		}

		@Override
		public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
			base.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
		}

		@Override
		public void submitItem(
			PoseStack poseStack,
			ItemDisplayContext displayContext,
			int lightCoords,
			int overlayCoords,
			int outlineColor,
			int[] tintLayers,
			ItemQuads quads,
			ItemStackRenderState.FoilType foilType
		) {
			base.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers, quads, foilType);
		}

		@Override
		public void submitQuadParticleGroup(QuadParticleRenderState particles) {
			base.submitQuadParticleGroup(particles);
		}

		@Override
		public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
			base.submitGizmoPrimitives(group, camera, onTop);
		}
	}
}
