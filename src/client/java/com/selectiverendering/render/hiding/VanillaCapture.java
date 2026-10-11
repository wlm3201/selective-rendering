package com.selectiverendering.render.hiding;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.selectiverendering.SelectiveRenderingManager;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

public final class VanillaCapture {
	private static final float CAPTURE_INSET = 0.005F;
	// 原版流体逐顶点输出，四个顶点凑成一个四边形：每个顶点 7 个数（位置、uv、颜色位、光照位）。
	private static final int STRIDE = 8;

	private static final class FluidBuffer {
		final float[] data = new float[4 * STRIDE];
		int count;
	}

	// 半透明截流和投影各自一份，互不干扰地凑顶点。
	private static final ThreadLocal<FluidBuffer> TRANSLUCENT_FLUID = ThreadLocal.withInitial(FluidBuffer::new);
	private static final ThreadLocal<FluidBuffer> PROJECTED_FLUID = ThreadLocal.withInitial(FluidBuffer::new);

	private VanillaCapture() {
	}

	public static void block(BlockPos pos, float x, float y, float z, BakedQuad quad, QuadInstance instance, int alpha) {
		TerrainTranslucency.addCaptured(pos, toQuad(x, y, z, quad, instance, alpha, true));
	}

	/** capture 为真时用于半透明截流（边界面向内收、透明度跟随全局），否则原样转换（投影烘焙）。 */
	public static TerrainTranslucency.Quad toQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance, int alpha, boolean capture) {
		return toQuad(x, y, z, quad, instance, alpha, capture, false, -1);
	}

	public static TerrainTranslucency.Quad toQuad(float x, float y, float z, BakedQuad quad, QuadInstance instance, int alpha, boolean capture, boolean flatShade, int tint) {
		boolean translucent = capture || quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT;
		float inset = capture ? CAPTURE_INSET : 0.0F;
		float[] positions = new float[12];
		float[] uvs = new float[8];
		int[] colors = new int[4];
		int[] lights = new int[4];

		Direction direction = quad.direction();
		float nx = direction.getStepX();
		float ny = direction.getStepY();
		float nz = direction.getStepZ();
		int emission = quad.materialInfo().lightEmission();

		for (int i = 0; i < 4; i++) {
			Vector3fc position = quad.position(i);
			positions[i * 3] = x + position.x() - nx * inset;
			positions[i * 3 + 1] = y + position.y() - ny * inset;
			positions[i * 3 + 2] = z + position.z() - nz * inset;

			long uv = quad.packedUV(i);
			uvs[i * 2] = UVPair.unpackU(uv);
			uvs[i * 2 + 1] = UVPair.unpackV(uv);

			int color = instance.getColor(i);

			if (!capture) {
				color = tint == -1 ? 0xFFFFFF : tint;
			}

			colors[i] = color | 0xFF000000;
			lights[i] = instance.getLightCoordsWithEmission(i, emission);
		}

		return new TerrainTranslucency.Quad(positions, uvs, colors, lights, nx, ny, nz, alpha, capture && alpha == SelectiveRenderingManager.config().alpha(), translucent, null, !capture);
	}

	/** 半透明流体截流用的一个顶点；凑够四个就成为一个四边形。 */
	public static void fluidVertex(BlockPos pos, float x, float y, float z, int color, float u, float v, int light, int alpha) {
		TerrainTranslucency.Quad quad = push(TRANSLUCENT_FLUID.get(), x, y, z, color, u, v, light, alpha, true, true);
		if (quad != null) {
			TerrainTranslucency.addCaptured(pos, quad);
		}
	}

	/** 投影用的一个顶点：复制给包含 pos 的每个投影范围。 */
	public static void projectedFluidVertex(BlockPos pos, float x, float y, float z, int color, float u, float v, int light, boolean translucent) {
		TerrainTranslucency.Quad quad = push(PROJECTED_FLUID.get(), x, y, z, color, u, v, light, 255, false, translucent);
		if (quad != null) {
			TerrainTranslucency.addProjected(pos, quad);
		}
	}

	private static TerrainTranslucency.Quad push(FluidBuffer buffer, float x, float y, float z, int color, float u, float v, int light, int alpha, boolean capture, boolean translucent) {
		float[] data = buffer.data;
		int base = buffer.count * STRIDE;

		data[base] = x;
		data[base + 1] = y;
		data[base + 2] = z;
		data[base + 3] = u;
		data[base + 4] = v;
		data[base + 5] = Float.intBitsToFloat(color | 0xFF000000);
		data[base + 6] = Float.intBitsToFloat(light);

		buffer.count++;
		if (buffer.count < 4) {
			return null;
		}

		buffer.count = 0;

		float ux = data[STRIDE] - data[0];
		float uy = data[STRIDE + 1] - data[1];
		float uz = data[STRIDE + 2] - data[2];
		float vx = data[2 * STRIDE] - data[0];
		float vy = data[2 * STRIDE + 1] - data[1];
		float vz = data[2 * STRIDE + 2] - data[2];
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (length > 0.0F) {
			nx /= length;
			ny /= length;
			nz /= length;
		}

		float[] positions = new float[12];
		float[] uvs = new float[8];
		int[] colors = new int[4];
		int[] lights = new int[4];

		for (int i = 0; i < 4; i++) {
			positions[i * 3] = data[i * STRIDE];
			positions[i * 3 + 1] = data[i * STRIDE + 1];
			positions[i * 3 + 2] = data[i * STRIDE + 2];
			uvs[i * 2] = data[i * STRIDE + 3];
			uvs[i * 2 + 1] = data[i * STRIDE + 4];
			colors[i] = Float.floatToRawIntBits(data[i * STRIDE + 5]);
			lights[i] = Float.floatToRawIntBits(data[i * STRIDE + 6]);
		}

		return new TerrainTranslucency.Quad(positions, uvs, colors, lights, nx, ny, nz, alpha, capture && alpha == SelectiveRenderingManager.config().alpha(), translucent, null, false);
	}
}
