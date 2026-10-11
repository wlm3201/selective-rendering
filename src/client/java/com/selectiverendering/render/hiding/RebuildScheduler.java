package com.selectiverendering.render.hiding;

import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.compat.Platform;
import com.selectiverendering.lighting.LightSuppression;

/** 根据渲染设置变化调度区段重建和光照重算。 */
public final class RebuildScheduler {
	private RebuildScheduler() {
	}

	/** 重建已记录的隐藏区段，无法增量处理时全量重建。 */
	public static void rebuildHidden() {
		if (!HiddenSections.mark()) {
			rebuildChunks();
			return;
		}

		LightSuppression.relight();
	}

	/** 根据名单增删和当前模式选择增量或全量重建。 */
	public static void rebuildAfterListChange(boolean added) {
		Mode mode = SelectiveRenderingManager.getMode();
		if (!mode.usesList()) {
			return;
		}

		if (SelectiveRenderingManager.isInvert()) {
			rebuildChunks();
			return;
		}

		if (mode.hidesListed() == added) {
			rebuildChunks();
			return;
		}

		rebuildHidden();
	}

	/** 清除区段记录，重建全部区段并重算光照。 */
	public static void rebuildChunks() {
		HiddenSections.clear();
		Platform.rebuildAll();
		LightSuppression.relight();
	}
}
