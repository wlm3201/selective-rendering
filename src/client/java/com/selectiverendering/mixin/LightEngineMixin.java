package com.selectiverendering.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 让<b>客户端的</b>光照引擎把被隐藏的方块当成空气，否则把一整片墙淡化之后，
 * 墙后面依然是一片黑（光被墙挡住了），非常假。
 *
 * <h2>⚠ 为什么要判断"是不是客户端的引擎"</h2>
 * <p>{@code getState} 定义在抽象基类 {@link LightEngine} 上，而它的子类
 * {@code BlockLightEngine} / {@code SkyLightEngine} **服务端也有**：
 * <pre>
 *   客户端：ClientLevel → ClientChunkCache → LevelLightEngine → {Block,Sky}LightEngine
 *   服务端：ServerLevel → ServerChunkCache → ThreadedLevelLightEngine → LevelLightEngine → 同样两个
 * </pre>
 * 单人游戏（集成服务端）跑在<b>同一个 JVM</b> 里，Mixin 是全局的类转换，
 * 不做区分的话我们的判定会在服务端光照上也生效——那会污染服务端的光照数据，
 * 进而影响刷怪、作物生长、积雪融化这些游戏逻辑。本 Mod 是 client-only，不该碰。
 *
 * <p>所以这里对每个引擎实例判断一次来源（{@code chunkSource instanceof ClientChunkCache}），
 * 服务端的直接放行。
 */
@Mixin(LightEngine.class)
public class LightEngineMixin {
	@Shadow
	@Final
	protected LightChunkGetter chunkSource;

	/** 本实例属于客户端还是服务端；每台只判断一次。 */
	@Unique
	private Boolean selectiveRendering$clientEngine;

	@WrapMethod(method = "getState")
	private BlockState selectiveRendering$hideBlock(BlockPos pos, Operation<BlockState> original) {
		if (!this.selectiveRendering$isClientEngine()) {
			return original.call(pos);
		}

		return SelectiveRenderingManager.airIfHidden(original.call(pos), pos);
	}

	@Unique
	private boolean selectiveRendering$isClientEngine() {
		Boolean cached = this.selectiveRendering$clientEngine;
		if (cached != null) {
			return cached;
		}

		boolean client = this.chunkSource instanceof ClientChunkCache;
		this.selectiveRendering$clientEngine = client;
		return client;
	}
}
