package com.selectiverendering.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
//? if <26.2 {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/** 封装不同 Minecraft 版本的 API 差异。 */
public final class Platform {
	//? if >=26.3 {
	public static final int MOUSE_LEFT = 1;
	//?} else {
	/*public static final int MOUSE_LEFT = 0;
	*///?}
	public static final int MOUSE_MIDDLE = 2;
	//? if >=26.3 {
	public static final int MOUSE_RIGHT = 3;
	//?} else {
	/*public static final int MOUSE_RIGHT = 1;
	*///?}

	private Platform() {
	}

	/** 返回当前渲染相机，供魔杖射线使用。 */
	public static Camera camera(Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gameRenderer.mainCamera();
		//?} else {
		/*return minecraft.gameRenderer.getMainCamera();
		*///?}
	}

	public static boolean ready() {
		return Minecraft.getInstance().levelRenderer != null;
	}

	public static void markSectionWithNeighbors(int x, int y, int z) {
		//? if >=26.2 {
		Minecraft.getInstance().levelExtractor.setSectionDirtyWithNeighbors(x, y, z);
		//?} else {
		/*LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
		if (renderer != null) {
			renderer.setSectionDirtyWithNeighbors(x, y, z);
		}
		*///?}
	}

	public static void rebuildAll() {
		//? if >=26.2 {
		Minecraft.getInstance().levelExtractor.allChanged();
		//?} else {
		/*LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
		if (renderer != null) {
			renderer.allChanged();
		}
		*///?}
	}

	public static Screen screen(Minecraft minecraft) {
		//? if >=26.2 {
		return minecraft.gui.screen();
		//?} else {
		/*return minecraft.screen;
		*///?}
	}

	public static void setScreen(Minecraft minecraft, Screen screen) {
		//? if >=26.2 {
		minecraft.gui.setScreen(screen);
		//?} else {
		/*minecraft.setScreen(screen);
		*///?}
	}

	public static void swingMainHand(Minecraft minecraft) {
		if (minecraft.player == null) {
			return;
		}

		//? if >=26.3 {
		minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
		//?} else {
		/*minecraft.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		*///?}
	}

	public static boolean isKeyDown(Window window, int key) {
		//? if >=26.3 {
		return InputConstants.isKeyDown(key);
		//?} else {
		/*return InputConstants.isKeyDown(window, key);
		*///?}
	}

	public static String keyName(int key) {
		//? if >=26.3 {
		return InputConstants.Type.KEYBOARD.getOrCreate(key).getDisplayName().getString();
		//?} else {
		/*return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
		*///?}
	}

	public static BakedQuad.MaterialInfo translucentMaterial(BakedQuad.MaterialInfo material) {
		return new BakedQuad.MaterialInfo(
			material.sprite(),
			ChunkSectionLayer.TRANSLUCENT,
			material.itemRenderType(),
			//? if >=26.3 {
			material.itemGlintRenderType(),
			material.itemGlintSpecialRenderType(),
			//?}
			material.tintIndex(),
			//? if >=26.3 {
			material.shadeDirectionOverride(),
			//?} else {
			/*material.shade(),
			*///?}
			material.lightEmission()
		);
	}
}
