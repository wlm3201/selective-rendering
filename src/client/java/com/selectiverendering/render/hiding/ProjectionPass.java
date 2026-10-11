package com.selectiverendering.render.hiding;

import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 选区投影里的实体、方块实体和粒子：源范围里的这些东西再按位移后的位置提交一遍。
 * 投影出来的一份不受选区自己的隐藏 / 半透明设置影响。
 */
public final class ProjectionPass {
	private static boolean active;
	private static ProjectionAreas.Active particleArea;

	private ProjectionPass() {
	}
	public static boolean active() {
		return active;
	}
	public static ProjectionAreas.Active particleArea() {
		return particleArea;
	}

	public static void beginParticle(ProjectionAreas.Active area) {
		particleArea = area;
	}

	public static void endParticle() {
		particleArea = null;
	}

	public static ProjectionAreas.Active areaAt(double x, double y, double z) {
		for (ProjectionAreas.Active area : ProjectionAreas.current()) {
			if (area.contains(x, y, z)) {
				return area;
			}
		}

		return null;
	}

	public static void submit(ClientLevel level, PoseStack poseStack, LevelRenderState renderState, SubmitNodeCollector collector) {
		List<ProjectionAreas.Active> areas = ProjectionAreas.current();
		if (areas.isEmpty()) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		Entity viewer = minecraft.getCameraEntity();
		CameraRenderState camera = renderState.cameraRenderState;
		Vec3 cameraPos = camera.pos;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
		BlockEntityRenderDispatcher blockEntities = minecraft.getBlockEntityRenderDispatcher();

		active = true;

		try {
			for (ProjectionAreas.Active area : areas) {
				AABB box = area.area().box();

				for (Entity entity : level.getEntities((Entity) null, box, ignored -> true)) {
					if (entity == viewer || !area.contains(entity.getX(), entity.getY(), entity.getZ())) {
						continue;
					}

					EntityRenderState state = entities.extractEntity(entity, partial);
					entities.submit(
						state, camera,
						state.x + area.offsetX() - cameraPos.x,
						state.y + area.offsetY() - cameraPos.y,
						state.z + area.offsetZ() - cameraPos.z,
						poseStack, collector
					);
				}

				int minChunkX = area.area().min().getX() >> 4;
				int maxChunkX = area.area().max().getX() >> 4;
				int minChunkZ = area.area().min().getZ() >> 4;
				int maxChunkZ = area.area().max().getZ() >> 4;

				for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
					for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
						if (!level.hasChunk(chunkX, chunkZ)) {
							continue;
						}

						ChunkAccess access = level.getChunk(chunkX, chunkZ);
						if (!(access instanceof LevelChunk chunk)) {
							continue;
						}

						for (BlockEntity blockEntity : List.copyOf(chunk.getBlockEntities().values())) {
							if (!area.contains(blockEntity.getBlockPos())) {
								continue;
							}

							// 第四个参数要和渲染器的 shouldRenderOffScreen 一致才会提取，两种都试一遍。
							for (int pass = 0; pass < 2; pass++) {
								boolean offScreen = pass == 1;

								//? if >=26.2 {
								BlockEntityRenderState state = blockEntities.tryExtractRenderState(blockEntity, partial, null, offScreen);
								//?} else {
								/*BlockEntityRenderState state = pass == 0 ? blockEntities.tryExtractRenderState(blockEntity, partial, null) : null;
								*///?}

								if (state == null) {
									continue;
								}

								poseStack.pushPose();
								BlockPos blockPos = state.blockPos;
								poseStack.translate(
									blockPos.getX() + area.offsetX() - cameraPos.x,
									blockPos.getY() + area.offsetY() - cameraPos.y,
									blockPos.getZ() + area.offsetZ() - cameraPos.z
								);
								blockEntities.submit(state, poseStack, collector, camera);
								poseStack.popPose();
							}
						}
					}
				}
			}
		}
		finally {
			active = false;
		}
	}
}
