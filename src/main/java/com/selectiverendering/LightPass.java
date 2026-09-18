package com.selectiverendering;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.LightLayer;

/**
 * Lets light through a block the mod is hiding.
 *
 * <p>Light is stored per position by the lighting engine, and a solid block stores a dark value
 * there because it blocks the light on its way past. Rendering that block see-through does not
 * change any of that, so everything it was shading stays dark: make the grass translucent and the
 * dirt under it is black, which is what a block that is not there would never do. Rebuilding the
 * lighting engine around hidden blocks is the honest fix and it needs a full light update over
 * every position that changed, so instead this answers the one question the renderer asks.</p>
 *
 * <p>Every light lookup the block renderer makes goes through here, and a hidden position answers
 * with the brightest of its neighbours rather than with its own dark value, which is what the same
 * position would have been given had nothing ever been there. Nothing is written back, so turning
 * the mod off costs nothing and there is no light update to wait for.</p>
 *
 * <p>Which of the two renderers asks this is not known here; the hooks that hand this in know
 * which path they belong to.</p>
 */
public final class LightPass {
	/**
	 * Handed to the vanilla light lookup in place of its own, see
	 * {@code BlockModelLighterCacheMixin} and {@code LightDataAccessMixin}.
	 */
	public static final LevelRenderer.BrightnessGetter GETTER = LightPass::packedBrightness;

	private LightPass() {
	}

	public static int packedBrightness(BlockAndLightGetter level, BlockPos pos) {
		int block = level.getBrightness(LightLayer.BLOCK, pos);
		int sky = level.getBrightness(LightLayer.SKY, pos);

		if (!SelectiveRenderingManager.isHidden(level, pos)) {
			return LightCoordsUtil.pack(block, sky);
		}

		// A position that is hidden takes the light of the brightest side it touches, so light
		// reaches the faces behind it. The block's own value is the maximum's starting point: it
		// is right for a hidden block that is lit from itself, such as one that emits light.
		for (Direction direction : Direction.values()) {
			BlockPos neighbour = pos.relative(direction);
			block = Math.max(block, level.getBrightness(LightLayer.BLOCK, neighbour));
			sky = Math.max(sky, level.getBrightness(LightLayer.SKY, neighbour));
		}

		return LightCoordsUtil.pack(block, sky);
	}
}
