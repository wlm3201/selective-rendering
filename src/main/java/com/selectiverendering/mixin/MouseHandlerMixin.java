package com.selectiverendering.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.selectiverendering.BlockListMessage;
import com.selectiverendering.ModKeyBindings;
import com.selectiverendering.SelectiveRendering;
import com.selectiverendering.SelectiveRenderingManager;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Wand controls. With the configured wand held the plain clicks set the two corners, and everything
 * else is a modified click.
 *
 * <p>Holding the region key turns the left button into adding or dropping a region: it drops the
 * one the crosshair is on, and adds the two corners when there is none there. Holding the block key
 * turns the left button into adding or dropping the id of the block that was clicked and the right
 * one into the same block with all of its states written out. Both are bound in the controls screen
 * like any other key, so what they are is up to the player.</p>
 *
 * <p>The wheel is split the way Litematica splits it, and is deliberately not bound to either of
 * those two: control switches the rendering mode and alt moves the corner that is picked, whoever
 * the player has the clicks on.</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	private static final int ACTION_PRESS = 1;
	private static final int BUTTON_LEFT = 0;
	private static final int BUTTON_RIGHT = 1;
	private static final float STEEP_PITCH = 60.0F;

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		// A wheel that went sideways has no direction to move a corner in.
		if (yOffset == 0) {
			return;
		}

		int direction = yOffset > 0 ? 1 : -1;
		Window handle = minecraft.getWindow();

		// Control and alt themselves, and not the two wand keys that happen to start on them. The
		// wheel being on those two is what makes the wand feel like Litematica's, and that is worth
		// more than following a binding: whoever moves the click modifiers somewhere else has not
		// asked for the wheel to go with them, and whoever leaves them alone still expects control
		// and alt to do what they do in every other mod of this kind.
		if (InputConstants.isKeyDown(handle, InputConstants.KEY_LCONTROL)) {
			SelectiveRenderingManager.cycleMode(direction);
			ci.cancel();
			return;
		}

		if (InputConstants.isKeyDown(handle, InputConstants.KEY_LALT)) {
			SelectiveRenderingManager.moveSelectedCorner(lookingDirection(minecraft), direction);
			ci.cancel();
		}
	}

	@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
		if (action != ACTION_PRESS) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.screen != null || minecraft.level == null || !SelectiveRendering.isWandHeld()) {
			return;
		}

		// A miss means the crosshair is on nothing at all, and there is no block to act on. Taking
		// the position anyway would select the origin, which is a long way from where the player is
		// looking, so a click on air is simply left alone.
		if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() == HitResult.Type.MISS) {
			return;
		}

		// Left is the box the two corners make, right is the block being pointed at on its own.
		// The crop the right button will one day be - cutting the added regions up - is not here
		// yet, and a single block is the useful half of that either way.
		if (ModKeyBindings.REGION.isDown()) {
			if (buttonInfo.button() == BUTTON_LEFT) {
				SelectiveRenderingManager.toggleRegionAt(hit.getBlockPos());
				ci.cancel();
			}
			else if (buttonInfo.button() == BUTTON_RIGHT) {
				// The block alone, rather than the box the two corners make: the same region the
				// corners would make were they both on this block, without having to put them
				// there. Whichever region is already acting on the block goes first, the same as
				// the left button does.
				SelectiveRenderingManager.toggleSingleRegionAt(hit.getBlockPos());
				ci.cancel();
			}

			return;
		}

		if (ModKeyBindings.BLOCKS.isDown()) {
			if (buttonInfo.button() == BUTTON_LEFT) {
				toggleBlockId(hit, minecraft);
				ci.cancel();
			}
			else if (buttonInfo.button() == BUTTON_RIGHT) {
				toggleBlockStates(hit, minecraft);
				ci.cancel();
			}

			return;
		}

		// Placing a corner also picks it, so the wheel acts on the corner that was just put down.
		if (buttonInfo.button() == BUTTON_LEFT) {
			SelectiveRenderingManager.setCorner(0, hit.getBlockPos());
			ci.cancel();
		}
		else if (buttonInfo.button() == BUTTON_RIGHT) {
			SelectiveRenderingManager.setCorner(1, hit.getBlockPos());
			ci.cancel();
		}
	}

	/**
	 * The direction the player is facing, horizontal unless they are looking steeply up or down, so
	 * the wheel moves a corner the way they are looking rather than along an axis they have to keep
	 * in their head.
	 */
	private static Direction lookingDirection(Minecraft minecraft) {
		Entity entity = minecraft.getCameraEntity();
		if (entity == null) {
			return Direction.NORTH;
		}

		float pitch = entity.getXRot();
		if (pitch > STEEP_PITCH) {
			return Direction.DOWN;
		}

		if (-pitch > STEEP_PITCH) {
			return Direction.UP;
		}

		return Direction.fromYRot(entity.getYRot());
	}

	/**
	 * Adds or drops the block that was clicked by its id alone, which is every state of it.
	 */
	private static void toggleBlockId(BlockHitResult hit, Minecraft minecraft) {
		toggleRuleAt(hit, minecraft, false);
	}

	/**
	 * Adds or drops the block that was clicked with all of its states written out, so the entry
	 * covers the block as it stands right now and nothing else. A block without states of its own
	 * has nothing to write out, and falls back to the plain id, the same entry the left button
	 * makes.
	 */
	private static void toggleBlockStates(BlockHitResult hit, Minecraft minecraft) {
		toggleRuleAt(hit, minecraft, true);
	}

	/**
	 * Air is left alone: there is nothing to list about a block that is not there.
	 *
	 * @param withStates whether to write the states out, see {@link #toggleBlockId(BlockHitResult,
	 *                   Minecraft)} and {@link #toggleBlockStates(BlockHitResult, Minecraft)}.
	 */
	private static void toggleRuleAt(BlockHitResult hit, Minecraft minecraft, boolean withStates) {
		BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
		if (state.isAir()) {
			return;
		}

		Block block = state.getBlock();
		String source = BuiltInRegistries.BLOCK.getKey(block).toString();

		// A block that has no states would grow an empty pair of brackets, which reads as a
		// different, stricter rule than the id alone when it matches exactly the same blocks.
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
		}
		else {
			SelectiveRenderingManager.addRule(source);
		}

		// What the click just did, where the player is already looking. The list itself is only in
		// the config screen, so without this there is nothing to say it worked, or that it took
		// the rule off rather than putting it on.
		BlockListMessage.show(source, !listed);
	}

	@SuppressWarnings({"rawtypes", "unchecked"})
	private static String value(BlockState state, Property<?> property) {
		return String.valueOf(state.getValue((Property) property));
	}
}
