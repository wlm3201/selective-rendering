package com.selectiverendering.mixin.hiding.compat.sodium;

import com.selectiverendering.util.BlockPosScratch;
import net.minecraft.core.Direction;
import com.selectiverendering.SelectiveRenderingManager;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.joml.Vector3f;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadOrientation;
import com.selectiverendering.render.hiding.TerrainTranslucency;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.selectiverendering.render.hiding.AlphaGetter;
import com.selectiverendering.render.hiding.BlockRenderContextAccess;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 为 Sodium 方块渲染应用透明度及半透明材质，并维护当前方块上下文。 */
@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {
	@Unique
	@Nullable
	private BlockRenderContextAccess selectiveRendering$context;

	@Shadow
	@Final
	private Vector3f posOffset;

	/** 位于方块边界的面向内收一点，避免和相邻方块的面共面闪烁。 */
	@Unique
	private static final float CAPTURE_INSET = 0.005F;

	/** 当前正在渲染的方块的世界坐标，用来判断它在不在投影范围里。 */
	@Unique
	private BlockPos selectiveRendering$pos;

	@WrapMethod(method = "renderModel")
	private void selectiveRendering$renderModel(BlockStateModel model, BlockState state, BlockPos pos, BlockPos origin, Operation<Void> original) {
		BlockRenderContextAccess context = this.selectiveRendering$context();
		int alpha = AlphaGetter.getAlpha(state, pos);

		context.selectiveRendering$setAlpha(alpha);
		context.selectiveRendering$setForcedFaces(0);
		this.selectiveRendering$pos = pos;

		try {
			// 被隐藏的方块如果在投影的源范围里，也要走一遍，好把它的四边形复制给投影（真实网格里仍然不出现）。
			if (alpha != 0 || (TerrainTranslucency.capturing() && SelectiveRenderingManager.isBuildHiddenMesh()) || TerrainTranslucency.projecting(pos)) {
				original.call(model, state, pos, origin);
			}
		} finally {
			context.selectiveRendering$setAlpha(null);
		}
	}

	@Unique
	private BlockRenderContextAccess selectiveRendering$context() {
		BlockRenderContextAccess context = this.selectiveRendering$context;
		if (context == null) {
			context = (BlockRenderContextAccess) (Object) this;
			this.selectiveRendering$context = context;
		}

		return context;
	}

	@Inject(method = "bufferQuad", at = @At("HEAD"), cancellable = true)
	private void selectiveRendering$onBufferQuad(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
		if (this.selectiveRendering$pos != null && TerrainTranslucency.projecting(this.selectiveRendering$pos)) {
			// 只因为投影才保留的面不能进真实的网格。
			Direction face = quad.getCullFace();
			boolean forced = face != null && (this.selectiveRendering$context().selectiveRendering$forcedFaces() & (1 << face.ordinal())) != 0;

			TerrainTranslucency.addProjected(this.selectiveRendering$pos, this.selectiveRendering$buildQuad(quad, brightnesses, 255, false, material.pass.isTranslucent()));
			TerrainTranslucency.addSprite(quad.sprite(SpriteFinderCache.forBlockAtlas()));

			if (forced) {
				ci.cancel();
				return;
			}
		}

		int alpha = this.selectiveRendering$alpha();
		if (alpha < 0 || alpha >= 255) {
			return;
		}

		if (TerrainTranslucency.capturing() && (alpha > 0 || SelectiveRenderingManager.isBuildHiddenMesh())) {
			this.selectiveRendering$capture(quad, brightnesses, alpha);
			ci.cancel();
			return;
		}

		if (alpha == 0) {
			ci.cancel();
			return;
		}

		for (int i = 0; i < 4; i++) {
			int color = quad.getColor(i);
			quad.setColor(i, ((alpha & 0xFF) << 24) | (color & 0x00FFFFFF));
		}
	}

	@ModifyArg(
		method = "processQuad",
		at = @At(
			value = "INVOKE",
			target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer;bufferQuad(Lnet/caffeinemc/mods/sodium/client/render/model/MutableQuadViewImpl;[FLnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;)V"
		),
		index = 2
	)
	private Material selectiveRendering$useTranslucentMaterial(Material material) {
		int alpha = this.selectiveRendering$alpha();
		return alpha > 0 && alpha < 255 ? DefaultMaterials.TRANSLUCENT : material;
	}

	/** 光影包下地形没法半透明：四边形不进区块 mesh，改存起来由实体管线绘制。 */
	@Unique
	private void selectiveRendering$capture(MutableQuadViewImpl quad, float[] brightnesses, int alpha) {
		TerrainTranslucency.addSprite(quad.sprite(SpriteFinderCache.forBlockAtlas()));
		TerrainTranslucency.addCaptured(this.selectiveRendering$pos, this.selectiveRendering$buildQuad(quad, brightnesses, alpha, true, true));
	}

	/** 把 Sodium 的一个四边形转成区段内坐标的四边形；capture 为真时边界面向内收，透明度跟随全局。 */
	@Unique
	private TerrainTranslucency.Quad selectiveRendering$buildQuad(MutableQuadViewImpl quad, float[] brightnesses, int alpha, boolean capture, boolean translucent) {
		float[] positions = new float[12];
		float[] uvs = new float[8];
		int[] colors = new int[4];
		int[] lights = new int[4];

		Vector3f normal = quad.faceNormal();
		float inset = capture && quad.getCullFace() != null ? CAPTURE_INSET : 0.0F;

		for (int i = 0; i < 4; i++) {
			int source = ModelQuadOrientation.NORMAL.getVertexIndex(i);

			positions[i * 3] = quad.getX(source) + this.posOffset.x - normal.x() * inset;
			positions[i * 3 + 1] = quad.getY(source) + this.posOffset.y - normal.y() * inset;
			positions[i * 3 + 2] = quad.getZ(source) + this.posOffset.z - normal.z() * inset;

			uvs[i * 2] = quad.getTexU(source);
			uvs[i * 2 + 1] = quad.getTexV(source);

			int color = quad.baseColor(source);
			// 投影的四边形不烘焙阴影和 AO，之后按投影自己的光照场再加。
			float shade = capture ? Math.min(1.0F, brightnesses[source]) : 1.0F;
			int red = (int) (((color >> 16) & 0xFF) * shade);
			int green = (int) (((color >> 8) & 0xFF) * shade);
			int blue = (int) ((color & 0xFF) * shade);
			colors[i] = (255 << 24) | (red << 16) | (green << 8) | blue;

			lights[i] = quad.getLight(source);
		}

		return new TerrainTranslucency.Quad(positions, uvs, colors, lights, normal.x(), normal.y(), normal.z(), alpha, capture && alpha == SelectiveRenderingManager.config().alpha(), translucent, null, !capture);
	}

	@Unique
	private int selectiveRendering$alpha() {
		Integer alpha = this.selectiveRendering$context().selectiveRendering$alpha();
		return alpha == null ? -1 : alpha;
	}
}
