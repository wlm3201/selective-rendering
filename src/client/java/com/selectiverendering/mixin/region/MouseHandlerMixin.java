package com.selectiverendering.mixin.region;

import com.selectiverendering.region.Region;
import com.selectiverendering.interaction.ProjectionControls;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import com.selectiverendering.config.entrypoint.ModHotkeys;
import com.selectiverendering.interaction.OperationActions;
import com.selectiverendering.region.CameraRay;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 处理魔杖的选点、选区编辑、规则切换和滚轮操作。 */
@Mixin(value = MouseHandler.class, priority = 2000)
public class MouseHandlerMixin {
	@Unique
    private static final int ACTION_PRESS = 1;
	@Unique
    private static final float STEEP_PITCH = 60.0F;

	@Unique
    private static final double MAX_TRACE_DISTANCE = 200.0;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
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
			Region selected = SelectiveRenderingManager.selectedRegion();
			if (selected != null) {
				ProjectionControls.scroll(selected, lookingDirection(minecraft), direction);
				ci.cancel();
				return;
			}

			SelectiveRenderingManager.cycleMode(direction);
			ci.cancel();
			return;
		}

		if (Platform.isKeyDown(handle, InputConstants.KEY_LALT)) {
			RegionEditor.moveSelectedCorner(lookingDirection(minecraft), direction);
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
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
				Platform.swingMainHand(minecraft);
				ci.cancel();
			}
			else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
				RegionEditor.clipRegions();
				Platform.swingMainHand(minecraft);
				ci.cancel();
			}
			return;
		}

        if (ModHotkeys.operationHeld()) {
        	int button = buttonInfo.button();
        	if ((button == Platform.MOUSE_LEFT || button == Platform.MOUSE_RIGHT) && OperationActions.operate(minecraft, camera, button == Platform.MOUSE_RIGHT)) {
        		Platform.swingMainHand(minecraft);
        		ci.cancel();
        	}
        	return;
        }

        Window handle = minecraft.getWindow();
        boolean shift = Platform.isKeyDown(handle, InputConstants.KEY_LSHIFT) || Platform.isKeyDown(handle, InputConstants.KEY_RSHIFT);
        if (shift && buttonInfo.button() == Platform.MOUSE_LEFT) {
            RegionEditor.clearCorners();
            Platform.swingMainHand(minecraft);
            ci.cancel();
            return;
        }

        BlockHitResult hit = CameraRay.pick(minecraft, camera, MAX_TRACE_DISTANCE);
        if (hit.getType() == HitResult.Type.MISS) {
			return;
		}


		if (buttonInfo.button() == Platform.MOUSE_LEFT) {
			RegionEditor.setCorner(0, hit.getBlockPos());
			Platform.swingMainHand(minecraft);
			ci.cancel();
		}
		else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
			RegionEditor.setCorner(1, hit.getBlockPos());
			Platform.swingMainHand(minecraft);
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
