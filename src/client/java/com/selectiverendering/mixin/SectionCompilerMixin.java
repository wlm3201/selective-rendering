package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.selectiverendering.SelectiveRenderingManager;
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

import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.SectionBufferBuilderPack;

/**
 * 让"可见性图"（{@link VisGraph}）把被隐藏的方块当成<b>不挡视线的</b>。
 *
 * <p>{@code VisGraph} 决定一个 section 的哪些面组合起来能"看到外面"，
 * 是区块剔除（occlusion culling）的依据。如果我们把一整片石头变透明，
 * 但 VisGraph 仍然认为它们是不透明实体，那藏在石头后面的 section 会被误剔除，
 * 表现为"转头时后面的东西不出现"。所以这里要把隐藏方块从"不透明"里摘掉。
 *
 * <h2>数据源：用 {@code RenderSectionRegion}，不要碰 {@code Minecraft.getInstance().level}</h2>
 * <p>本方法跑在<b>区块构建工作线程</b>上。以前这里走
 * {@code SelectiveRenderingManager.isHiddenAt(pos)}，它内部是
 * {@code Minecraft.getInstance().level.getBlockState(pos)}——从工作线程读客户端世界，
 * 而且不一定对应正在构建的那一份数据（构建用的是当时抓的快照）。
 *
 * <p>{@code compile} 的第二个参数 {@code region} 正是<b>本次构建使用的那份快照</b>
 * （紧接着的 {@code tesselateBlock} 传的就是它），
 * 所以在 HEAD 把它记下来，{@code setOpaque} 里用它取方块状态即可，
 * 既不跨线程，也不用 {@code Minecraft.getInstance()}。
 *
 * <p>用的判定是 {@link SelectiveRenderingManager#isHiddenIn}：与 {@code isHiddenAt} 相比
 * 它不往 {@link com.selectiverendering.HiddenSections} 记账——
 * 一个 section 有 4096 格，而这些位置在出网格的路径里已经被记过了。
 *
 * <p>用的是 {@code @WrapOperation} 而非 {@code @Redirect}：
 * {@code @Redirect} 会独占调用点，别的 Mod 也改这里就会冲突；
 * {@code @WrapOperation} 可以叠加。
 */
@Mixin(SectionCompiler.class)
public class SectionCompilerMixin {
	/** 本次 {@code compile} 使用的区块快照；只在 {@code compile} 期间有效。 */
	@Unique
	private static final ThreadLocal<RenderSectionRegion> selectiveRendering$region = new ThreadLocal<>();

	// require = 1：本项目的 defaultRequire 是 0，签名一旦对不上会静默跳过，
	// 表现为"剔除失效但没有任何报错"（转头时淡化墙壁后面的区块不出现），极难排查。
	// SectionCompiler 是 Minecraft 类，一定存在，所以这里要求必须命中。
	@Inject(method = "compile", at = @At("HEAD"), require = 1)
	private void selectiveRendering$beginCompile(
		SectionPos sectionPos,
		RenderSectionRegion region,
		VertexSorting vertexSorting,
		SectionBufferBuilderPack builders,
		CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		selectiveRendering$region.set(region);
	}

	@Inject(method = "compile", at = @At("TAIL"), require = 1)
	private void selectiveRendering$endCompile(
		SectionPos sectionPos,
		RenderSectionRegion region,
		VertexSorting vertexSorting,
		SectionBufferBuilderPack builders,
		CallbackInfoReturnable<SectionCompiler.Results> cir
	) {
		// 及时释放：RenderSectionRegion 持有整块 section 的数据，不该挂在线程上过夜
		selectiveRendering$region.remove();
	}

	@WrapOperation(
		method = "compile",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/chunk/VisGraph;setOpaque(Lnet/minecraft/core/BlockPos;)V"),
		require = 1
	)
	private void selectiveRendering$setOpaque(VisGraph instance, BlockPos pos, Operation<Void> original) {
		if (SelectiveRenderingManager.isHiddenIn(selectiveRendering$region.get(), pos)) {
			return;
		}

		original.call(instance, pos);
	}
}
