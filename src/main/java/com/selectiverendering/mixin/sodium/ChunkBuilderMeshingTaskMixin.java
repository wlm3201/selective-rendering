package com.selectiverendering.mixin.sodium;

import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps hidden blocks out of the sight graph Sodium builds for each section, which is the same job
 * {@code SectionCompilerMixin} does for vanilla.
 *
 * <p>Sodium fills a {@link DirectionalVisGraph} while it meshes: one cell per block that stops
 * sight, and the sets it resolves from them are what later decide whether the section next door is
 * reachable from the camera at all. A block drawn translucent still stops sight in that graph
 * unless it is left out here, so every section behind it is found unreachable and dropped before a
 * single triangle is drawn. That is the whole of why caves appear and vanish as the camera turns:
 * the graph keeps saying they are not visible, and it says so from more angles than it does not.</p>
 *
 * <p>It also explains the one case that always worked. Sodium turns its culling off by itself when
 * the camera is inside a solid block, so from in there everything is drawn and the world looks
 * right - and that is exactly the behaviour this hook asks for, only without having to get inside
 * a block to see it.</p>
 *
 * <p>The cells come in the section's own coordinates, so the section origin has to be added back
 * before the manager can be asked about the position.</p>
 *
 * <p>Sodium is left to sort translucent geometry the way it sees fit. Forcing a sort behaviour on
 * every section was tried and taken out again: the order a static sort picks is fixed when the
 * section is built, so it is the right order for the one direction the faces happen to have been
 * laid out in and the wrong one for every other, which is a face that comes and goes as the
 * camera turns. It was forced on every section at that, whether or not anything in it was being
 * drawn see-through, so it reached the glass the mod was never asked about.</p>
 *
 * <p>Like the vanilla hook this runs on the build worker threads, so it may only ask about state
 * the manager shares safely.</p>
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
	@Shadow
	private ChunkRenderContext renderContext;

	@Redirect(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/DirectionalVisGraph;setOpaque(III)V"
		)
	)
	private void selectiveRendering$setOpaque(DirectionalVisGraph graph, int x, int y, int z) {
		if (SelectiveRenderingManager.isHiddenAt(worldPos(x, y, z))) {
			return;
		}

		graph.setOpaque(x, y, z);
	}

	private BlockPos worldPos(int x, int y, int z) {
		SectionPos section = renderContext.getOrigin();
		return new BlockPos(section.getX() * 16 + x, section.getY() * 16 + y, section.getZ() * 16 + z);
	}
}
