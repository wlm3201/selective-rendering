package com.selectiverendering.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.client.BlockListMessage;
import com.selectiverendering.client.CameraRay;
import com.selectiverendering.client.ModHotkeys;
import com.selectiverendering.client.SelectiveRendering;
import com.selectiverendering.client.SelectiveRenderingManager;
import com.selectiverendering.client.compat.Platform;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 2000)
public class MouseHandlerMixin {
    private static final int ACTION_PRESS = 1;
    private static final float STEEP_PITCH = 60.0F;

    private static final double MAX_TRACE_DISTANCE = 200.0;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void selectiveRendering$onScroll(
            long window, double xOffset, double yOffset, CallbackInfo ci) {
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
            SelectiveRenderingManager.cycleMode(direction);
            ci.cancel();
            return;
        }

        if (Platform.isKeyDown(handle, InputConstants.KEY_LALT)) {
            SelectiveRenderingManager.moveSelectedCorner(lookingDirection(minecraft), direction);
            ci.cancel();
        }
    }

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void selectiveRendering$onButton(
            long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (ci.isCancelled()) {
            return;
        }

        if (action != ACTION_PRESS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (Platform.screen(minecraft) != null
                || minecraft.level == null
                || !SelectiveRendering.isWandHeld()) {
            return;
        }

        Entity camera = minecraft.getCameraEntity();
        if (camera == null) {
            return;
        }

        if (buttonInfo.button() == Platform.MOUSE_MIDDLE) {
            SelectiveRenderingManager.selectCorner(rayStart(minecraft), rayEnd(minecraft));
            ci.cancel();
            return;
        }

        if (ModHotkeys.regionHeld()) {
            if (buttonInfo.button() == Platform.MOUSE_LEFT) {
                SelectiveRenderingManager.toggleRegionAlongRay(
                        rayStart(minecraft), rayEnd(minecraft));
                ci.cancel();
            } else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
                SelectiveRenderingManager.clipRegions();
                ci.cancel();
            }

            return;
        }

        HitResult trace = CameraRay.pick(minecraft, camera, MAX_TRACE_DISTANCE);

        if (!(trace instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
            return;
        }

        if (ModHotkeys.blocksHeld()) {
            if (buttonInfo.button() == Platform.MOUSE_LEFT) {
                toggleBlockId(hit, minecraft);
                ci.cancel();
            } else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
                toggleBlockStates(hit, minecraft);
                ci.cancel();
            }

            return;
        }

        if (buttonInfo.button() == Platform.MOUSE_LEFT) {
            SelectiveRenderingManager.setCorner(0, hit.getBlockPos());
            ci.cancel();
        } else if (buttonInfo.button() == Platform.MOUSE_RIGHT) {
            SelectiveRenderingManager.setCorner(1, hit.getBlockPos());
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

    private static void toggleBlockId(BlockHitResult hit, Minecraft minecraft) {
        toggleRuleAt(hit, minecraft, false);
    }

    private static void toggleBlockStates(BlockHitResult hit, Minecraft minecraft) {
        toggleRuleAt(hit, minecraft, true);
    }

    private static void toggleRuleAt(BlockHitResult hit, Minecraft minecraft, boolean withStates) {
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

        boolean listed = SelectiveRenderingManager.hasRule(source);
        if (listed) {
            SelectiveRenderingManager.removeRule(source);
        } else {
            SelectiveRenderingManager.addRule(source);
        }

        BlockListMessage.show(source, !listed);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String value(BlockState state, Property<?> property) {
        return String.valueOf(state.getValue((Property) property));
    }
}
