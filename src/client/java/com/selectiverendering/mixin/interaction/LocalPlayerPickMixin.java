package com.selectiverendering.mixin.interaction;

import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.render.hiding.AlphaGetter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.function.Predicate;

/**
 * 准星拾取实体时，把被淡化的实体从候选里剔掉。
 * 方块侧由 {@code ClientLevelClipMixin} 负责，这里只补实体：被跳过的实体不会返回 EntityHitResult，
 * 于是准星自然回落到后面的方块（那个方块本身也已经忽略了淡化方块）。
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerPickMixin {
	// 原版 26.1.2 / 26.2 / 26.3 的这个方法逐字相同；注入失败要当场报错，别静默失效。
	@ModifyArg(method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"), index = 4, require = 1)
	private static Predicate<Entity> selectiveRendering$skipFadedEntities(Predicate<Entity> original) {
		// 关着的时候直接还回去，一次额外的判定都不做。
		if (!SelectiveRenderingManager.isPassThroughEntitiesActive()) {
			return original;
		}

		return entity -> original.test(entity) && !AlphaGetter.isFadedEntity(entity);
	}
}
