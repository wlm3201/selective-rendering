package com.selectiverendering.mixin;

import com.selectiverendering.Region;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the regions: the ones that were added, and the two corners being placed right now.
 *
 * <p>26.1 rebuilt rendering around pipelines and GPU buffers, and the old way of drawing a box, a
 * pose stack and a vertex consumer, has nowhere to plug in any more. What replaced it is the gizmo
 * system, which is what vanilla now uses for its own boxes including the block outline. Gizmos are
 * collected once per frame: {@link LevelRenderer#collectPerFrameGizmos()} opens this frame's
 * collector, and anything handed to {@link Gizmos} from then on lands in that batch. Injecting at
 * the tail of that method means the collector is already open, so the gizmos join the box the game
 * is about to draw anyway.</p>
 *
 * <p>An added region gets a face as well as an edge, so it reads as a volume rather than as a
 * wireframe. The two corners are told apart the way Litematica tells them apart: one is red and the
 * other is blue, so it is always clear which end of the box is which. The corner the wheel would
 * move is the one with a face on it, and that face is the colour of the corner's own edge: which
 * end it is stays readable while it is picked, rather than everything turning a third colour.</p>
 *
 * <p>Nothing is drawn unless the wand is held, the same as the overlay, so it does not sit in the
 * world after the rod is put away.</p>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
	private static final int SELECTION_STROKE = 0xFFFFFFFF;
	private static final int CORNER_1_STROKE = 0xFFFF3333;
	private static final int CORNER_2_STROKE = 0xFF3355FF;
	private static final int CORNER_1_FILL = 0x33FF3333;
	private static final int CORNER_2_FILL = 0x333355FF;
	private static final int ADDED_STROKE = 0xFF00E5FF;
	private static final int ADDED_FILL = 0x1800E5FF;
	private static final float STROKE_WIDTH = 2.0F;

	@Inject(method = "collectPerFrameGizmos", at = @At("TAIL"))
	private void selectiveRendering$emitRegionGizmo(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
		if (!SelectiveRendering.isWandHeld()) {
			return;
		}

		for (Region added : SelectiveRenderingManager.regions()) {
			Gizmos.cuboid(added.box(), GizmoStyle.strokeAndFill(ADDED_STROKE, STROKE_WIDTH, ADDED_FILL));
		}

		BlockPos first = SelectiveRenderingManager.getCorner(0);
		BlockPos second = SelectiveRenderingManager.getCorner(1);

		// The box between the two corners, drawn when both are placed. The corners are drawn either
		// way, so the ends of the box are still there to pick up and move.
		if (first != null && second != null) {
			AABB box = SelectiveRenderingManager.regionBox();
			if (box != null) {
				Gizmos.cuboid(box, GizmoStyle.stroke(SELECTION_STROKE, STROKE_WIDTH));
			}
		}

		corner(0, first);
		corner(1, second);
	}

	/**
	 * One corner in the colour that says which end it is, filled with the same colour when it is
	 * the one the wheel would move. The filled one is drawn last of all, so it sits over the box
	 * rather than under it.
	 */
	private static void corner(int index, BlockPos pos) {
		if (pos == null) {
			return;
		}

		boolean first = index == 0;
		int stroke = first ? CORNER_1_STROKE : CORNER_2_STROKE;

		if (SelectiveRenderingManager.isCornerSelected(index)) {
			int fill = first ? CORNER_1_FILL : CORNER_2_FILL;
			Gizmos.cuboid(pos, GizmoStyle.strokeAndFill(stroke, STROKE_WIDTH, fill));
			return;
		}

		Gizmos.cuboid(pos, GizmoStyle.stroke(stroke, STROKE_WIDTH));
	}
}
