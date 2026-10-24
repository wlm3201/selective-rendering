package com.selectiverendering.interaction;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import com.selectiverendering.config.entrypoint.ModHotkeys;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.region.CameraRay;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class WandActions {
	private static final int ACTION_PRESS = 1;
	private static final float STEEP_PITCH = 60.0F;
	private static final double MAX_TRACE_DISTANCE = 200.0;

	private void onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (ci.isCancelled()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (Platform.screen(minecraft) != null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		if (yOffset == 0) {
			return;
		}

		int direction = yOffset > 0 ? 1 : -1;
		Window handle = minecraft.getWindow();

		if (Platform.isKeyDown(handle, InputConstants.KEY_LCONTROL)) {
			SelectiveRenderingManager.cycleMode(direction);
			ci.cancel();
			return;
		}

		if (Platform.isKeyDown(handle, InputConstants.KEY_LALT) && ProjectionControls.scroll(lookingDirection(minecraft), direction)) {
			ci.cancel();
		}
	}

	private void onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (ci.isCancelled()) {
			return;
		}

		if (action != ACTION_PRESS) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (Platform.screen(minecraft) != null || minecraft.level == null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		Entity camera = minecraft.getCameraEntity();
		if (camera == null) {
			return;
		}

		if (buttonInfo.button() == Platform.MOUSE_MIDDLE) {
			RegionEditor.selectAlongRay(rayStart(minecraft), rayEnd(minecraft));
			ci.cancel();
			return;
		}

		if (ModHotkeys.regionHeld()) {
			if (buttonInfo.button() == Platform.MOUSE_LEFT) {
				RegionEditor.toggleAreaAlongRay(rayStart(minecraft), rayEnd(minecraft));
				ci.cancel();
			} else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
				RegionEditor.clipRegions();
				ci.cancel();
			}
			return;
		}

		if (ModHotkeys.operationHeld()) {
			int button = buttonInfo.button();
			if ((button == Platform.MOUSE_LEFT || button == Platform.MOUSE_RIGHT) && OperationActions.operate(minecraft, camera, button == Platform.MOUSE_RIGHT)) {
				ci.cancel();
			}
			return;
		}

		BlockHitResult hit = CameraRay.pick(minecraft, camera, MAX_TRACE_DISTANCE);
		if (hit.getType() == HitResult.Type.MISS) {
			return;
		}

		if (buttonInfo.button() == Platform.MOUSE_LEFT) {
			RegionEditor.setCorner(0, hit.getBlockPos());
			ci.cancel();
		} else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
			RegionEditor.setCorner(1, hit.getBlockPos());
			ci.cancel();
		}
	}

	private static Vec3 rayStart(Minecraft minecraft) {
		return CameraRay.start(minecraft);
	}

	private static Vec3 rayEnd(Minecraft minecraft) {
		return CameraRay.end(minecraft, MAX_TRACE_DISTANCE);
	}

	private static Direction lookingDirection(Minecraft minecraft) {
		Camera camera = Platform.camera(minecraft);

		float pitch = camera.xRot();
		if (pitch > STEEP_PITCH) {
			return Direction.DOWN;
		}

		if (-pitch > STEEP_PITCH) {
			return Direction.UP;
		}

		return Direction.fromYRot(camera.yRot());
	}
}
