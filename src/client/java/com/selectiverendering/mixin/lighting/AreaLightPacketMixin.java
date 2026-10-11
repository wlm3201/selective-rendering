package com.selectiverendering.mixin.lighting;

import com.selectiverendering.lighting.LightSuppression;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 收到或启用服务端区块光照后，标记客户端需要重算的区块。 */
@Mixin(ClientPacketListener.class)
public class AreaLightPacketMixin {
	@Inject(method = "applyLightData", at = @At("RETURN"))
	private void selectiveRendering$markLightData(int x, int z, ClientboundLightUpdatePacketData data, boolean update, CallbackInfo ci) {
		LightSuppression.markChunk(x, z);
	}

	@Inject(method = "enableChunkLight", at = @At("RETURN"))
	private void selectiveRendering$markLightEnabled(LevelChunk chunk, int x, int z, CallbackInfo ci) {
		LightSuppression.markChunk(x, z);
	}
}
