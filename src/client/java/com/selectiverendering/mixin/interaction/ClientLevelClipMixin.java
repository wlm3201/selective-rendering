package com.selectiverendering.mixin.interaction;

import com.selectiverendering.interaction.FadedBlockGetter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** 覆写客户端世界的射线拾取，使交互能够穿透淡化方块。 */
@Mixin(ClientLevel.class)
public class ClientLevelClipMixin {
	public BlockHitResult clip(ClipContext context) {
		return new FadedBlockGetter((ClientLevel) (Object) this).clip(context);
	}
}
