package com.selectiverendering.client.mixin;

import com.selectiverendering.client.FadedBlockGetter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ClientLevel.class)
public class ClientLevelClipMixin {
    public BlockHitResult clip(ClipContext context) {
        return new FadedBlockGetter((ClientLevel) (Object) this).clip(context);
    }
}
