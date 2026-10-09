package com.selectiverendering;

import com.selectiverendering.config.ModConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;

/**
 * Mod 客户端入口（{@code fabric.mod.json} 里注册的 {@code client} entrypoint）。
 *
 * <p>职责只有三件事：
 * <ol>
 *   <li>打印一下运行环境里 Sodium / FRAPI(Indigo) 是否存在——这两个是可选依赖，
 *       它们的兼容 mixin 都写成 {@code require = 0}，缺席时静默跳过；</li>
 *   <li>读磁盘配置 {@link ModConfig#load()} 并把配置灌进运行时状态
 *       {@link SelectiveRenderingManager#load()}；</li>
 *   <li>注册 MaLiLib 配置界面与快捷键 {@link ModConfigScreen#register()}。</li>
 * </ol>
 *
 * <p>本 Mod 移植自 Lucidity 的 selective rendering（作者 mypals/ml），
 * 核心思路一致：保留方块的几何，只是把它划到半透明层并改顶点 alpha；
 * alpha 为 0 时直接不渲染（等价于 Xray）。
 */
public class SelectiveRendering implements ClientModInitializer {
	public static final String MOD_ID = "selective_rendering";

	private static final String SODIUM_ID = "sodium";

	private static final String FABRIC_RENDERER_API_ID = "fabric-renderer-indigo";

	/** 魔杖解析结果缓存。用单个引用存放，避免"新字符串 + 旧物品"的错配。 */
	private static volatile Wand cachedWand;

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		Log.say(
			"[state] sodium {}, fabric renderer api {}",
			loader.isModLoaded(SODIUM_ID) ? "present" : "absent",
			loader.isModLoaded(FABRIC_RENDERER_API_ID) ? "present" : "absent"
		);

		ModConfig.load();
		SelectiveRenderingManager.load();

		ModConfigScreen.register();
		reportClipHook();
	}

	/**
	 * 启动自检：射线穿透那个 mixin 到底应用上没有。
	 *
	 * <h2>为什么需要它</h2>
	 * <p>本项目<b>不产 refmap</b>，Mixin 目标能不能解析在构建期验证不了。
	 * 而它失败的方式是<b>静默的</b>：不崩游戏，只是那一个 mixin 类整个失效
	 * （还会连带同类的其它注入点一起失效）。曾经把 {@code clip} 打在
	 * {@code ClientLevel} 上（它只是继承 {@code BlockGetter} 的 default 方法、
	 * 并未覆写），实际就是这么静悄悄失效的。
	 *
	 * <p>判据是"{@code ClientLevel} 自己<b>声明</b>了 {@code clip(ClipContext)}"——
	 * 原版它只是从 {@code BlockGetter} 继承，没有声明；声明了就说明我们的覆写生效了。
	 * 用 {@code getDeclaredMethod} 而不是 {@code getMethod}，后者会顺着继承链找到
	 * default 方法，永远返回非 null，就查不出问题了。
	 *
	 * <p>{@code ClientLevel.class} 这个字面量会<b>强制加载</b>该类，
	 * 也就是强制 Mixin 在这一刻完成转换 —— 于是成败立刻可见，
	 * 不用等玩家真的进世界才发现。
	 */
	private static void reportClipHook() {
		boolean installed = false;

		try {
			ClientLevel.class.getDeclaredMethod("clip", ClipContext.class);
			installed = true;
		}
		catch (NoSuchMethodException e) {
			installed = false;
		}

		Log.say("[state] pass through clip hook {}", installed ? "installed" : "MISSING (mixin failed to apply)");
	}

	/** 一次魔杖解析的结果，作为不可分割的整体缓存。 */
	private record Wand(String source, Item item) {
	}

	/**
	 * 玩家主手或副手是否拿着"魔杖"。
	 *
	 * <p>手持魔杖是绝大多数交互的开关：HUD 提示、选区线框、左右键选点、
	 * 滚轮切模式都只在手持时生效。默认魔杖是 {@code minecraft:breeze_rod}，
	 * 可以在配置里改成任意物品 id。
	 *
	 * <p>调用很频繁：每帧的 HUD 与线框各一次。
	 * 但真正的开销只有"读两只手的 {@code ItemStack} +
	 * 一次缓存命中的 {@link #resolveWand()}"，没有注册表查找，不需要再优化。
	 */
	public static boolean isWandHeld() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return false;
		}

		return isWand(minecraft.player.getMainHandItem()) || isWand(minecraft.player.getOffhandItem());
	}

	private static boolean isWand(ItemStack stack) {
		Item item = resolveWand();
		return item != null && stack.is(item);
	}

	/**
	 * 把配置里的字符串解析成 {@link Item}，结果按字符串缓存。
	 *
	 * <p>{@link #isWandHeld()} 每帧都会被调用（HUD 提示 + 选区线框各一次，
	 * 每次还要看两只手），不缓存的话等于每帧查好几次物品注册表。
	 *
	 * <p>解析失败（空串、非法 id、注册表里没有）时一律回落到 {@link Items#BREEZE_ROD}，
	 * 保证"魔杖"永远有效，不会因为玩家填错配置就彻底用不了。
	 */
	private static Item resolveWand() {
		String source = SelectiveRenderingManager.getWand();

		Wand cached = cachedWand;
		if (cached != null && cached.source.equals(source)) {
			return cached.item;
		}

		Identifier id = Identifier.tryParse(source);
		Item resolved = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);

		cachedWand = new Wand(source, resolved == null ? Items.BREEZE_ROD : resolved);
		return cachedWand.item;
	}
}
