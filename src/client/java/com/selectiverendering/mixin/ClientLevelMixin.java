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

	/**
	 * 准星/交互射线：被淡化的方块当空气（{@code FadedBlockGetter}）。
	 *
	 * <h2>为什么打在 {@code ClientLevel#clip} 而不是 {@code Entity#pick}</h2>
	 * <p>{@code Entity#pick} 只是"准星射线"的<b>其中一条</b>路径。
	 * 任何自己算射线的 mod（例如 OrbitCam 在 {@code Minecraft#pick} 的 TAIL 里
	 * 用复刻的 {@code raycast()} 整个重写 {@code mc.hitResult}）都会绕开它，
	 * 于是穿透失效。
	 *
	 * <p>而 {@code ClientLevel#clip} 是它们<b>共同的收口</b>：
	 * <ul>
	 *   <li>原版 {@code Entity#pick} → {@code level().clip(...)}；</li>
	 *   <li>OrbitCam → {@code mc.level.clip(...)}（静态类型就是 {@code ClientLevel}）；</li>
	 *   <li>长矛等带 {@code ATTACK_RANGE} 的道具 → {@code ProjectileUtil} →
	 *       {@code clipIncludingBorder} → 内部 {@code this.clip(c)}。</li>
	 * </ul>
	 * 三者在运行期的接收者都是 {@code ClientLevel}，虚分派都会落到本方法。
	 * 因此只要在这里套一层，<b>所有</b>客户端射线都自动穿透，
	 * 第三方 mod 不需要知道自己要和本 Mod 协作。
	 *
	 * <h2>波及面（刻意接受）</h2>
	 * <p>除了上面三条，客户端还会走 {@code Camera#clip}（第三人称把相机拉近，
	 * 用 {@code Block.VISUAL}）。被淡化后相机不再被看不见的方块拉进来，
	 * 这反而修好了一个原有的小毛病。服务端逻辑完全不受影响（本 Mod 是客户端 mod）。
	 *
	 * <p>之所以不按 {@code ClipContext.Block} 枚举收窄，是因为
	 * {@link net.minecraft.world.level.ClipContext} <b>没有暴露</b>
	 * {@code block} 字段的访问器，拿不到它是 OUTLINE / COLLIDER / VISUAL。
	 *
	 * <h2>⚠ 本方法注入的是继承来的 default 方法</h2>
	 * <p>{@code clip} 声明在 {@code BlockGetter} 上，{@code ClientLevel} 只是继承。
	 * 若某个 Mixin 版本解析不到（启动会报 {@code clip ... not found}），
	 * 退路是把 {@code @Mixin(ClientLevel.class)} 换成 {@code @Mixin(BlockGetter.class)}
	 * 并在方法体开头加 {@code if (!(this instanceof ClientLevel)) return;}——
	 * 语义等价，只是多织进所有实现类。
	 */
}
