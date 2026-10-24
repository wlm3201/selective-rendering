package com.selectiverendering.render.hiding;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.vertex.VertexFormat;
//?} else if >=26.2 {
/*import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.vertex.VertexFormat;
*///?} else {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat.IndexType;
import com.mojang.blaze3d.vertex.VertexFormat.Mode;
import com.selectiverendering.mixin.hiding.blockentity.RenderTypeAccessor;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import java.util.Map.Entry;
import java.util.OptionalDouble;
import java.util.OptionalInt;
*///?}
	//? if >=26.3 {
import net.minecraft.client.renderer.StagedVertexBuffer;
//?}
//? if >=26.2 {
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
//?}
import com.selectiverendering.SelectiveRenderingManager;
import com.selectiverendering.util.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class RetainedTerrain {
	// 开着光影时顶点只能在渲染线程同步构建，预算太小会让大片区段"迟迟不渲染"。
	private static final int REBUILD_QUAD_BUDGET = 16384;
	// 小的变化（比如活塞推动一个方块）在本帧内同步完成：重算光照、建缓冲、排序，不让它经过后台线程多帧才出现。
	private static final int FAST_QUADS = 3000;
	private static final int SYNC_SORT_BUDGET = 16384;
	private static final int VERTEX_STRIDE = 48;
	private static final float DEPTH_PUSH = 1.0005F;
	// 相机移动多远（平方）后需要重排。
	private static final float SORT_MOVE_SQUARED = 0.25F;

	// 每帧最多上传的新四边形数、最多落实的重排结果数。世界级淡化时段很多，放宽一些避免长时间缺画面。
	private static final int UPLOAD_QUAD_BUDGET = 131072;
	private static final int SORT_UPLOAD_BUDGET = 256;

	// 不依赖渲染线程状态的构建和排序都在这里并行做，渲染线程只负责上传。
	private static final ExecutorService POOL = Executors.newFixedThreadPool(Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 2, 8), runnable -> {
		Thread thread = new Thread(runnable, "selective-rendering-terrain");
		thread.setDaemon(true);
		return thread;
	});

	private static final List<GpuBuffer> GRAVEYARD = new ArrayList<>();
	private static final Map<Object, Section> SECTIONS = new HashMap<>();

	// 按投影自己的光照重算过的四边形；源四边形或光照场变了才重算。
	private static final class Lit {
		// quads 是按 source 和 fieldVersion 算出来的结果，期间的新结果在 job 里算，算完才替换，所以不会有空窗。
		List<TerrainTranslucency.Quad> source;
		int fieldVersion = -1;
		List<TerrainTranslucency.Quad> quads;
		Future<List<TerrainTranslucency.Quad>> job;
		List<TerrainTranslucency.Quad> jobSource;
		int jobVersion;
	}

	private static final Map<TerrainTranslucency.ProjectionKey, Lit> LIT = new HashMap<>();
	// 后台线程在区块编译完成时就把顶点数据建好放进来，渲染线程只负责上传。
	private static final Map<Object, Pending> PENDING = new ConcurrentHashMap<>();

	private static Frame current;
	private static SubmitNodeCollector currentStorage; // 当前主画面的提交目标，阴影图等其它提交靠它区分
	private static ClientLevel lastLevel;
	private static boolean lastShaders;

	// 一个区段里同一透明度的四边形：每组一块顶点缓冲，透明度在绘制时提供。
	private static final class BufferPice {
		final GpuBuffer buffer;
		final int indexCount;
		final int alpha;
		final boolean global;
		final boolean translucent;
		final java.util.UUID region;
		final float minX, minY, minZ, maxX, maxY, maxZ;
		// 每个四边形的中心（相对区段原点），用来按到相机的距离排序。
		final float[] centroids;
		// 按距离从远到近重排后的索引缓冲；还没排过时为 null，改用顺序索引
		GpuBuffer indexBuffer;
		IndexType indexType;
		float sortX, sortY, sortZ;
		// 正在后台做的排序，完成后在渲染线程上传
		Future<ByteBuffer> sortJob;
		float jobX, jobY, jobZ;
		boolean retired;

		BufferPice(GpuBuffer buffer, int indexCount, int alpha, boolean global, boolean translucent, java.util.UUID region, float[] bounds, float[] centroids) {
			this.translucent = translucent;
			this.region = region;
			this.buffer = buffer;
			this.indexCount = indexCount;
			this.alpha = alpha;
			this.global = global;
			this.centroids = centroids;
			this.minX = bounds[0];
			this.minY = bounds[1];
			this.minZ = bounds[2];
			this.maxX = bounds[3];
			this.maxY = bounds[4];
			this.maxZ = bounds[5];
		}
	}

	// 同一个区段里一起画的四边形：同一个选区的覆盖、或者同一个固定透明度、或者全局透明度，并且同属不透明 / 半透明层。
	private record GroupKey(java.util.UUID region, boolean global, int alpha, boolean translucent) {
		static GroupKey of(TerrainTranslucency.Quad quad) {
			boolean fixed = quad.region() == null && !quad.global();
			return new GroupKey(quad.region(), quad.region() == null && quad.global(), fixed ? quad.alpha() : 0, quad.translucent());
		}
	}

	// 每帧刷新：各选区当前的透明度覆盖值。
	private static final Map<java.util.UUID, Integer> OVERRIDES = new HashMap<>();

	private static void refreshOverrides() {
		OVERRIDES.clear();

		for (com.selectiverendering.region.Region region : SelectiveRenderingManager.config().regions()) {
			OVERRIDES.put(region.id(), region.options().overrideAlpha());
		}
	}

	// 一块缓冲当前应该用的透明度：选区的覆盖在绘制时取最新值，所以改透明度覆盖不用重建缓冲。
	private static int alphaOf(BufferPice piece) {
		int global = SelectiveRenderingManager.config().alpha();

		if (piece.region != null) {
			Integer override = OVERRIDES.get(piece.region);
			return override != null && override >= 0 ? override : global;
		}

		return piece.global ? global : piece.alpha;
	}

	private static final class Section {
		final List<TerrainTranslucency.Quad> source;
		final List<BufferPice> pieces;
		// 新建的区段里半透明的块还没排好序之前，继续画上一份，免得顺序不对的半透明把后面的遮住（露出天空）。
		Section prior;

		Section(List<TerrainTranslucency.Quad> source, List<BufferPice> pieces) {
			this.source = source;
			this.pieces = pieces;
		}

		List<TerrainTranslucency.Quad> source() {
			return source;
		}

		List<BufferPice> pieces() {
			return pieces;
		}

		// 半透明的块都至少排过一次序了。
		boolean ready() {
			for (BufferPice piece : pieces) {
				if (piece.translucent && piece.indexCount > 0 && piece.indexBuffer == null) {
					return false;
				}
			}

			return true;
		}
	}

	private static void replace(Object key, Section old, Section next) {
		if (old != null) {
			if (old.ready()) {
				next.prior = old;
			} else {
				next.prior = old.prior;
				old.prior = null;
				retire(old);
			}
		}

		SECTIONS.put(key, next);
	}

	// CPU 侧已经建好的一组顶点数据，等渲染线程上传。
	private record PieceData(ByteBuffer vertices, int indexCount, int alpha, boolean global, boolean translucent, java.util.UUID region, float[] bounds, float[] centroids) {
	}

	private record Pending(List<TerrainTranslucency.Quad> source, Future<List<PieceData>> future) {
	}

	// 一个要绘制的来源：原版区段截流下来的四边形，或者选区的投影。origin 是世界坐标的最小角，size 是方块数。
	private record Source(Object key, List<TerrainTranslucency.Quad> quads, Set<TextureAtlasSprite> sprites,
		double minX, double minY, double minZ, double sizeX, double sizeY, double sizeZ) {
	}

	private record Placed(BufferPice bufferPice, float x, float y, float z) {
	}

	private record Frame(List<Placed> placed, Matrix4f view, boolean shaders) {
	}

	/**
	 * 改透明度是否能靠"绘制时取最新值"生效，不用重建区块。
	 * 只有走常驻管线（截流）时才成立；烘焙进区块网格的 alpha 是编译期写死的，改数值必须重建。
	 */
	public static boolean alphaFollowsDraw() {
		return TerrainTranslucency.captureWanted();
	}

	// 不透明和镂空的方块：不走半透明混合，光影里才能像普通方块一样和天空、地形正确合成。
	private static RenderType solidType(boolean shaders) {
		//? if >=26.3 {
		return shaders ? RenderTypes.entityCutoutCull(TextureAtlas.LOCATION_BLOCKS) : RenderTypes.cutoutMovingBlock();
		//?} else {
		/*return shaders ? RenderTypes.entityCutout(TextureAtlas.LOCATION_BLOCKS) : RenderTypes.cutoutMovingBlock();
		*///?}
	}

	// 开着光影时用半透明实体管线，没开光影时用原版区块的半透明管线
	private static RenderType renderType(boolean shaders) {
		//? if >=26.3 {
		return shaders ? RenderTypes.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS) : RenderTypes.translucentMovingBlock();
		//?} else {
		/*return shaders ? RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS) : RenderTypes.translucentMovingBlock();
		*///?}
	}

	// 在区块编译线程上调用：不开光影时顶点格式已知，直接把这个区段的顶点数据建好，不用等渲染线程来触发。
	public static void prebuild(Object section, List<TerrainTranslucency.Quad> quads) {
		if (ShaderPackState.active()) {
			return;
		}

		try {
			VertexFormat format = renderType(false).format();
			PENDING.put(section, new Pending(quads, CompletableFuture.completedFuture(buildData(quads, format))));
		} catch (Exception exception) {
			Log.error("[terrain] prebuild failed", exception);
		}
	}

	// 必须在实体提交时建造缓冲：开着光影时，只有此时构造的 BufferBuilder 才会按 Iris 的扩展顶点格式写数据。
	public static void prepare(ClientLevel level, CameraRenderState camera, SubmitNodeCollector collector) {
		current = null;
		currentStorage = collector;
		refreshOverrides();

		GRAVEYARD.forEach(GpuBuffer::close);
		GRAVEYARD.clear();

		// 光影开关会同时切换走哪条管线（见 TerrainTranslucency.captureWanted）：
		// 开光影要把淡化地形截流到常驻管线，关掉则烘焙进区块网格。已编好的区段还是旧做法，
		// 必须全部重新编译一次，否则"网格里的淡化方块"和"常驻缓冲里的同一批方块"会同时出现。
		boolean shaders = ShaderPackState.active();
		if (shaders != lastShaders) {
			lastShaders = shaders;
			RebuildScheduler.rebuildChunks();
			return;
		}

		if (level != lastLevel) {
			releaseAll();
			TerrainTranslucency.clear();
			ProjectionLight.clear();
			LIT.clear();
			lastLevel = level;
		}

		// 关掉之后没有方块需要淡化：截流下来的旧缓冲必须立刻丢掉。
		// 否则它们会一直按旧透明度参与遍历、排序和绘制，表现为"关掉也回不来，只能重进存档"。
		if (SelectiveRenderingManager.getMode() == SelectiveRenderingManager.Mode.OFF) {
			if (!SECTIONS.isEmpty() || !PENDING.isEmpty() || !LIT.isEmpty()) {
				releaseAll();
				TerrainTranslucency.clear();
				ProjectionLight.clear();
				LIT.clear();
			}

			return;
		}

		Map<Long, List<TerrainTranslucency.Quad>> sections = TerrainTranslucency.sections();
		Map<String, ProjectionAreas.Active> activeAreas = new HashMap<>();
		for (ProjectionAreas.Active active : ProjectionAreas.current()) {
			activeAreas.put(active.key(), active);
		}

		Map<TerrainTranslucency.ProjectionKey, List<TerrainTranslucency.Quad>> projected = TerrainTranslucency.projected();
		projected.keySet().removeIf(key -> {
			if (activeAreas.containsKey(key.area())) {
				return false;
			}

			TerrainTranslucency.removeProjected(key);
			return true;
		});

		Set<Object> live = new HashSet<>(sections.keySet());
		live.addAll(projected.keySet());

		SECTIONS.entrySet().removeIf(entry -> {
			if (live.contains(entry.getKey())) {
				return false;
			}

			retire(entry.getValue());
			return true;
		});
		PENDING.entrySet().removeIf(entry -> {
			if (live.contains(entry.getKey())) {
				return false;
			}

			entry.getValue().future().cancel(true);
			return true;
		});

		if (live.isEmpty()) {
			return;
		}

		List<Source> sources = new ArrayList<>();

		for (Map.Entry<Long, List<TerrainTranslucency.Quad>> entry : sections.entrySet()) {
			SectionPos section = SectionPos.of(entry.getKey());

			if (!level.hasChunk(section.x(), section.z())) {
				TerrainTranslucency.remove(entry.getKey());
				continue;
			}

			sources.add(new Source(entry.getKey(), entry.getValue(), TerrainTranslucency.sprites(entry.getKey()),
				section.minBlockX(), section.minBlockY(), section.minBlockZ(), 16, 16, 16));
		}

		for (Map.Entry<TerrainTranslucency.ProjectionKey, List<TerrainTranslucency.Quad>> entry : projected.entrySet()) {
			ProjectionAreas.Active area = activeAreas.get(entry.getKey().area());
			if (area == null) {
				continue;
			}

			// 投影的四边形仍是区段内坐标，投影后的位置就是区段原点加上位移；光照按投影自己的光照场重算。
			ProjectionLight.Field usedField = ProjectionLight.field(level, area);
			if (usedField == null) {
				continue;
			}

			SectionPos section = SectionPos.of(entry.getKey().section());
			Lit lit = LIT.computeIfAbsent(entry.getKey(), ignored -> new Lit());

			if (lit.job != null && lit.job.isDone()) {
				try {
					lit.quads = lit.job.get();
					lit.source = lit.jobSource;
					lit.fieldVersion = lit.jobVersion;
				} catch (Exception exception) {
					Log.error("[projection] relight failed", exception);
				}

				lit.job = null;
			}

			if (lit.job == null && lit.source != entry.getValue() && entry.getValue().size() <= FAST_QUADS) {
				lit.quads = ProjectionLight.relight(entry.getValue(), section.minBlockX(), section.minBlockY(), section.minBlockZ(), area.area(), usedField);
				lit.source = entry.getValue();
				lit.fieldVersion = usedField.version;
			}

			if (lit.job == null && (lit.quads == null || lit.source != entry.getValue() || lit.fieldVersion != usedField.version)) {
				List<TerrainTranslucency.Quad> source = entry.getValue();
				List<TerrainTranslucency.Quad> previous = lit.quads;
				boolean sameSource = lit.source == source;

				lit.jobSource = source;
				lit.jobVersion = usedField.version;
				lit.job = POOL.submit(() -> {
					List<TerrainTranslucency.Quad> relit = ProjectionLight.relight(source, section.minBlockX(), section.minBlockY(), section.minBlockZ(), area.area(), usedField);

					// 只是光照场变了、这个区段的光照其实没变：沿用原来的列表，缓冲就不用重建。
					return sameSource && previous != null && ProjectionLight.sameLights(previous, relit) ? previous : relit;
				});
			}

			if (lit.quads == null) {
				continue;
			}

			sources.add(new Source(entry.getKey(), lit.quads, TerrainTranslucency.projectedSprites(entry.getKey()),
				section.minBlockX() + area.offsetX(), section.minBlockY() + area.offsetY(), section.minBlockZ() + area.offsetZ(), 16, 16, 16));
		}

		LIT.keySet().retainAll(projected.keySet());

		List<Placed> placedList = new ArrayList<>();
		Vec3 position = camera.pos;
		Frustum frustum = new Frustum(camera.viewRotationMatrix, camera.projectionMatrix);
		frustum.prepare(position.x, position.y, position.z);
		double range = (Minecraft.getInstance().options.renderDistance().get() + 1) * 16.0;
		int budget = REBUILD_QUAD_BUDGET;
		int uploadBudget = UPLOAD_QUAD_BUDGET;
		int sortUploads = SORT_UPLOAD_BUDGET;
		int syncSortBudget = SYNC_SORT_BUDGET;
		RenderType type = renderType(shaders);

		for (Source source : sources) {
			double minX = source.minX();
			double minY = source.minY();
			double minZ = source.minZ();

			double dx = minX + source.sizeX() / 2.0 - position.x;
			double dy = minY + source.sizeY() / 2.0 - position.y;
			double dz = minZ + source.sizeZ() / 2.0 - position.z;
			double radius = Math.sqrt(source.sizeX() * source.sizeX() + source.sizeY() * source.sizeY() + source.sizeZ() * source.sizeZ()) / 2.0;
			double limit = range + radius;
			if (dx * dx + dy * dy + dz * dz > limit * limit) {
				continue;
			}

			// 不开光影时视锥外的区段也先上传，转头时才不会因为现建而闪烁。
			boolean visible = frustum.isVisible(new AABB(minX, minY, minZ, minX + source.sizeX(), minY + source.sizeY(), minZ + source.sizeZ()));
			if (!visible && shaders) {
				continue;
			}

			List<TerrainTranslucency.Quad> quads = source.quads();
			Section built = SECTIONS.get(source.key());

			if (built == null || built.source() != quads) {
				if (shaders || (quads.size() <= FAST_QUADS && budget > 0)) {
					// 开着光影时 Iris 的扩展顶点格式只有此时在渲染线程才能写，同步构建；
					// 不开光影时，小的变化也同步构建。
					if (budget > 0) {
						Section rebuilt = build(quads, type);
						replace(source.key(), built, rebuilt);
						built = rebuilt;
						budget -= quads.size();
					}
				} else {
					Pending pending = PENDING.get(source.key());
					if (pending == null || pending.source() != quads) {
						if (pending != null) {
							pending.future().cancel(true);
						}

						VertexFormat format = type.format();
						pending = new Pending(quads, POOL.submit(() -> buildData(quads, format)));
						PENDING.put(source.key(), pending);
					}

					if (pending.future().isDone() && uploadBudget > 0) {
						PENDING.remove(source.key());

						try {
							Section uploaded = upload(quads, pending.future().get());
							replace(source.key(), built, uploaded);
							built = uploaded;
							uploadBudget -= quads.size();
						} catch (Exception exception) {
							Log.error("[terrain] build failed", exception);
						}
					}
				}

				if (built == null) {
					continue;
				}
			}

			if (!visible) {
				continue;
			}

			boolean marked = false;

			Section drawn = built;
			if (built.prior != null) {
				if (built.ready()) {
					retire(built.prior);
					built.prior = null;
				} else {
					drawn = built.prior;
				}
			}

			float localX = (float) (position.x - minX);
			float localY = (float) (position.y - minY);
			float localZ = (float) (position.z - minZ);

			// 新建的区段和正在画的区段都要排序（排好序之前新的不画）。
			for (Section section : drawn == built ? List.of(built) : List.of(built, drawn)) {
				for (BufferPice piece : section.pieces()) {
					if (piece.indexCount <= 0) {
						continue;
					}

					if (piece.sortJob != null && piece.sortJob.isDone() && sortUploads > 0) {
						sortUploads--;
						uploadSort(piece);
					}

					float moveX = localX - piece.sortX;
					float moveY = localY - piece.sortY;
					float moveZ = localZ - piece.sortZ;

					if (piece.sortJob == null && piece.indexBuffer == null && piece.indexCount / 6 <= FAST_QUADS && syncSortBudget > 0) {
						syncSortBudget -= piece.indexCount / 6;
						sortNow(piece, localX, localY, localZ);
					} else if (piece.sortJob == null && (piece.indexBuffer == null || moveX * moveX + moveY * moveY + moveZ * moveZ > SORT_MOVE_SQUARED)) {
						startSort(piece, localX, localY, localZ);
					}
				}
			}

			for (BufferPice piece : drawn.pieces()) {
				if (piece.indexCount <= 0 || alphaOf(piece) <= 0 || (piece.translucent && piece.indexBuffer == null)
					|| !frustum.isVisible(new AABB(minX + piece.minX, minY + piece.minY, minZ + piece.minZ, minX + piece.maxX, minY + piece.maxY, minZ + piece.maxZ))) {
					continue;
				}

				if (!marked) {
					marked = true;
					for (TextureAtlasSprite sprite : source.sprites()) {
						SodiumSprites.markActive(sprite);
					}
				}

				placedList.add(new Placed(piece, (float) (minX - position.x), (float) (minY - position.y), (float) (minZ - position.z)));
			}
		}

		// 区段之间也要从远到近画。
		placedList.sort((a, b) -> Float.compare(distanceSquared(b), distanceSquared(a)));

		//? if >=26.2 {
		current = new Frame(placedList, RenderSystem.getModelViewMatrixCopy(), shaders);
		//?} else {
		/*current = new Frame(placedList, new Matrix4f(), shaders);
		*///?}
	}

	// 不透明的投影方块在实体的“实心”阶段画，光影包才会把它们当作普通的不透明几何体（深度、天空合成都正确）；
	// 半透明的在半透明阶段画。
	//? if >=26.3 {
	public static void drawSolid(RenderPass pass, Object storage) {
		drawFrame(pass, storage, false);
	}

	public static void drawTranslucent(RenderPass pass, Object storage) {
		drawFrame(pass, storage, true);
	}
	//?} else {
	/*public static void drawSolid(Object storage) {
		drawFrame(null, storage, false);
	}

	public static void drawTranslucent(Object storage) {
		drawFrame(null, storage, true);
	}
	*///?}

	private static BufferBuilder newBuilder(ByteBufferBuilder bytes, VertexFormat format) {
		//? if >=26.2 {
		return new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
		//?} else {
		/*return new BufferBuilder(bytes, Mode.QUADS, format);
		*///?}
	}

	private static void drawFrame(Object pass, Object storage, boolean translucentPhase) {
		Frame frame = current;
		if (frame == null || storage != currentStorage) {
			return;
		}

		// 实心阶段先于半透明阶段，等半透明阶段画完这一帧的数据才作废。
		if (translucentPhase) {
			current = null;
		}

		List<Placed> subset = new ArrayList<>();
		for (Placed placed : frame.placed()) {
			if (placed.bufferPice().translucent == translucentPhase) {
				subset.add(placed);
			}
		}

		if (subset.isEmpty()) {
			return;
		}

		//? if >=26.2 {
		// 透明度由 ColorModulator 提供，顶点 alpha 恒为 255。
		PreparedRenderType translucentBase = renderType(frame.shaders()).prepare();
		PreparedRenderType solidBase = solidType(frame.shaders()).prepare();
		Matrix4f texture = new Matrix4f();
		int globalAlpha = SelectiveRenderingManager.config().alpha();

		for (Placed placed : subset) {
			BufferPice piece = placed.bufferPice();
			float alpha = alphaOf(piece) / 255.0F;

			Matrix4f modelView = new Matrix4f(frame.view()).scale(DEPTH_PUSH).translate(placed.x(), placed.y(), placed.z());
			GpuBufferSlice transform = RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), texture);

			drawPiece(withTransform(piece.translucent ? translucentBase : solidBase, transform), pass, piece);
		}
		//?} else {
		/*drawImmediate(frame, subset);
		*///?}
	}

	//? if >=26.2 {
	private static PreparedRenderType withTransform(PreparedRenderType base, GpuBufferSlice transform) {
		//? if >=26.3 {
		return new PreparedRenderType(base.name(), base.pipeline(), base.oitPipelineSet(), transform, base.scissorState(), base.textures());
		//?} else {
		/*return new PreparedRenderType(base.pipeline(), base.outputTarget(), transform, base.scissorState(), base.textures());
		*///?}
	}

	private static void drawPiece(PreparedRenderType prepared, Object pass, BufferPice piece) {
		//? if >=26.3 {
		RenderPass renderPass = (RenderPass) pass;
		if (piece.indexBuffer != null) {
			prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(piece.buffer, piece.indexBuffer, piece.indexType, 0, 0, piece.indexCount, PrimitiveTopology.QUADS), renderPass);
			return;
		}

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		indices.getBuffer(piece.indexCount);
		prepared.drawFromBuffer(new StagedVertexBuffer.ExecuteInfo(piece.buffer, null, indices.type(), 0, 0, piece.indexCount, PrimitiveTopology.QUADS), renderPass);
		//?} else {
		/*if (piece.indexBuffer != null) {
			prepared.drawFromBuffer(piece.buffer, piece.indexBuffer, piece.indexType, 0, 0, piece.indexCount);
			return;
		}

		RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
		prepared.drawFromBuffer(piece.buffer, indices.getBuffer(piece.indexCount), indices.type(), 0, 0, piece.indexCount);
		*///?}
	}
	//?} else {
	/*// 26.1.2 没有 PreparedRenderType：按 RenderType.draw 的做法自己开一个渲染通道，一次画完所有区段。
	private static void drawImmediate(Frame frame, List<Placed> subset) {
		RenderType translucentType = renderType(frame.shaders());
		RenderType solidType = solidType(frame.shaders());
		RenderType type = translucentType;

		RenderTarget target = type.outputTarget().getRenderTarget();
		GpuTextureView color = RenderSystem.outputColorTextureOverride != null ? RenderSystem.outputColorTextureOverride : target.getColorTextureView();
		GpuTextureView depth = null;
		if (target.useDepth) {
			depth = RenderSystem.outputDepthTextureOverride != null ? RenderSystem.outputDepthTextureOverride : target.getDepthTextureView();
		}

		Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
		Matrix4f texture = new Matrix4f();
		int globalAlpha = SelectiveRenderingManager.config().alpha();

		// 写 DynamicTransforms 要映射缓冲，必须在打开渲染通道之前全部写好。
		List<GpuBufferSlice> transforms = new ArrayList<>();
		for (Placed placed : subset) {
			BufferPice piece = placed.bufferPice();
			float alpha = alphaOf(piece) / 255.0F;

			Matrix4f modelView = new Matrix4f(view).scale(DEPTH_PUSH).translate(placed.x(), placed.y(), placed.z());
			transforms.add(RenderSystem.getDynamicUniforms().writeTransform(modelView, new Vector4f(1.0F, 1.0F, 1.0F, alpha), new Vector3f(), texture));
		}

		var encoder = RenderSystem.getDevice().createCommandEncoder();

		try (RenderPass renderPass = depth != null
			? encoder.createRenderPass(() -> "selective_rendering translucent terrain", color, OptionalInt.empty(), depth, OptionalDouble.empty())
			: encoder.createRenderPass(() -> "selective_rendering translucent terrain", color, OptionalInt.empty())) {
			ScissorState scissor = RenderSystem.getScissorStateForRenderTypeDraws();
			if (scissor.enabled()) {
				renderPass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height());
			}

			RenderSystem.bindDefaultUniforms(renderPass);

			for (int index = 0; index < subset.size(); index++) {
				BufferPice piece = subset.get(index).bufferPice();
				RenderType pieceType = piece.translucent ? translucentType : solidType;
				RenderSetup state = ((RenderTypeAccessor) (Object) pieceType).selectiveRendering$state();

				renderPass.setPipeline(pieceType.pipeline());
				renderPass.setUniform("DynamicTransforms", transforms.get(index));
				renderPass.setVertexBuffer(0, piece.buffer);

				for (Entry<String, RenderSetup.TextureAndSampler> entry : state.getTextures().entrySet()) {
					renderPass.bindTexture(entry.getKey(), entry.getValue().textureView(), entry.getValue().sampler());
				}

				if (piece.indexBuffer != null) {
					renderPass.setIndexBuffer(piece.indexBuffer, piece.indexType);
				}
				else {
					RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(Mode.QUADS);
					renderPass.setIndexBuffer(indices.getBuffer(piece.indexCount), indices.type());
				}

				renderPass.drawIndexed(0, 0, piece.indexCount, 1);
			}
		}
	}
	*///?}

	private static float distanceSquared(Placed placed) {
		float x = placed.x() + 8.0F;
		float y = placed.y() + 8.0F;
		float z = placed.z() + 8.0F;
		return x * x + y * y + z * z;
	}

	// 在后台把一组四边形按到相机的距离从远到近排好，结果是新的索引数据，半透明才能正确叠加。
	private static void startSort(BufferPice piece, float cameraX, float cameraY, float cameraZ) {
		float[] centroids = piece.centroids;
		int count = piece.indexCount / 6;

		piece.jobX = cameraX;
		piece.jobY = cameraY;
		piece.jobZ = cameraZ;
		piece.sortJob = POOL.submit(() -> computeIndices(centroids, count, cameraX, cameraY, cameraZ));
	}

	// 按到相机的距离从远到近排序，生成索引数据
	private static ByteBuffer computeIndices(float[] centroids, int count, float cameraX, float cameraY, float cameraZ) {
		long[] keys = new long[count];

		for (int i = 0; i < count; i++) {
			float dx = centroids[i * 3] - cameraX;
			float dy = centroids[i * 3 + 1] - cameraY;
			float dz = centroids[i * 3 + 2] - cameraZ;
			float distance = dx * dx + dy * dy + dz * dz;
			keys[i] = ((long) Float.floatToIntBits(distance) << 32) | i;
		}

		Arrays.sort(keys);

		boolean wide = count * 4 > 65535;
		ByteBuffer data = ByteBuffer.allocateDirect(count * 6 * (wide ? 4 : 2)).order(ByteOrder.nativeOrder());

		for (int k = count - 1; k >= 0; k--) {
			int base = (int) (keys[k] & 0xFFFFFFFFL) * 4;
			int[] pattern = {base, base + 1, base + 2, base + 2, base + 3, base};

			for (int index : pattern) {
				if (wide) {
					data.putInt(index);
				} else {
					data.putShort((short) index);
				}
			}
		}

		data.flip();
		return data;
	}

	// 小的块第一次排序可以直接在渲染线程上进行？
	private static void sortNow(BufferPice piece, float cameraX, float cameraY, float cameraZ) {
		piece.jobX = cameraX;
		piece.jobY = cameraY;
		piece.jobZ = cameraZ;
		piece.sortJob = CompletableFuture.completedFuture(computeIndices(piece.centroids, piece.indexCount / 6, cameraX, cameraY, cameraZ));
		uploadSort(piece);
	}

	// 渲染线程落实后台排好的索引：第一次创建缓冲，之后原地写入。
	private static void uploadSort(BufferPice piece) {
		Future<ByteBuffer> job = piece.sortJob;
		piece.sortJob = null;

		if (piece.retired) {
			return;
		}

		ByteBuffer data;
		try {
			data = job.get();
		} catch (Exception exception) {
			return;
		}

		if (piece.indexBuffer == null) {
			piece.indexType = piece.indexCount / 6 * 4 > 65535 ? IndexType.INT : IndexType.SHORT;
			piece.indexBuffer = RenderSystem.getDevice().createBuffer(() -> "selective_rendering translucent terrain indices", GpuBuffer.USAGE_INDEX | GpuBuffer.USAGE_COPY_DST, data);
		} else {
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(piece.indexBuffer.slice(), data);
		}

		piece.sortX = piece.jobX;
		piece.sortY = piece.jobY;
		piece.sortZ = piece.jobZ;
	}

	private static void retire(Section section) {
		if (section.prior != null) {
			retire(section.prior);
			section.prior = null;
		}

		for (BufferPice piece : section.pieces()) {
			piece.retired = true;
			if (piece.sortJob != null) {
				piece.sortJob.cancel(true);
			}

			GRAVEYARD.add(piece.buffer);
			if (piece.indexBuffer != null) {
				GRAVEYARD.add(piece.indexBuffer);
			}
		}
	}

	// 区段里的四边形按透明度分组，每组一块缓冲。
	private static Section build(List<TerrainTranslucency.Quad> quads, RenderType type) {
		Map<GroupKey, List<TerrainTranslucency.Quad>> groups = new LinkedHashMap<>();

		for (TerrainTranslucency.Quad quad : quads) {
			GroupKey key = GroupKey.of(quad);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(quad);
		}

		List<BufferPice> pieces = new ArrayList<>();
		for (List<TerrainTranslucency.Quad> group : groups.values()) {
			pieces.add(buildPiece(group, type));
		}

		return new Section(quads, pieces);
	}

	// 后台线程：把四边形按透明度分组并写成顶点字节，不碰任何渲染线程状态。
	private static List<PieceData> buildData(List<TerrainTranslucency.Quad> quads, VertexFormat format) {
		Map<GroupKey, List<TerrainTranslucency.Quad>> groups = new LinkedHashMap<>();

		for (TerrainTranslucency.Quad quad : quads) {
			GroupKey key = GroupKey.of(quad);
			groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(quad);
		}

		List<PieceData> result = new ArrayList<>();
		for (List<TerrainTranslucency.Quad> group : groups.values()) {
			try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(1, group.size()) * 4 * VERTEX_STRIDE)) {
				BufferBuilder builder = newBuilder(bytes, format);
				float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
				float[] centroids = new float[group.size() * 3];
				fill(builder, group, bounds, centroids);

				try (MeshData mesh = builder.buildOrThrow()) {
					ByteBuffer copy = ByteBuffer.allocateDirect(mesh.vertexBuffer().remaining()).order(ByteOrder.nativeOrder());
					copy.put(mesh.vertexBuffer().duplicate());
					copy.flip();

					TerrainTranslucency.Quad first = group.get(0);
					result.add(new PieceData(copy, mesh.drawState().indexCount(), first.alpha(), first.global(), first.translucent(), first.region(), bounds, centroids));
				}
			}
		}

		return result;
	}

	// 渲染线程：把后台建好的数据上传成显存缓冲。
	private static Section upload(List<TerrainTranslucency.Quad> quads, List<PieceData> data) {
		List<BufferPice> pieces = new ArrayList<>();

		for (PieceData piece : data) {
			GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "selective_rendering translucent terrain", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, piece.vertices());
			pieces.add(new BufferPice(buffer, piece.indexCount(), piece.alpha(), piece.global(), piece.translucent(), piece.region(), piece.bounds(), piece.centroids()));
		}

		return new Section(quads, pieces);
	}

	private static void fill(BufferBuilder builder, List<TerrainTranslucency.Quad> quads, float[] bounds, float[] centroids) {
		int quadIndex = 0;

		for (TerrainTranslucency.Quad quad : quads) {
			for (int i = 0; i < 12; i++) {
				centroids[quadIndex * 3 + i % 3] += quad.positions()[i] * 0.25F;
				bounds[i % 3] = Math.min(bounds[i % 3], quad.positions()[i]);
				bounds[3 + i % 3] = Math.max(bounds[3 + i % 3], quad.positions()[i]);
			}

			quadIndex++;

			for (int i = 0; i < 4; i++) {
				builder.addVertex(quad.positions()[i * 3], quad.positions()[i * 3 + 1], quad.positions()[i * 3 + 2])
					.setColor(quad.colors()[i])
					.setUv(quad.uvs()[i * 2], quad.uvs()[i * 2 + 1])
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(quad.lights()[i])
					.setNormal(quad.normalX(), quad.normalY(), quad.normalZ());
			}
		}
	}

	private static BufferPice buildPiece(List<TerrainTranslucency.Quad> quads, RenderType type) {
		try (ByteBufferBuilder bytes = new ByteBufferBuilder(Math.max(1, quads.size()) * 4 * VERTEX_STRIDE)) {
			BufferBuilder builder = newBuilder(bytes, type.format());
			float[] bounds = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
			float[] centroids = new float[quads.size() * 3];
			fill(builder, quads, bounds, centroids);

			try (MeshData mesh = builder.buildOrThrow()) {
				GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "selective_rendering translucent terrain", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
				TerrainTranslucency.Quad first = quads.get(0);
				return new BufferPice(buffer, mesh.drawState().indexCount(), first.alpha(), first.global(), first.translucent(), first.region(), bounds, centroids);
			}
		}
	}

	public static void releaseAll() {
		current = null;
		PENDING.values().forEach(pending -> pending.future().cancel(true));
		PENDING.clear();
		GRAVEYARD.forEach(GpuBuffer::close);
		GRAVEYARD.clear();

		for (Section top : SECTIONS.values()) {
			for (Section section = top; section != null; section = section.prior) {
				for (BufferPice piece : section.pieces()) {
					piece.retired = true;
					piece.buffer.close();
					if (piece.indexBuffer != null) {
						piece.indexBuffer.close();
					}
				}
			}
		}

		SECTIONS.clear();
	}
}
