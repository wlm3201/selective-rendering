package com.selectiverendering.mixin.recording;

import com.selectiverendering.recording.BlockChangeRecorder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 将服务端确认的方块变化及本地活塞移动事件交给记录器。 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), require = 1)
	private void selectiveRendering$noteBlockChange(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		BlockChangeRecorder.note(pos, state);
	}

	@Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At("HEAD"), require = 1)
	private void selectiveRendering$notePistonMove(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
		if (state.getBlock() == Blocks.MOVING_PISTON) {
			BlockChangeRecorder.noteMoving(pos);
		}
	}
}
