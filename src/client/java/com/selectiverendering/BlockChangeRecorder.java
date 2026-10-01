package com.selectiverendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "变化记录器"：录制一段时间内、当前选区里哪些方块位置发生了变化，
 * 结束时把它们合并成若干 {@link Region} 加进选区列表。
 *
 * <p>典型用法：想给一整台红石机器做选区 → 先框好选区 → 按记录键 →
 * 让机器跑一遍（或自己挖/放）→ 再按一次记录键 → 所有变过的位置自动变成选区。
 *
 * <h2>数据流</h2>
 * <pre>
 *   ClientLevelMixin(setServerVerifiedBlockState) ──► note(pos, 新状态)
 *   ClientLevelMixin(setBlock, 新状态是 moving_piston) ──► noteMoving(pos)
 *        ├─ 在选区内 &amp; 命中名单  ──► changed  (已确认的记录)
 *        └─ 涉及活塞 moving_piston ──► pending (延后处理，最多重试 MAX_RETRY 次)
 *                                          │
 *   ModConfigs 注册的客户端 tick  ──► onTick() 每 tick 最多处理 PER_TICK 个 pending
 *                                          │
 *   停止 ──► RegionMerger.merge(changed) ──► SelectiveRenderingManager.addRegions(...)
 * </pre>
 *
 * <h2>为什么要 pending 这一层</h2>
 * <p>活塞推动时，方块状态会先变成 {@code moving_piston}（壳），真正被推的方块藏在
 * {@code PistonMovingBlockEntity} 里。收到变化的那一刻壳可能还没建好，
 * 所以要等一个 tick 再回头取 {@code getMovedState()} 来判定。
 *
 * <h2>为什么 {@code moving_piston} 必须单独抓一次</h2>
 * <p>关键事实：<b>服务端从不把 {@code moving_piston} 发给客户端</b>。
 * {@code PistonBaseBlock.moveBlocks} 用的是 flag 324（不含 {@code UPDATE_CLIENTS}），
 * 客户端的活塞动画是收到<b>方块事件包</b>后自己跑一遍 {@code moveBlocks} 造出来的
 * （见 {@code ClientPacketListener.handleBlockEvent} → {@code Level.blockEvent}），
 * 走的是 {@code ClientLevel.setBlock}，根本不经过 {@code setServerVerifiedBlockState}。
 *
 * <p>所以只挂 {@code setServerVerifiedBlockState} 时，能不能录到"正在被推动的方块"
 * 全看运气：只有当服务端恰好在客户端还留着 {@code moving_piston} 的那一两 tick
 * 里补发一个方块更新包，{@link #note} 里的"当前状态是 moving_piston"分支才会命中。
 * 活塞头（{@code armPos}）常常正好撞上这个窗口，被推的方块却撞不上——
 * 表现出来的现象就是"加了 {@code *[moving=true]} 却只录到活塞头那一格"。
 *
 * <p>修法：再挂一次 {@code ClientLevel.setBlock}，凡是"本地新状态是 moving_piston"
 * 的位置都直接丢进 pending，之后的解析流程和原来一样。
 *
 * <h2>线程与容量</h2>
 * <p>{@code changed}/{@code pending} 是并发集合：写入来自
 * {@code setServerVerifiedBlockState}（网络/客户端包处理路径），
 * 读取与消化在客户端 tick，两者不保证同一线程。
 * {@code changed} 还有 {@link #MAX_CHANGED} 的容量上限，
 * 达到上限后停止记录并提示，避免长时间录制大选区把内存吃光。
 */
public final class BlockChangeRecorder {
	private static final int PER_TICK = 2000;

	/** {@code changed} 的容量上限，防止长时间录制把内存吃光。 */
	private static final int MAX_CHANGED = 1 << 19;

	/**
	 * pending 里同一个位置最多重试几次。
	 *
	 * <p>活塞刚推动的那一两 tick，方块状态已经变成 {@code moving_piston} 但
	 * {@code PistonMovingBlockEntity} 可能还没挂上；此时必须<b>把位置放回去</b>等下一 tick，
	 * 而不是直接丢掉——早期实现就是丢掉了，于是被推的方块经常录不进来。
	 */
	private static final byte MAX_RETRY = 5;

	/** 待解析的位置 → 已经重试过几次。用 Map 而不是 Set 就是为了带这个计数。 */
	private static final Map<Long, Byte> pending = new ConcurrentHashMap<>();

	private static final Set<Long> changed = ConcurrentHashMap.newKeySet();

	private static volatile boolean recording;

	private BlockChangeRecorder() {
	}

	public static boolean isRecording() {
		return recording;
	}

	public static int count() {
		return changed.size();
	}

	/**
	 * 开始录制。前置条件：必须先框好一个选区（两个角点都设过），否则拒绝并开始提示。
	 */
	public static boolean start() {
		if (recording) {
			return false;
		}

		if (SelectiveRenderingManager.selection() == null) {
			say("no_selection", false);
			return false;
		}

		changed.clear();
		pending.clear();
		recording = true;
		Log.say("[recorder] started in {}", SelectiveRenderingManager.selection().toSource());
		say("started", true);

		// 默认现在是白名单，而"白名单 + 空名单"按字面语义是什么都录不到。
		// manager 那边对空名单做了"不过滤"的兜底，这里把兜底行为说清楚，免得玩家以为坏了。
		if (SelectiveRenderingManager.recordSources().isEmpty()) {
			say("empty_list", false);
		}

		return true;
	}

	public static boolean stop() {
		if (!recording) {
			return false;
		}

		recording = false;
		pending.clear();

		List<BlockPos> positions = new ArrayList<>(changed.size());
		for (long packed : changed) {
			positions.add(BlockPos.of(packed));
		}

		changed.clear();

		// 先把散点贪心压成尽量少的长方体：
		// 逐点去裁剪/新增都不可行（每挖一个方块位置都会把选区切成最多 6 块），
		// 必须先合并。RegionMerger 产出的盒子互不重叠，后续的集合减法才不会碎片爆炸。
		List<Region> boxes = RegionMerger.merge(positions);
		Log.say("[recorder] stopped: {} position(s) -> {} region(s)", positions.size(), boxes.size());

		if (boxes.isEmpty()) {
			say("nothing", false);
			return true;
		}

		boolean subtract = SelectiveRenderingManager.getRecordApplyMode() == SelectiveRenderingManager.RecordApplyMode.SUBTRACT;
		boolean applied = subtract
			? SelectiveRenderingManager.subtractRegions(boxes)
			: SelectiveRenderingManager.addRegions(boxes) > 0;

		if (!applied) {
			// 裁剪模式下可能是"没有选区可挖"或"碎片过多被放弃"
			say("nothing", false);
			return true;
		}

		say(subtract ? "clipped" : "recorded", true, String.valueOf(boxes.size()));

		return true;
	}

	public static void toggle() {
		if (recording) {
			stop();
		}
		else {
			start();
		}
	}

	/**
	 * 收到一次服务端确认的方块变化。{@code ClientLevelMixin} 注入
	 * {@code setServerVerifiedBlockState} 后调用。
	 *
	 * @param incoming 变化后的新状态；同时也会检查变化前的状态，
	 *                 只要<b>新旧任意一个</b>命中名单就记录——这样"放下方块"和"打掉方块"都能录到。
	 */
	public static void note(BlockPos pos, BlockState incoming) {
		if (!recording || !inside(pos)) {
			return;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}

		long packed = pos.asLong();
		if (changed.contains(packed)) {
			return;
		}

		// 本方法是 @At("HEAD")，此刻客户端世界里还是"变化前"的状态。
		// 活塞推动中的方块要换成它真正携带的状态（getMovedState）再判定，
		// 否则 [*][moving=true] 这类规则永远只能命中运气好的那几格。
		BlockState current = level.getBlockState(pos);

		if (isBeingMoved(current)) {
			BlockState carried = carried(level, pos);
			if (carried == null) {
				defer(packed);
				return;
			}

			consider(packed, carried, true);
			return;
		}

		// 服务端实际上不会发 moving_piston（见类注释），留着只是兜底。
		if (isBeingMoved(incoming)) {
			defer(packed);
			return;
		}

		if (wanted(current, false)) {
			consider(packed, current, false);
			return;
		}

		consider(packed, incoming, false);
	}

	/**
	 * 客户端本地有位置被活塞变成 {@code moving_piston}。
	 * {@code ClientLevelMixin} 注入 {@code ClientLevel.setBlock} 后调用——
	 * 这是拿到"正在被推动的方块"的唯一可靠入口，详见类注释。
	 */
	public static void noteMoving(BlockPos pos) {
		if (!recording || !inside(pos)) {
			return;
		}

		long packed = pos.asLong();
		if (changed.contains(packed)) {
			return;
		}

		// 此刻 PistonMovingBlockEntity 还没挂上（setBlockEntity 在 setBlock 之后才调用），
		// 直接交给 onTick 下一 tick 解析。
		defer(packed);
	}

	public static void onTick(Minecraft minecraft) {
		if (!recording) {
			return;
		}

		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}

		int budget = PER_TICK;
		Iterator<Map.Entry<Long, Byte>> iterator = pending.entrySet().iterator();

		while (iterator.hasNext() && budget > 0) {
			Map.Entry<Long, Byte> entry = iterator.next();
			iterator.remove();
			budget--;

			long packed = entry.getKey();
			if (changed.contains(packed)) {
				continue;
			}

			BlockPos pos = BlockPos.of(packed);
			BlockState state = level.getBlockState(pos);
			boolean moving = false;

			if (isBeingMoved(state)) {
				BlockState carried = carried(level, pos);
				if (carried == null) {
					// 壳还在、BE 没建好：放回队列重试，别丢掉这个位置
					byte tried = entry.getValue();
					if (tried < MAX_RETRY) {
						pending.put(packed, (byte) (tried + 1));
					}

					continue;
				}

				state = carried;
				moving = true;
			}

			consider(packed, state, moving);
		}
	}

	private static boolean inside(BlockPos pos) {
		Region selection = SelectiveRenderingManager.selection();
		return selection != null && selection.contains(pos);
	}

	private static void defer(long packed) {
		pending.putIfAbsent(packed, (byte) 0);
	}

	/** 判定 + 记账的唯一出口，容量检查也在这里，避免每个调用点各写一遍。 */
	private static void consider(long packed, BlockState state, boolean moving) {
		if (changed.contains(packed) || !wanted(state, moving)) {
			return;
		}

		if (changed.size() >= MAX_CHANGED) {
			Log.say("[recorder] 已达容量上限 {}，自动停止记录", MAX_CHANGED);
			stop();
			say("full", false, String.valueOf(MAX_CHANGED));
			return;
		}

		changed.add(packed);
	}

	private static boolean isBeingMoved(BlockState state) {
		return state.getBlock() instanceof MovingPistonBlock;
	}

	private static BlockState carried(ClientLevel level, BlockPos pos) {
		BlockEntity entity = level.getBlockEntity(pos);
		return entity instanceof PistonMovingBlockEntity piston ? piston.getMovedState() : null;
	}

	private static boolean wanted(BlockState state, boolean moving) {
		return SelectiveRenderingManager.matchesRecorded(state, moving);
	}

	private static void say(String key, boolean good, Object... arguments) {
		String prefix = SelectiveRendering.MOD_ID + ".message.";
		BlockListMessage.show(Component.translatable(prefix + key, arguments).getString(), good);
	}
}
