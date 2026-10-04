package com.selectiverendering;

import com.selectiverendering.compat.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 魔杖射线：从<b>渲染相机</b>出发、穿过<b>鼠标指针</b>。
 *
 * <h2>为什么不是从玩家眼睛出发</h2>
 * <p>原来用的是 {@code cameraEntity.getEyePosition()} + {@code getViewVector()}，
 * 也就是"玩家本体"的视线。开环绕 / 出窍相机后，玩家本体的朝向和画面<b>不是一回事</b>，
 * 于是拿着魔杖点选会选中玩家朝向的方块，而不是画面上看到的那个。
 *
 * <p>改成读 {@code Minecraft#gameRenderer} 的<b>主相机</b>：
 * 出窍相机类 mod（OrbitCam、ShoulderSurfing…）改的就是这个 {@code Camera}
 * （OrbitCam 在 {@code Camera#alignWithEntity} 的 RETURN 上
 * {@code setPosition + setRotation}），所以读它天然就是"画面上的相机"。
 *
 * <h2>这样不需要任何 mod 间约定</h2>
 * <p>不需要 OrbitCam 暴露接口，也不需要编译期依赖：只要一个 mod 把 {@code Camera}
 * 摆正了，我们从它读到的就是正确姿态。对没有相机 mod 的原版环境同样成立——
 * 第一人称下 {@code Camera#position()} 就是眼睛位置，行为与原来一致。
 *
 * <h2>指针：鼠标没被锁时才用光标位置</h2>
 * <p>{@code MouseHandler#isMouseGrabbed()} 为真说明鼠标被锁在屏幕中心
 * （原版游戏内），此时指针 NDC 恒为 (0, 0)，即屏幕正中；
 * 没被锁（OrbitCam 建模模式会 {@code cancel} 掉 {@code grabMouse}）时才按
 * {@code xpos() / ypos()} 换算，于是"准星跟着鼠标走"也能点选。
 *
 * <p>射线方向直接用 {@code Camera#getNearPlane(fov)#getPointOnPlane(x, y)}：
 * 近平面已经把相机旋转算进去了，不需要手动 {@code rotateX / rotateY}，
 * 也不用去偷 {@code Camera#projection} 私有字段。
 *
 * <h2>第三人称安全吗</h2>
 * <p>安全。原版把相机往后挪时会做"拉近"（pull-in），
 * 保证相机到眼睛这一段是空的，所以从相机出发的射线不会先撞到玩家背后的方块。
 */
public final class CameraRay {
	private CameraRay() {
	}

	public static Vec3 start(Minecraft minecraft) {
		return Platform.camera(minecraft).position();
	}

	public static Vec3 end(Minecraft minecraft, double distance) {
		return start(minecraft).add(direction(minecraft).scale(distance));
	}

	/** 世界空间的射线方向（已归一化）。 */
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

	/**
	 * 沿本射线做一次方块拾取。
	 *
	 * <p>注意没有套 {@code FadedBlockGetter}：手持魔杖时穿透是<b>强制关闭</b>的
	 * （见 {@code SelectiveRenderingManager#isPassThroughActive}），
	 * 因为玩家想选的往往正是那个被淡化的方块本身。
	 * 这里走 {@code level.clip} 会碰到我们给 {@code ClientLevel} 补的 {@code clip} 覆写，
	 * 但那个外壳自己会查开关，手持魔杖时恒为"不淡化"，结果与原版一致。
	 */
	public static BlockHitResult pick(Minecraft minecraft, Entity camera, double distance) {
		Vec3 from = start(minecraft);
		Vec3 to = from.add(direction(minecraft).scale(distance));
		return minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, camera));
	}

	private static boolean isPointerFree(Minecraft minecraft) {
		return !minecraft.mouseHandler.isMouseGrabbed();
	}

	/** 屏幕坐标 → NDC（[-1, 1]）。GLFW 的 y 向下，所以纵向要取反。 */
	private static float ndc(double value, double size) {
		return size <= 0.0 ? 0.0F : (float) (value / size * 2.0 - 1.0);
	}
}
