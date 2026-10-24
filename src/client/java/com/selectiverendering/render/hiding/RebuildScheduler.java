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

		// 既然要全量重建，截流下来的四边形和已建好的缓冲都一起作废：
		// 它们属于旧的配置，留着只会继续按旧透明度画，直到各自区段碰巧被重建。
		RetainedTerrain.releaseAll();
		TerrainTranslucency.clear();
		// 这里不清 ProjectionLight：区段重新编译时会自己 ProjectionLight.invalidate() 让光照场重算，
		// 直接清空会让投影在光照场算完之前整段消失几帧（prepare 里 usedField == null 就跳过）。

		Platform.rebuildAll();
		LightSuppression.relight();
	}
}
