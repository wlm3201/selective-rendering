package com.selectiverendering.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.selectiverendering.client.SelectiveRenderingManager;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
    @Unique
    private static final ThreadLocal<RenderSectionRegion> selectiveRendering$region =
            new ThreadLocal<>();

    @Inject(method = "compile", at = @At("HEAD"), require = 1)
    private void selectiveRendering$beginCompile(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir) {
        selectiveRendering$region.set(region);
    }

    @Inject(method = "compile", at = @At("TAIL"), require = 1)
    private void selectiveRendering$endCompile(
            SectionPos sectionPos,
            RenderSectionRegion region,
            VertexSorting vertexSorting,
            SectionBufferBuilderPack builders,
            CallbackInfoReturnable<SectionCompiler.Results> cir) {
        selectiveRendering$region.remove();
    }

    @WrapOperation(
            method = "compile",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"),
            require = 1)
    private void selectiveRendering$setOpaque(
            VisGraph instance, BlockPos pos, Operation<Void> original) {
        if (SelectiveRenderingManager.isHiddenIn(selectiveRendering$region.get(), pos)) {
            return;
        }

        original.call(instance, pos);
    }
}
