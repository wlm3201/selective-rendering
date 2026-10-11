package com.selectiverendering.region;

import com.selectiverendering.compat.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** 从渲染相机出发，沿鼠标指针计算魔杖射线。 */
public final class CameraRay {
	private CameraRay() {
	}

	public static Vec3 start(Minecraft minecraft) {
		return Platform.camera(minecraft).position();
	}

	public static Vec3 end(Minecraft minecraft, double distance) {
		return start(minecraft).add(direction(minecraft).scale(distance));
	}

	public static Vec3 direction(Minecraft minecraft) {
		Camera camera = Platform.camera(minecraft);
		float ndcX = 0.0F;
		float ndcY = 0.0F;

		if (isPointerFree(minecraft)) {
			MouseHandler mouse = minecraft.mouseHandler;
			ndcX = ndc(mouse.xpos(), minecraft.getWindow().getScreenWidth());
			ndcY = -ndc(mouse.ypos(), minecraft.getWindow().getScreenHeight());
		}

		float fov = camera.getFov() > 0.0F ? camera.getFov() : minecraft.options.fov().get().intValue();
		return camera.getNearPlane(fov).getPointOnPlane(ndcX, ndcY).normalize();
	}

	/** 沿魔杖射线拾取方块。 */
	public static BlockHitResult pick(Minecraft minecraft, Entity camera, double distance) {
		Vec3 from = start(minecraft);
		Vec3 to = from.add(direction(minecraft).scale(distance));
		return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
	}

	private static boolean isPointerFree(Minecraft minecraft) {
		return !minecraft.mouseHandler.isMouseGrabbed();
	}

	private static float ndc(double value, double size) {
		return size <= 0.0 ? 0.0F : (float) (value / size * 2.0 - 1.0);
	}
}
