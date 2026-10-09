package com.selectiverendering.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.BlockPosScratch;
import com.selectiverendering.SelectiveRenderingManager;
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

/**
 * Sodium 版"可见性图修正"：对应原版的 {@code SectionCompilerMixin}。
 *
 * <p>Sodium 的 {@code DirectionalVisGraph.setOpaque} 用的是 section 内的<b>局部坐标</b>
 * ({@code 0..15})，所以这里要先借助 {@code renderContext.getOrigin()}
 * 还原成世界坐标才能去问 {@code SelectiveRenderingManager}。
 *
 * <h2>数据源：用 {@code LevelSlice}，不要碰 {@code Minecraft.getInstance().level}</h2>
 * <p>本任务跑在<b>区块构建工作线程</b>上。以前这里走
 * {@code SelectiveRenderingManager.isHiddenAt(...)}，它内部是
 * {@code Minecraft.getInstance().level.getBlockState(pos)}——从工作线程读客户端世界，
 * 而且不一定对应正在构建的那一份数据。
 *
 * <p>正确的数据源就在 {@link ChunkBuildContext} 里：
 * {@code buildContext.cache.getWorldSlice()} 返回的 {@code LevelSlice}
 * 正是整个 meshing 任务里所有 {@code getBlockState} 用的那一份（线程隔离的拷贝），
 * 所以在 {@code execute} 的 HEAD 把它记下来。
 *
 * <p>这里用 {@code @Inject(HEAD)} 而不是 {@code @WrapMethod}：
 * {@code @WrapMethod} 会重命名原方法，和下面那个同方法上的 {@code @WrapOperation}
 * 配合起来顺序不好保证；{@code @Inject} 只往方法体里插代码，与调用点改写互不干扰。
 *
 * <p>用 {@code @WrapOperation} 而非 {@code @Redirect}：后者独占调用点，容易和其它 Mod 冲突。
 */
@Mixin(value = ChunkBuilderMeshingTask.class, remap = false)
public class ChunkBuilderMeshingTaskMixin {
	@Shadow
	private ChunkRenderContext renderContext;

	/**
	 * 本次 {@code execute} 使用的世界切片；只在 {@code execute} 期间有效。
	 *
	 * <p>每次 {@code execute} 开头都会重写，所以即使上一次因为取消 / 异常没走到结尾，
	 * 也不会留下过期数据——{@code setOpaque} 只可能在 {@code execute} 里被调用。
	 */
	@Unique
	private static final ThreadLocal<BlockGetter> selectiveRendering$slice = new ThreadLocal<>();

	// 描述符写全是因为这个类还带一个协变覆写的合成桥方法，只按方法名选会同时命中两个。
	// require = 1 而不是沿用项目的 defaultRequire = 0：匹配不上时静默跳过会让遮挡图
	// 少掉"淡化方块不算遮挡"这条修正（表现为后面的区块不出现），比直接报错更难查。
	@Inject(
		method = "execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;",
		at = @At("HEAD"),
		remap = false,
		require = 1
	)
	private void selectiveRendering$beginExecute(ChunkBuildContext buildContext, CancellationToken cancellationToken, CallbackInfoReturnable<ChunkBuildOutput> cir) {
		selectiveRendering$slice.set(buildContext.cache.getWorldSlice());
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
		if (slice != null && SelectiveRenderingManager.isHiddenIn(slice, worldPos(x, y, z))) {
			return;
		}

		original.call(graph, x, y, z);
	}

	private BlockPos worldPos(int x, int y, int z) {
		SectionPos section = renderContext.getOrigin();
		return BlockPosScratch.at(section.getX() * 16 + x, section.getY() * 16 + y, section.getZ() * 16 + z);
	}
}
