package com.selectiverendering.mixin.compat;

import com.selectiverendering.SelectiveRenderingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityCulling 兼容：别把"被淡化的方块"当成实心墙。
 *
 * <h2>问题现象</h2>
 * <p>装上 EntityCulling 后，隔着几格淡化方块看活塞，活塞会"时隐时现"——
 * 视角挪一点又出现，挪回去又消失。用 EntityCulling 自己的调试（高亮被剔除者）能看到
 * 是它的<b>方块实体剔除</b>在起作用，把它配置里"跳过方块实体剔除"的名单勾上
 * {@code minecraft:piston} 也能绕过去。
 *
 * <h2>根因</h2>
 * <p>EntityCulling 在自己的剔除线程里做体素光线追踪，"这一格是不是实心墙"
 * 由 {@code dev.tr7zw.entityculling.Provider#isOpaqueFullCube} 决定，
 * 实现是 {@code state.isSolidRender()}。而我们的淡化<b>从不动方块状态</b>
 * （只改渲染：顶点 alpha + 半透明层，外加光照伪造），
 * 被淡化的石头在方块世界里仍是原状态，于是 EntityCulling 把它当实心墙，
 * 墙后面的方块实体（活塞就是 {@code PistonMovingBlockEntity}）和实体都被误剔。
 *
 * <h2>修法</h2>
 * <p>注入 {@code isOpaqueFullCube}：被淡化的位置一律回答"不是实心的"。
 * 这一条同时救两类对象——方块实体（活塞、潜影盒……）和普通实体
 * （生物、盔甲架……），后者用配置名单是拦不住的。
 *
 * <p>用字符串目标 + {@code require = 0}：没装 EntityCulling 时整个 mixin 静默跳过，
 * 和 Sodium / FRAPI 兼容层同一套路；EntityCulling 改了内部结构时也只是退回
 * "手动把它加进跳过剔除名单"的行为，不会崩游戏。
 *
 * <p>本方法在 EntityCulling 的剔除线程上被高频调用（体素遍历），
 * 所以走 {@link SelectiveRenderingManager#alphaAt} 这条<b>无记账</b>的纯查询路径，
 * 绝不能碰会写 {@code HiddenSections} 的 {@code getAlpha}。
 */
@Mixin(targets = "dev.tr7zw.entityculling.Provider", remap = false)
public class EntityCullingProviderMixin {
	@Inject(
		method = "isOpaqueFullCube(III)Z",
		at = @At("HEAD"),
		cancellable = true,
		remap = false,
		require = 0
	)
	private void selectiveRendering$fadedBlocksAreTransparent(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		if (SelectiveRenderingManager.isFadedCube(x, y, z)) {
			cir.setReturnValue(false);
		}
	}
}
