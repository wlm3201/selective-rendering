package com.selectiverendering.mixin;

import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "夜视"：把渲染状态里的夜视强度拉满，让暗部整体提亮。
 *
 * <h2>思路来源</h2>
 * <p>meteor 的 Xray 也做全亮，但它是硬编码的（Xray 一激活就必然全亮），这里是开关。
 * 而<b>注入点的选择学自 Flashback</b>
 * （{@code com.moulberry.flashback.mixin.visuals.MixinLightmapRenderStateExtractor}）：
 * 它把"改 {@code LightmapRenderState}"这件事放在 {@code LightmapRenderStateExtractor#extract}
 * 的 RETURN，而不是像我们最初那样放在 {@code Lightmap#render} 的 HEAD。
 *
 * <h2>为什么 {@code extract} 比 {@code render} 更合适</h2>
 * <pre>
 *   LightmapRenderStateExtractor.extract(renderState, partialTicks)  ← 填充阶段，【本类注入这里】
 *   Lightmap.render(renderState)                                      ← 消费阶段，拿它去画纹理
 * </pre>
 * <ul>
 *   <li>{@code extract} 是"生成这一帧的渲染状态"的地方，在这里改语义最自然；
 *       放到 {@code Lightmap.render} 里改属于"事后修改已经生成好的数据"，比较绕。</li>
 *   <li>在 RETURN 注入，{@code renderState} 已被原版填好，我们只做覆盖，不会互相打架。</li>
 * </ul>
 *
 * <h2>为什么改的是渲染状态、而不是光照贴图纹理</h2>
 * <p>meteor 的做法（{@code LightmapMixin}）是直接把光照贴图的 GPU 纹理刷成不透明白色
 * （{@code clearColorTexture(texture, new Vector4f(1))} + {@code ci.cancel()}），
 * 采样结果恒为 1 即处处最亮。那样最直接，但要碰 {@code GpuTexture} 和
 * {@code RenderSystem.getDevice()}——这两个在 26.1/26.2 还在 {@code com.mojang.blaze3d}，
 * 26.3 才搬到 {@code com.mojang.renderpearl}，共享源码引用不了。
 *
 * <p>这里改成喂给原版自己的夜视通道。"夜视"本来就是原版提亮暗部的正确入口，
 * 不会像刷白纹理那样把亮处一起冲爆。
 *
 * <h2>关于 {@code needsUpdate}</h2>
 * <p>原版只在 {@code needsUpdate} 为真时才真正重画光照贴图。Flashback 选择不改它
 * （只在原版本来就要更新时顺带生效，额外开销为零）；我们这里<b>强制置 true</b>，
 * 因为我们是常驻开关，切换后必须<b>立刻</b>看到效果，不能等玩家走动触发更新。
 * 代价是每帧多一次 16×16 光照贴图的更新，相对整个世界渲染可以忽略。
 *
 * <p>本文件能放在共享源码里：{@code LightmapRenderStateExtractor} 类与
 * {@code extract(LightmapRenderState, float)} 签名在 26.1.2 / 26.2 / 26.3 上完全一致。
 */
@Mixin(LightmapRenderStateExtractor.class)
public class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("RETURN"))
	private void selectiveRendering$nightVision(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (!SelectiveRenderingManager.isNightVisionActive()) {
			return;
		}

		renderState.nightVisionEffectIntensity = 1.0F;
		renderState.blockFactor = 1.0F;
		renderState.skyFactor = 1.0F;
		renderState.needsUpdate = true;
	}
}
