package com.selectiverendering.mixin.hiding.compat.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.selectiverendering.util.BlockPosScratch;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildContext;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask;
import net.caffeinemc.mods.sodium.client.util.task.CancellationToken;
import net.caffeinemc.mods.sodium.client.world.cloned.ChunkRenderContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 使用构建任务的世界切片，将隐藏方块从 Sodium 遮挡图中排除。 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
	@Shadow
	private ChunkRenderContext renderContext;

	/** 本次构建任务使用的世界切片。 */
	@Unique
	private static final ThreadLocal<BlockGetter> selectiveRendering$slice = new ThreadLocal<>();

	// 完整描述符排除合成桥方法，并要求遮挡图注入成功。
	@Inject(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At("HEAD"),
		remap = false,
		require = 1
	)
	private void selectiveRendering$beginExecute(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		selectiveRendering$slice.set(buildContext.cache.getWorldSlice());
		TerrainTranslucency.begin(renderContext.getOrigin().asLong());
	}

	@Inject(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At("RETURN"),
		remap = false,
		require = 1
	)
	private void selectiveRendering$endExecute(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		TerrainTranslucency.finish(cir.getReturnValue() != null);
	}

	@WrapOperation(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/occlusion/DirectionalVisGraph;setOpaque(III)V"
		),
		remap = false,
		require = 1
	)
	private void selectiveRendering$setOpaque(DirectionalVisGraph graph, int x, int y, int z, Operation<Void> original) {
		BlockGetter slice = selectiveRendering$slice.get();
		if (slice != null && AlphaGetter.isHiddenIn(slice, worldPos(x, y, z))) {
			return;
		}

		original.call(graph, x, y, z);
	}

	private BlockPos worldPos(int x, int y, int z) {
		SectionPos section = renderContext.getOrigin();
		return BlockPosScratch.at(section.getX() * 16 + x, section.getY() * 16 + y, section.getZ() * 16 + z);
	}
}
