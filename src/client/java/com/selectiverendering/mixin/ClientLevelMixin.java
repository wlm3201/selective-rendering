package com.selectiverendering.mixin;

import com.selectiverendering.BlockChangeRecorder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 记录器的数据来源，两个入口：
 *
 * <h2>1. {@code setServerVerifiedBlockState}（服务端确认过的方块变化）</h2>
 * <p>区别于客户端预测的本地 {@code setBlock}：好处是不会把玩家自己乱放的
 * 预测状态录进去，录到的都是真实发生的变化。
 *
 * <h2>2. {@code setBlock}（客户端本地的方块变化，只挑 {@code moving_piston}）</h2>
 * <p>活塞动画是客户端收到<b>方块事件包</b>之后自己跑一遍
 * {@code PistonBaseBlock.moveBlocks} 造出来的：它在服务端用的 flag 是 324
 * （不含 {@code UPDATE_CLIENTS}），<b>服务端从来不会把 {@code moving_piston} 发给客户端</b>，
 * 所以入口 1 永远看不到它。想让 {@code *[moving=true]} 这类规则可靠地录到
 * "正在被推动的方块"，就必须在这里补一刀。
 *
 * <p>只在新状态是 {@code moving_piston} 时才转发，其余情况全部忽略——
 * 这样入口 2 不会把玩家自己的预测操作混进记录里。
 *
 * <p>两个入口都只做转发，真正的过滤/合并逻辑在 {@link BlockChangeRecorder}。
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {
	@Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), require = 1)
	private void selectiveRendering$noteBlockChange(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
		BlockChangeRecorder.note(pos, state);
	}

	@Inject(
		method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
		at = @At("HEAD"),
		require = 1
	)
	private void selectiveRendering$notePistonMove(BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
		if (state.getBlock() == Blocks.MOVING_PISTON) {
			BlockChangeRecorder.noteMoving(pos);
		}
	}
}
