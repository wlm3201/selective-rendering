package com.selectiverendering.interaction;

import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.matchingrule.BlockListMessage;
import com.selectiverendering.region.CameraRay;
import com.selectiverendering.region.Region;
import com.selectiverendering.region.RegionEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 操作键 + 左 / 右键：把视线命中的方块或实体加入或移出名单。
 * 选中了选区时写入选区自己的本地名单，否则方块写入全局名单；实体没有全局名单，必须先选中选区。
 */
public final class OperationActions {
	private static final double MAX_TRACE_DISTANCE = 200.0;
	private static final double PICK_MARGIN = 0.1;

	private OperationActions() {
	}

	public static boolean operate(Minecraft minecraft, Entity camera, boolean detailed) {
		Vec3 start = CameraRay.start(minecraft);
		Vec3 end = CameraRay.end(minecraft, MAX_TRACE_DISTANCE);

		BlockHitResult block = CameraRay.pick(minecraft, camera, MAX_TRACE_DISTANCE);
		boolean blockHit = block.getType() != HitResult.Type.MISS;
		double limit = blockHit ? start.distanceToSqr(block.getLocation()) : Double.MAX_VALUE;

		Entity entity = pickEntity(minecraft, camera, start, end, limit);
		if (entity != null) {
			toggleEntity(entity, detailed);
			return true;
		}

		if (blockHit) {
			toggleBlock(block, minecraft, detailed);
			return true;
		}

		return false;
	}

	private static Entity pickEntity(Minecraft minecraft, Entity camera, Vec3 start, Vec3 end, double limit) {
		Entity closest = null;
		double closestDistance = limit;

        assert minecraft.level != null;
        for (Entity entity : minecraft.level.getEntities(camera, new AABB(start, end).inflate(1.0), candidate -> !candidate.isSpectator())) {
			Optional<Vec3> hit = entity.getBoundingBox().inflate(entity.getPickRadius() + PICK_MARGIN).clip(start, end);
			if (hit.isEmpty()) {
				continue;
			}

			double distance = start.distanceToSqr(hit.get());
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = entity;
			}
		}

		return closest;
	}

	private static void toggleBlock(BlockHitResult hit, Minecraft minecraft, boolean withStates) {
        assert minecraft.level != null;
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
		if (state.isAir()) {
			return;
		}

		Block block = state.getBlock();
		String source = BuiltInRegistries.BLOCK.getKey(block).toString();

		if (withStates && !block.getStateDefinition().getProperties().isEmpty()) {
			StringBuilder builder = new StringBuilder(source).append('[');
			boolean first = true;

			for (Property<?> property : block.getStateDefinition().getProperties()) {
				if (!first) {
					builder.append(',');
				}

				first = false;
				builder.append(property.getName()).append('=').append(value(state, property));
			}

			source = builder.append(']').toString();
		}

		Region region = SelectiveRenderingManager.selectedRegion();
		boolean added;

		if (region == null) {
			boolean listed = SelectiveRenderingManager.hasRule(source);

			if (listed) {
				SelectiveRenderingManager.removeRule(source);
			}
			else {
				SelectiveRenderingManager.addRule(source);
			}

			added = !listed;
		}
		else {
			added = RegionEditor.toggleLocalRule(region, source);
		}

		BlockListMessage.show(source + "  (" + scope(region) + ")", added);
	}

	private static void toggleEntity(Entity entity, boolean detailed) {
		Region region = SelectiveRenderingManager.selectedRegion();
		if (region == null) {
			BlockListMessage.note(Component.translatable(SelectiveRendering.MOD_ID + ".message.select_region_first").getString());
			return;
		}

		String source = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();

		if (detailed && entity instanceof ItemEntity item) {
			CompoundTag stack = new CompoundTag();
			stack.putString("id", BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString());

			CompoundTag nbt = new CompoundTag();
			nbt.put("Item", stack);
			source += nbt;
		}

		boolean added = RegionEditor.toggleLocalEntityType(region, source);
		BlockListMessage.show(source + "  (" + scope(region) + ")", added);
	}

	/** 当前操作写入哪份名单的说明*/
	public static String scope(Region region) {
		if (region == null) {
			return Component.translatable(SelectiveRendering.MOD_ID + ".hint.scope_global").getString();
		}

		int index = SelectiveRenderingManager.regions().indexOf(region) + 1;
		return Component.translatable(SelectiveRendering.MOD_ID + ".hint.scope_region", String.valueOf(index)).getString();
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String value(BlockState state, Property<?> property) {
		return String.valueOf(state.getValue((Property) property));
	}
}
