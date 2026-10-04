package com.selectiverendering.mixin;

import com.selectiverendering.FadedBlockGetter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 客户端射线（准星 / 交互）的淡化穿透：给 {@code ClientLevel} 补一个 {@code clip} 覆写。
 *
 * <h2>为什么是"补一个覆写"而不是 {@code @Inject}</h2>
 * <p>{@code clip} 是 {@code BlockGetter} 的 <b>default 方法</b>，
 * {@code ClientLevel} 只是继承、<b>自己没有声明</b>。两种注入写法都实测过，都不行：
 * <ul>
 *   <li>{@code @Mixin(ClientLevel.class)} + {@code @Inject(method="clip")} →
 *       {@code could not find any targets matching 'clip(...)' in ClientLevel}
 *       （Mixin 匹配不到继承来的<b>接口</b> default 方法）；</li>
 *   <li>{@code @Mixin(BlockGetter.class)} + {@code @Inject(method="clip")} →
 *       同样不生效。</li>
 * </ul>
 * 而且这两种失败都是<b>静默</b>的：不崩游戏，只是整个 mixin 类被禁用，
 * 连累同类的其它注入点一起失效。
 *
 * <p>所以改成<b>在 {@code ClientLevel} 上真正声明一个 {@code clip}</b>。
 * 它本来没有这个方法，对 Mixin 而言是"新增"而不是"改写"，不存在匹配问题；
 * 声明之后它就<b>覆写</b>了继承来的 default 实现，虚分派自然落到这里。
 *
 * <h2>为什么打在 {@code clip} 这一层</h2>
 * <p>它是所有客户端射线<b>共同的收口</b>：
 * <ul>
 *   <li>原版准星：{@code Entity#pick} → {@code level().clip(...)}（静态类型 {@code Level}）；</li>
 *   <li>OrbitCam 之类自己算射线的 mod：{@code mc.level.clip(...)}（静态类型 {@code ClientLevel}）；</li>
 *   <li>长矛等带 {@code ATTACK_RANGE} 的道具：{@code ProjectileUtil} →
 *       {@code clipIncludingBorder} → 内部 {@code this.clip(c)}。</li>
 * </ul>
 * 三者的运行期接收者都是 {@code ClientLevel}，都会落到这里。
 * 因此第三方 mod <b>不需要知道要和本 Mod 协作</b>。
 *
 * <h2>开关关闭时</h2>
 * <p>仍然会套一层 {@link FadedBlockGetter}，但开关判定就在外壳内部，
 * 关闭时它原样转发，走的还是同一个 {@code BlockGetter#clip} default 实现，
 * 结果与原版逐字一致。
 *
 * <p>本类单独成文件：一个 mixin 类里只要有东西解析失败，<b>整类会被禁用</b>，
 * 分开可以避免把 {@code ClientLevelMixin}（记录器）一起带走。
 */
@Mixin(ClientLevel.class)
public class ClientLevelClipMixin {
	/**
	 * 覆写 {@code BlockGetter#clip}。
	 *
	 * <p>⚠ 签名必须<b>逐字</b>对上 {@code default BlockHitResult clip(ClipContext)}，
	 * 少一点差别就变成"新增了一个重载"而不是覆写，交接处会静默失效。
	 */
	public BlockHitResult clip(ClipContext context) {
		return new FadedBlockGetter((ClientLevel) (Object) this).clip(context);
	}
}
