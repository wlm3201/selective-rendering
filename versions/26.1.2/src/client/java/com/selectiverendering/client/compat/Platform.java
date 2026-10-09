package com.selectiverendering.client.compat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

public final class Platform {
    public static final int MOUSE_LEFT = 0;
    public static final int MOUSE_MIDDLE = 2;
    public static final int MOUSE_RIGHT = 1;

    private Platform() {}

    public static Camera camera(Minecraft minecraft) {
        return minecraft.gameRenderer.getMainCamera();
    }

    public static boolean ready() {
        return Minecraft.getInstance().levelRenderer != null;
    }

    public static void markSectionWithNeighbors(int x, int y, int z) {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null) {
            renderer.setSectionDirtyWithNeighbors(x, y, z);
        }
    }

    public static void rebuildAll() {
        LevelRenderer renderer = Minecraft.getInstance().levelRenderer;
        if (renderer != null) {
            renderer.allChanged();
        }
    }

    public static Screen screen(Minecraft minecraft) {
        return minecraft.screen;
    }

    public static void setScreen(Minecraft minecraft, Screen screen) {
        minecraft.setScreen(screen);
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
                material.lightEmission());
    }
}
