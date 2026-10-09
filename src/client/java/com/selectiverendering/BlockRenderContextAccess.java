package com.selectiverendering;

import org.jetbrains.annotations.Nullable;

/**
 * 让两个 Sodium mixin 共享"当前方块的 alpha"。
 *
 * <h2>为什么放在 {@code com.selectiverendering} 而不是 {@code ...mixin.sodium}</h2>
 * <p>{@code selective-rendering.client.mixins.json} 里写了
 * {@code "package": "com.selectiverendering.mixin"}，Mixin 会把这个包及其子包下的
 * <b>每一个类</b>都当成 mixin 来处理，并且明确禁止它们被普通代码直接引用
 * （否则报 {@code IllegalClassLoadError: ... is in a defined mixin package ...}）。
 * 所以这个"被 mixin 实现的接口"必须放在 mixin 包之外。
 * LiquidBounce 的同款接口放在 {@code net.ccbluex.liquidbounce.interfaces}，同理。
 *
 * <h2>为什么要一个接口</h2>
 * <p>Sodium 里 {@code BlockRenderer extends AbstractBlockRenderContext}：
 * <ul>
 *   <li>alpha 要在 {@code BlockRenderer.renderModel}（每个方块的入口）上算；</li>
 *   <li>但读它的地方是 {@code AbstractBlockRenderContext.shouldDrawSide}（面剔除），
 *       那个方法定义在<b>基类</b>上。</li>
 * </ul>
 * 两个 mixin 是不同的类，没法直接访问对方的 {@code @Unique} 字段，
 * 所以把字段挂在基类上（{@code AbstractBlockRenderContextMixin} 实现本接口），
 * {@code BlockRendererMixin} 通过 {@code (BlockRenderContextAccess) (Object) this} 写它。
 *
 * <h2>为什么是 {@link Integer} 而不是 {@code int}</h2>
 * <p>需要区分三种状态：
 * <ul>
 *   <li>{@code null} —— <b>没在方块作用域里</b>（例如 Sodium 的
 *       {@code NonTerrainBlockRenderContext} 那条 FRAPI 路径，它不走
 *       {@code BlockRenderer.renderModel}）。此时 {@code shouldDrawSide} 必须退回旧逻辑
 *       （自己现算），否则那条路径的淡化会整个失效；</li>
 *   <li>{@code -1} —— 在方块作用域里，但这个方块<b>不隐藏</b>；</li>
 *   <li>{@code 0..255} —— 在方块作用域里，且要淡化。</li>
 * </ul>
 * 用 {@code int} 的话 {@code -1} 会同时表示后两种含义，无法区分。
 */
public interface BlockRenderContextAccess {
	@Nullable
	Integer selectiveRendering$alpha();

	void selectiveRendering$setAlpha(@Nullable Integer alpha);
}
