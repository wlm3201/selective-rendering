package com.selectiverendering.render.hiding;

import com.selectiverendering.config.region.RenderConfig;
import com.selectiverendering.SelectiveRenderingManager.Mode;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.region.RegionTargets;
import com.selectiverendering.util.BlockPosScratch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** 根据配置快照判断方块是否淡化及其透明度。 */
public final class AlphaGetter {
	private AlphaGetter() {
	}

	public static int getAlpha(BlockState state, BlockPos pos) {
		return getAlpha(state, pos, false);
	}

	/** 返回顶点 alpha 并记录受影响区段；-1 表示正常渲染，0..255 表示透明度。 */
	public static int getAlpha(BlockState state, BlockPos pos, boolean moving) {
		int alpha = alphaNow(state, pos, moving);
		if (alpha < 0) {
			return -1;
		}

		HiddenSections.note(pos);
		return alpha;
	}

	// 排队查询方块实体，避免立即创建实例。
	public static BlockEntity queryBlockEntity(BlockPos pos, Level level) {
		return level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.QUEUED);
	}

	/** 返回顶点 alpha，不记录受影响区段；-1 表示正常渲染。 */
	public static int alphaNow(BlockState state, BlockPos pos, boolean moving) {
		return RegionTargets.blockAlpha(state, pos, moving);
	}

	public static int getFluidAlpha(BlockState state, BlockPos pos) {
		return getAlpha(state, pos);
	}

	/** 当前设置是否要求所有流体使用半透明层。 */
	public static boolean shouldRenderFluidsTranslucent() {
		RenderConfig current = SelectiveRenderingManager.config();
		if (current.mode() == Mode.OFF) {
			return false;
		}

		return current.transparency() < SelectiveRenderingManager.MAX_TRANSPARENCY;
	}

	public static boolean isHidden(BlockState state, BlockPos pos) {
		return getAlpha(state, pos) >= 0;
	}

	public static boolean isHidden(BlockGetter level, BlockPos pos) {
		return getAlpha(level.getBlockState(pos), pos) >= 0;
	}

	/** 使用调用方提供的世界数据判断是否完全隐藏，不记录区段。 */
	public static boolean isHiddenIn(@Nullable BlockGetter level, BlockPos pos) {
		if (level == null) {
			return false;
		}

		return alphaNow(level.getBlockState(pos), pos, false) >= 0;
	}

	/**
	 * 流体的一个面朝向被淡化的邻居时是否还要画：邻居是同种流体且淡化程度和自己相同，
	 * 两侧同样透明，面互相剔除，不需要强制绘制。
	 */
	public static boolean fluidNeighbourForcesFace(@Nullable BlockGetter level, BlockPos neighbour, int selfAlpha, boolean sameFluid) {
		if (level == null) {
			return false;
		}

		int neighbourAlpha = getAlpha(level.getBlockState(neighbour), neighbour);
		if (neighbourAlpha < 0) {
			return false;
		}

		return !(sameFluid && neighbourAlpha == selfAlpha);
	}

	/** 判断方块是否被淡化，不记录区段。 */
	public static boolean isFaded(BlockState state, BlockPos pos) {
		return alphaNow(state, pos, false) >= 0;
	}

	/** 返回实体应使用的透明度，-1 表示正常渲染；主线程调用。 */
	public static int entityAlphaNow(Entity entity) {
		return RegionTargets.entityAlpha(entity.getType(), entity.getId(), entity.getX(), entity.getY(), entity.getZ());
	}

	/** 判断实体是否被淡化（含完全隐藏），语义同 {@link #isFaded}；主线程调用。 */
	public static boolean isFadedEntity(Entity entity) {
		return entityAlphaNow(entity) >= 0;
	}

	public static boolean isHiddenAt(BlockPos pos) {
		return getAlphaAt(pos) >= 0;
	}

	/** 查询客户端世界中的顶点 alpha 并记录区段，仅限客户端线程使用。 */
	public static int getAlphaAt(BlockPos pos) {
		if (SelectiveRenderingManager.getMode() == Mode.OFF) {
			return -1;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1;
		}

		return getAlpha(minecraft.level.getBlockState(pos), pos);
	}

	/** 查询客户端世界中的顶点 alpha，不记录区段。 */
	public static int alphaAt(BlockPos pos) {
		if (SelectiveRenderingManager.getMode() == Mode.OFF) {
			return -1;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1;
		}

		return alphaNow(minecraft.level.getBlockState(pos), pos, false);
	}

	/** 供 EntityCulling 判断指定坐标是否被淡化。 */
	public static boolean isFadedCube(int x, int y, int z) {
		return alphaAt(BlockPosScratch.at(x, y, z)) >= 0;
	}

	public static int getHiddenAlpha() {
		return SelectiveRenderingManager.config().alpha();
	}
}
