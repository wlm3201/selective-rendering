package com.selectiverendering.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * <b>跨版本适配层</b>：把所有"每个 MC 版本签名都不一样"的调用收拢到这里。
 *
 * <p>共享代码（{@code src/client/java}）只依赖本类，
 * 各版本目录（{@code versions/26.x/src}）各提供一份实现，
 * 由 {@code gradle/mod.gradle} 把 {@code ${projectDir}/src/client/java}
 * 一起并进 sourceSet。
 *
 * <h2>这里究竟哪些东西变了</h2>
 * <ul>
 *   <li><b>鼠标按键编号</b>：26.1.2 / 26.2 是 0=左、1=右、2=中；26.3 改成了 1=左、2=中、3=右。</li>
 *   <li><b>区块重建入口</b>：26.2 起 {@code LevelRenderer.allChanged()} 被搬到
 *       {@code Minecraft.levelExtractor}（{@code LevelExtractor}）。</li>
 *   <li><b>GUI 访问</b>：26.2 起 {@code minecraft.screen} 变成 {@code minecraft.gui.screen()}。</li>
 *   <li><b>按键名</b>：{@code InputConstants.Type.KEYSYM} → 26.3 的 {@code KEYBOARD}。</li>
 *   <li><b>{@code BakedQuad.MaterialInfo} 构造参数</b>：字段数量随版本增减。</li>
 * </ul>
 *
 * <p>新增一个 MC 版本支持时，第一步就是照着现有某个 {@code Platform} 复制一份再改。
 */
public final class Platform {
	public static final int MOUSE_LEFT = 0;
	public static final int MOUSE_MIDDLE = 2;
	public static final int MOUSE_RIGHT = 1;

	private Platform() {
	}

	public static boolean ready() {
		return Minecraft.getInstance().levelRenderer != null;
	}

	public static void markSectionWithNeighbors(int x, int y, int z) {
		Minecraft.getInstance().levelExtractor.setSectionDirtyWithNeighbors(x, y, z);
	}

	public static void rebuildAll() {
		Minecraft.getInstance().levelExtractor.allChanged();
	}

	public static Screen screen(Minecraft minecraft) {
		return minecraft.gui.screen();
	}

	public static void setScreen(Minecraft minecraft, Screen screen) {
		minecraft.gui.setScreen(screen);
	}

	public static boolean isKeyDown(Window window, int key) {
		return InputConstants.isKeyDown(window, key);
	}

	public static String keyName(int key) {
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	public static BakedQuad.MaterialInfo translucentMaterial(BakedQuad.MaterialInfo material) {
		return new BakedQuad.MaterialInfo(
			material.sprite(),
			ChunkSectionLayer.TRANSLUCENT,
			material.itemRenderType(),
			material.tintIndex(),
			material.shade(),
			material.lightEmission()
		);
	}
}
