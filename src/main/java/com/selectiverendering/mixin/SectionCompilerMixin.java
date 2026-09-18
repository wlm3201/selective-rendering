package com.selectiverendering.mixin;

import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps hidden blocks out of the occlusion data of the section being built.
 *
 * <p>{@link VisGraph} records which cells of a section block sight, and that is what later decides
 * which neighbouring sections are worth drawing at all. A block that is only drawn translucent
 * still stops sight in that graph unless it is left out here, so the sections behind it get culled
 * and looking through it shows whatever was last in the depth buffer rather than the world. This
 * is the part that actually makes looking through blocks work; the alpha on the quads only decides
 * how the block itself looks.</p>
 *
 * <p>Runs on the section build worker threads, so nothing here may touch state that only the render
 * thread owns.</p>
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	@Redirect(
		method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V")
	)
	private void selectiveRendering$setOpaque(VisGraph instance, BlockPos pos) {
		if (!SelectiveRenderingManager.isHiddenAt(pos)) {
			instance.setOpaque(pos);
		}
	}
}
