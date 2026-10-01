package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Sodium 版"可见性图修正"：对应原版的 {@code SectionCompilerMixin}。
 *
 * <p>Sodium 的 {@code DirectionalVisGraph.setOpaque} 用的是 section 内的<b>局部坐标</b>
 * ({@code 0..15})，所以这里要先借助 {@code renderContext.getOrigin()}
 * 还原成世界坐标才能去问 {@code SelectiveRenderingManager}。
 *
 * <p>用 {@code @WrapOperation} 而非 {@code @Redirect}，避免与其它 Mod 冲突。
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
	@Shadow
	private ChunkRenderContext renderContext;

	@WrapOperation(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/DirectionalVisGraph;setOpaque(III)V"
		)
	)
	private void selectiveRendering$setOpaque(DirectionalVisGraph graph, int x, int y, int z, Operation<Void> original) {
		if (SelectiveRenderingManager.isHiddenAt(worldPos(x, y, z))) {
			return;
		}

		original.call(graph, x, y, z);
	}

	private BlockPos worldPos(int x, int y, int z) {
		SectionPos section = renderContext.getOrigin();
		return new BlockPos(section.getX() * 16 + x, section.getY() * 16 + y, section.getZ() * 16 + z);
	}
}
