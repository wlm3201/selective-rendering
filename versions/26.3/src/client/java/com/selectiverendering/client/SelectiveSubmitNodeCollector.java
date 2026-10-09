package com.selectiverendering.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
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
            int outlineColor) {
        order(0).submitModel(
                        model,
                        state,
                        poseStack,
                        renderType,
                        lightCoords,
                        overlayCoords,
                        tintedColor,
                        uvMapping,
                        outlineColor);
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
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        order(0).submitCrumblingOverlay(
                        model,
                        state,
                        poseStack,
                        renderType,
                        lightCoords,
                        overlayCoords,
                        outlineColor,
                        crumblingOverlay);
    }

    @Override
    public void submitBlockModel(
            PoseStack poseStack,
            RenderType renderType,
            List<BlockStateModelPart> parts,
            int[] tintLayers,
            int lightCoords,
            int overlayCoords,
            int outlineColor) {
        order(0).submitBlockModel(
                        poseStack,
                        renderType,
                        parts,
                        tintLayers,
                        lightCoords,
                        overlayCoords,
                        outlineColor);
    }

    @Override
    public void submitCustomGeometry(
            PoseStack poseStack,
            RenderType renderType,
            SubmitNodeCollector.CustomGeometryRenderer renderer) {
        order(0).submitCustomGeometry(poseStack, renderType, renderer);
    }

    @Override
    public void submitShadow(
            PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
        order(0).submitShadow(poseStack, radius, pieces);
    }

    @Override
    public void submitNameTag(
            PoseStack poseStack,
            Vec3 nameTagAttachment,
            int offset,
            Component name,
            boolean seeThrough,
            int lightCoords,
            CameraRenderState camera) {
        order(0).submitNameTag(
                        poseStack,
                        nameTagAttachment,
                        offset,
                        name,
                        seeThrough,
                        lightCoords,
                        camera);
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
            int outlineColor) {
        order(0).submitText(
                        poseStack,
                        x,
                        y,
                        string,
                        dropShadow,
                        displayMode,
                        lightCoords,
                        color,
                        backgroundColor,
                        outlineColor);
    }

    @Override
    public void submitTextBackground(
            PoseStack poseStack,
            float x,
            float y,
            float width,
            float height,
            int color,
            Font.DisplayMode displayMode,
            int lightCoords) {
        order(0).submitTextBackground(
                        poseStack, x, y, width, height, color, displayMode, lightCoords);
    }

    @Override
    public void submitFlame(
            PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
        order(0).submitFlame(poseStack, renderState, rotation);
    }

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
        order(0).submitLeash(poseStack, leashState);
    }

    @Override
    public void submitMovingBlock(
            PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
        order(0).submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
    }

    @Override
    public void submitBreakingBlockModel(
            PoseStack poseStack,
            List<BlockStateModelPart> parts,
            int progress,
            boolean afterTerrain) {
        order(0).submitBreakingBlockModel(poseStack, parts, progress, afterTerrain);
    }

    @Override
    public void submitShapeOutline(
            PoseStack poseStack,
            VoxelShape shape,
            RenderType renderType,
            int color,
            float width,
            boolean afterTerrain) {
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
            ItemStackRenderState.FoilType foilType) {
        order(0).submitItem(
                        poseStack,
                        displayContext,
                        lightCoords,
                        overlayCoords,
                        outlineColor,
                        tintLayers,
                        quads,
                        foilType);
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {
        order(0).submitQuadParticleGroup(particles);
    }

    @Override
    public void submitGizmoPrimitives(
            DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
        order(0).submitGizmoPrimitives(group, camera, onTop);
    }

    private static final class SelectiveOrderedSubmitNodeCollector
            implements OrderedSubmitNodeCollector {
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
                int outlineColor) {
            base.submitModel(
                    model,
                    state,
                    poseStack,
                    TranslucentRenderTypes.translucentVariant(renderType),
                    lightCoords,
                    overlayCoords,
                    tintedColor,
                    uvMapping,
                    outlineColor);
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
                ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
            base.submitCrumblingOverlay(
                    model,
                    state,
                    poseStack,
                    TranslucentRenderTypes.translucentVariant(renderType),
                    lightCoords,
                    overlayCoords,
                    outlineColor,
                    crumblingOverlay);
        }

        @Override
        public void submitBlockModel(
                PoseStack poseStack,
                RenderType renderType,
                List<BlockStateModelPart> parts,
                int[] tintLayers,
                int lightCoords,
                int overlayCoords,
                int outlineColor) {
            base.submitBlockModel(
                    poseStack,
                    TranslucentRenderTypes.translucentVariant(renderType),
                    parts,
                    tintLayers,
                    lightCoords,
                    overlayCoords,
                    outlineColor);
        }

        @Override
        public void submitCustomGeometry(
                PoseStack poseStack,
                RenderType renderType,
                SubmitNodeCollector.CustomGeometryRenderer renderer) {
            base.submitCustomGeometry(
                    poseStack, TranslucentRenderTypes.translucentVariant(renderType), renderer);
        }

        @Override
        public void submitShadow(
                PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
            base.submitShadow(poseStack, radius, pieces);
        }

        @Override
        public void submitNameTag(
                PoseStack poseStack,
                Vec3 nameTagAttachment,
                int offset,
                Component name,
                boolean seeThrough,
                int lightCoords,
                CameraRenderState camera) {
            base.submitNameTag(
                    poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
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
                int outlineColor) {
            base.submitText(
                    poseStack,
                    x,
                    y,
                    string,
                    dropShadow,
                    displayMode,
                    lightCoords,
                    color,
                    backgroundColor,
                    outlineColor);
        }

        @Override
        public void submitTextBackground(
                PoseStack poseStack,
                float x,
                float y,
                float width,
                float height,
                int color,
                Font.DisplayMode displayMode,
                int lightCoords) {
            base.submitTextBackground(
                    poseStack, x, y, width, height, color, displayMode, lightCoords);
        }

        @Override
        public void submitFlame(
                PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
            base.submitFlame(poseStack, renderState, rotation);
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
            base.submitLeash(poseStack, leashState);
        }

        @Override
        public void submitMovingBlock(
                PoseStack poseStack,
                MovingBlockRenderState movingBlockRenderState,
                int outlineColor) {
            base.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
        }

        @Override
        public void submitBreakingBlockModel(
                PoseStack poseStack,
                List<BlockStateModelPart> parts,
                int progress,
                boolean afterTerrain) {
            base.submitBreakingBlockModel(poseStack, parts, progress, afterTerrain);
        }

        @Override
        public void submitShapeOutline(
                PoseStack poseStack,
                VoxelShape shape,
                RenderType renderType,
                int color,
                float width,
                boolean afterTerrain) {
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
                ItemStackRenderState.FoilType foilType) {
            base.submitItem(
                    poseStack,
                    displayContext,
                    lightCoords,
                    overlayCoords,
                    outlineColor,
                    tintLayers,
                    quads,
                    foilType);
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
            base.submitQuadParticleGroup(particles);
        }

        @Override
        public void submitGizmoPrimitives(
                DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
            base.submitGizmoPrimitives(group, camera, onTop);
        }
    }
}
