package com.selectiverendering;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the state behind selective block rendering.
 *
 * <p>Chunk sections are compiled on worker threads, and the alpha is baked into the compiled
 * geometry rather than applied at draw time. That means every field here is read from those worker
 * threads, so mutations publish an immutable snapshot instead of sharing the live collection, and
 * every change has to be followed by a rebuild of the sections it lands in. {@link HiddenSections}
 * remembers which ones those are for the changes that cannot have reached anywhere new; the rest
 * rebuild the whole world with {@link #rebuildChunks()}.</p>
 */
public final class SelectiveRenderingManager {
	public static final int TRANSPARENCY_STEP = 5;
	public static final int MAX_TRANSPARENCY = 100;

	/**
	 * What a fresh config file gets. The config screen names them too, since they are what its
	 * reset buttons put back: a reset has to land on the value the mod started with and not on
	 * whatever the screen happened to be opened with.
	 */
	public static final Mode DEFAULT_MODE = Mode.OFF;
	public static final int DEFAULT_TRANSPARENCY = 50;
	public static final String DEFAULT_WAND = "minecraft:breeze_rod";

	/**
	 * The order these are declared in is the order the wheel runs through, and it is the one
	 * Lucidity uses: off, then the three that go by the region from the inside, then the same
	 * three from the outside, then the two that go by the list alone. Each group reads the same
	 * way round - listed, not listed, everything - so turning the wheel through one group teaches
	 * the next.
	 *
	 * @param key suffix of the translation key, see the lang files.
	 */
	public enum Mode {
		OFF("off"),
		/**
		 * Inside one of the regions and listed. Naming a block narrows the region down instead
		 * of widening it.
		 */
		REGION_INSIDE_LISTED("region_inside_listed"),
		/**
		 * Inside one of the regions and not listed, so the list punches holes in the region.
		 */
		REGION_INSIDE_UNLISTED("region_inside_unlisted"),
		/**
		 * Inside one of the regions, whatever block it is.
		 */
		REGION_INSIDE("region_inside"),
		REGION_OUTSIDE_LISTED("region_outside_listed"),
		REGION_OUTSIDE_UNLISTED("region_outside_unlisted"),
		/**
		 * Outside every region, which is the same as looking at the regions on their own.
		 */
		REGION_OUTSIDE("region_outside"),
		/**
		 * Listed, wherever it is.
		 */
		BLACKLIST("blacklist"),
		/**
		 * Not listed, wherever it is, which dims the world around the list.
		 */
		WHITELIST("whitelist");

		private final String key;

		Mode(String key) {
			this.key = key;
		}

		public Component displayName() {
			return Component.translatable(SelectiveRendering.MOD_ID + ".mode." + key);
		}

		public Mode cycle(int direction) {
			Mode[] values = values();
			return values[Math.floorMod(ordinal() + direction, values.length)];
		}

		/**
		 * Every mode but the two that go by the list alone has to know where the regions are.
		 */
		public boolean usesRegion() {
			return this != OFF && this != BLACKLIST && this != WHITELIST;
		}

		/**
		 * The modes that go by the list. Inside and outside on their own do not, and there is no
		 * point making a list a condition of a mode that never reads it.
		 */
		public boolean usesList() {
			return this != OFF && this != REGION_INSIDE && this != REGION_OUTSIDE;
		}

		/**
		 * Whether this mode hides the blocks that are on the list, as opposed to the ones that are
		 * not. Which way round it reads the list is which way a change to the list pushes, and so
		 * which of the two rebuilds that change asks for: naming one more block hides more under a
		 * mode that hides the list, and uncovers under one that hides everything else.
		 */
		public boolean hidesListed() {
			return this == BLACKLIST || this == REGION_INSIDE_LISTED || this == REGION_OUTSIDE_LISTED;
		}
	}

	private static final Object LOCK = new Object();
	private static final List<BlockMatchRule> RULES = new ArrayList<>();

	/**
	 * The regions that were added, as opposed to the two corners being clicked right now. The region
	 * modes go by these; the corners only matter until they are turned into one of these.
	 */
	private static final List<Region> REGIONS = new ArrayList<>();

	private static volatile Mode mode = DEFAULT_MODE;
	private static volatile int transparency = DEFAULT_TRANSPARENCY;
	private static volatile List<BlockMatchRule> ruleSnapshot = List.of();
	private static volatile List<Region> regionSnapshot = List.of();

	private static volatile String wand = DEFAULT_WAND;

	/**
	 * The corners as they were clicked, kept so the overlay can show them and so a corner can be
	 * moved without losing the other one. Null until it is set.
	 */
	private static volatile BlockPos corner1;
	private static volatile BlockPos corner2;

	/**
	 * The same pair as a box that covers whole blocks, or null while a corner is missing. AABB is
	 * immutable, so publishing a new one is enough to make it safe to read from the section build
	 * threads.
	 */
	private static volatile AABB region;

	/**
	 * Which of the two corners the wheel moves while alt is held: 0, 1, or -1 for neither. Placing
	 * a corner picks it, so the wheel acts on the one that was just put down without the player
	 * having to say which.
	 */
	private static volatile int selectedCorner = -1;

	private SelectiveRenderingManager() {
	}

	/**
	 * Restores the saved state. The rules are parsed here, so one that names a block or a tag this
	 * world does not have keeps its place in the list and starts matching if it ever shows up.
	 */
	public static void load() {
		ModConfig config = ModConfig.get();

		synchronized (LOCK) {
			RULES.clear();
			REGIONS.clear();

			mode = config.mode == null ? Mode.OFF : config.mode;
			transparency = Math.clamp(config.transparency, 0, MAX_TRANSPARENCY);
			wand = config.wand == null || config.wand.isBlank() ? DEFAULT_WAND : config.wand.trim();

			if (config.rules != null) {
				for (String source : config.rules) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null) {
						RULES.add(rule);
					}
				}
			}

			corner1 = toBlockPos(config.regionPos1);
			corner2 = toBlockPos(config.regionPos2);
			// The last corner that was placed is the one the wheel picks up where it left off.
			selectedCorner = corner2 != null ? 1 : corner1 != null ? 0 : -1;
			updateRegion();

			if (config.regions != null) {
				for (int[] values : config.regions) {
					Region added = toRegion(values);
					if (added != null) {
						REGIONS.add(added);
					}
				}
			}

			publish();
		}

		Log.say("[state] loaded: mode {}, transparency {}%, {} rule(s), {} region(s)", mode, transparency, RULES.size(), REGIONS.size());
	}

	public static Mode getMode() {
		return mode;
	}

	public static void setMode(Mode newMode) {
		if (mode == newMode) {
			return;
		}

		mode = newMode;
		Log.say("[state] mode {}", newMode.name());
		persist();

		// Two modes can be exact opposites of each other - blacklist and whitelist hide precisely
		// the blocks the other one leaves alone - so a change of mode is a change that can have
		// turned the answer over at every position in the world at once.
		rebuildChunks();
	}

	public static void cycleMode(int direction) {
		setMode(mode.cycle(direction));
	}

	public static int getTransparency() {
		return transparency;
	}

	public static void setTransparency(int value) {
		int clamped = Math.clamp(value, 0, MAX_TRANSPARENCY);
		if (clamped == transparency) {
			return;
		}

		transparency = clamped;
		Log.say("[state] transparency {}%", clamped);
		persist();
		rebuildHidden();
	}

	/**
	 * @param index which corner, 0 or 1.
	 * @return that corner, or null when it has not been set.
	 */
	public static BlockPos getCorner(int index) {
		return index == 0 ? corner1 : corner2;
	}

	/**
	 * The region as a box, or null while a corner is missing. For the outline that shows where the
	 * region is, which needs the box rather than the corners because the far corner block reaches
	 * one further than its own position.
	 */
	public static AABB regionBox() {
		return region;
	}

	/**
	 * Moves one corner of the region and picks it as the one the wheel acts on. Setting the second
	 * one is what makes it take effect, and setting a corner again just moves that corner and leaves
	 * the other alone.
	 *
	 * <p>No rebuild here. The corners are only what gets added later, and the modes go by the regions
	 * that were added, so moving one changes nothing that was ever compiled.</p>
	 *
	 * @param index which corner to move, 0 or 1.
	 */
	public static void setCorner(int index, BlockPos pos) {
		synchronized (LOCK) {
			if (index == 0) {
				corner1 = pos.immutable();
			}
			else {
				corner2 = pos.immutable();
			}

			selectedCorner = index;
			updateRegion();
		}

		persist();
	}

	/**
	 * Whether this is the corner the wheel moves while alt is held, so it can be drawn as the one
	 * that is picked.
	 */
	public static boolean isCornerSelected(int index) {
		return selectedCorner == index;
	}

	/**
	 * Moves the corner the wheel acts on one step along a direction, which is what alt plus the
	 * wheel does. Nothing happens while no corner is placed or none is picked, since there is no
	 * coordinate to move.
	 *
	 * <p>No rebuild, for the same reason {@link #setCorner(int, BlockPos)} does not do one.</p>
	 */
	public static void moveSelectedCorner(Direction direction, int amount) {
		synchronized (LOCK) {
			int index = selectedCorner;
			if (index < 0) {
				return;
			}

			BlockPos pos = index == 0 ? corner1 : corner2;
			if (pos == null) {
				return;
			}

			BlockPos moved = pos.relative(direction, amount).immutable();
			if (index == 0) {
				corner1 = moved;
			}
			else {
				corner2 = moved;
			}

			updateRegion();
		}

		persist();
	}

	/**
	 * Adds the two selected corners to the region list. The corners are left where they are, so the
	 * same pair can be added again after one of them has been moved.
	 *
	 * @return false while a corner is still missing, since there is no region to add yet.
	 */
	public static boolean addRegionFromSelection() {
		BlockPos first = corner1;
		BlockPos second = corner2;
		if (first == null || second == null) {
			return false;
		}

		return addRegion(Region.of(first, second));
	}

	public static boolean addRegion(Region newRegion) {
		synchronized (LOCK) {
			if (REGIONS.contains(newRegion)) {
				Log.say("[state] region already added: {}", newRegion.toSource());
				return false;
			}

			REGIONS.add(newRegion);
			publish();
		}

		Log.say("[state] region added: {}", newRegion.toSource());
		persist();

		// A region only ever speaks about the places it covers, whichever way round the mode reads
		// it, so its box is the whole of what has to be compiled again.
		if (mode.usesRegion()) {
			HiddenSections.mark(newRegion.box());
		}

		return true;
	}

	/**
	 * Drops every region covering the given position, which is what clicking a block should do: it
	 * undoes whichever region is acting on that block, the same way removing a rule works.
	 */
	public static boolean removeRegionAt(BlockPos pos) {
		List<Region> dropped;

		synchronized (LOCK) {
			// The regions that go are the ones covering the block that was clicked, which can be
			// more than one where they overlap. Each of them has to be dropped where it stood, so
			// which ones they were is kept rather than only the fact that something went.
			dropped = REGIONS.stream().filter(added -> added.contains(pos)).toList();
			if (dropped.isEmpty()) {
				return false;
			}

			REGIONS.removeAll(dropped);
			publish();
		}

		Log.say("[state] region removed at {} {} {}", pos.getX(), pos.getY(), pos.getZ());
		persist();

		if (mode.usesRegion()) {
			for (Region region : dropped) {
				HiddenSections.mark(region.box());
			}
		}

		return true;
	}

	/**
	 * One click on a region: the one under the crosshair goes, and with nothing there the two
	 * corners are added instead. Same as removing a rule, it undoes whichever region is acting on
	 * the block clicked, so one button covers both halves of the pair.
	 */
	public static boolean toggleRegionAt(BlockPos pos) {
		return removeRegionAt(pos) || addRegionFromSelection();
	}

	/**
	 * The same click for the one block that was pointed at: a region covering that block alone,
	 * which is the region the two corners would make were they both standing on it. The corners
	 * that are placed are left where they are, so a box that is being picked out to be added as a
	 * whole is not lost to adding a single block on the way.
	 */
	public static boolean toggleSingleRegionAt(BlockPos pos) {
		return removeRegionAt(pos) || addRegion(Region.of(pos, pos));
	}

	/**
	 * The added regions, for the outline that shows where they are.
	 */
	public static List<Region> regions() {
		return regionSnapshot;
	}

	/**
	 * The added regions as the text a config screen edits them as: the low corner and the high one,
	 * three numbers each, separated by a colon. See {@link Region#toSource()}.
	 */
	public static List<String> regionSources() {
		return regionSnapshot.stream().map(Region::toSource).toList();
	}

	/**
	 * Replaces every added region with these texts, dropping the ones that cannot be read. The list
	 * the screen shows stays in step with the one that applies, the same way
	 * {@link #setRuleSources(List)} keeps the block list honest.
	 */
	public static void setRegionSources(List<String> sources) {
		synchronized (LOCK) {
			REGIONS.clear();

			if (sources != null) {
				for (String source : sources) {
					Region parsed = Region.parse(source);
					if (parsed != null && !REGIONS.contains(parsed)) {
						REGIONS.add(parsed);
					}
				}
			}

			publish();
		}

		persist();

		// Same as the list: regions can have been added and dropped in the same edit.
		if (mode.usesRegion()) {
			rebuildChunks();
		}
	}

	/**
	 * Rebuilds the box from the two corners. Grows by one on the far side so the corner block is
	 * inside the region rather than only touching it. Caller holds {@link #LOCK}.
	 */
	private static void updateRegion() {
		if (corner1 == null || corner2 == null) {
			region = null;
			return;
		}

		region = new AABB(
			Math.min(corner1.getX(), corner2.getX()),
			Math.min(corner1.getY(), corner2.getY()),
			Math.min(corner1.getZ(), corner2.getZ()),
			Math.max(corner1.getX(), corner2.getX()) + 1,
			Math.max(corner1.getY(), corner2.getY()) + 1,
			Math.max(corner1.getZ(), corner2.getZ()) + 1
		);
	}

	/**
	 * @param source rule text, see {@link BlockMatchRule}.
	 * @return false when the text cannot be parsed or the rule is already listed.
	 */
	public static boolean addRule(String source) {
		BlockMatchRule rule = BlockMatchRule.parse(source);
		if (rule == null) {
			return false;
		}

		synchronized (LOCK) {
			for (BlockMatchRule existing : RULES) {
				if (existing.source().equalsIgnoreCase(rule.source())) {
					return false;
				}
			}

			RULES.add(rule);
			publish();
		}

		Log.say("[state] rule added: {}", rule.source());
		persist();
		rebuildAfterListChange(true);
		return true;
	}

	public static boolean removeRule(String source) {
		synchronized (LOCK) {
			if (!RULES.removeIf(rule -> rule.source().equalsIgnoreCase(source.trim()))) {
				return false;
			}

			publish();
		}

		Log.say("[state] rule removed: {}", source.trim());
		persist();
		rebuildAfterListChange(false);
		return true;
	}

	/**
	 * The rules as the text they were written as, for a config screen that edits them as text.
	 */
	public static List<String> ruleSources() {
		return ruleSnapshot.stream().map(BlockMatchRule::source).toList();
	}

	/**
	 * Replaces the whole list with these texts. Anything that will not parse, or that is already
	 * listed, is dropped, which is the same treatment {@link #addRule(String)} gives one rule. Not
	 * returning what was dropped keeps the list the screen shows in step with the one that applies.
	 */
	public static void setRuleSources(List<String> sources) {
		synchronized (LOCK) {
			RULES.clear();

			if (sources != null) {
				for (String source : sources) {
					BlockMatchRule rule = BlockMatchRule.parse(source);
					if (rule != null && !hasRule(rule.source())) {
						RULES.add(rule);
					}
				}
			}

			publish();
		}

		persist();

		// The list as a whole, so it can have grown in one place and shrunk in another and there is
		// no saying which way any of it went. Only a mode that does not read the list is untouched.
		if (mode.usesList()) {
			rebuildChunks();
		}
	}

	public static boolean hasRule(String source) {
		String trimmed = source.trim();
		for (BlockMatchRule rule : RULES) {
			if (rule.source().equalsIgnoreCase(trimmed)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @return the alpha the block should be rendered with, or {@code -1} to render it normally.
	 *         An alpha of {@code 0} means the block is skipped entirely.
	 */
	public static int getAlpha(BlockState state, BlockPos pos) {
		Mode current = mode;
		if (current == Mode.OFF) {
			return -1;
		}

		// A block carried by a piston is not the block it looks like: the state in the world
		// belongs to the moving piston head, and the block that is actually travelling rides in
		// the block entity. Both the state and the position are swapped for the ones the block
		// came from, so a rule or a region keeps holding for as long as the piston carries it
		// instead of the block popping back in mid-flight.
		//
		// Sections compile on worker threads, and this reads the world from there. That is a
		// race in theory and Lucidity does the same in practice; the worst outcome is a null
		// here, which falls through and leaves the block alone for that one frame.
		Minecraft minecraft = Minecraft.getInstance();
		BlockEntity entity = state.getBlock() instanceof MovingPistonBlock && minecraft.level != null
			? minecraft.level.getBlockEntity(pos)
			: null;
		if (entity instanceof PistonMovingBlockEntity piston) {
			BlockState moved = piston.getMovedState();
			if (moved != null) {
				state = moved;
				pos = pos.relative(piston.getMovementDirection().getOpposite());
			}
		}

		boolean needsRegion = current.usesRegion();
		boolean needsList = current.usesList();

		List<Region> regions = needsRegion ? regionSnapshot : List.of();
		List<BlockMatchRule> rules = needsList ? ruleSnapshot : List.of();

		// With no region there is nothing to go on, and reading it as one covering the world would
		// hide everything, which is the same trap as an empty list: an empty list would hide the
		// entire world in whitelist mode, and a mode with no region would do the same. Neither is
		// what anyone wants, and either would take a rebuild of every section it reached to put
		// right, for a setting the player did not ask to turn everything off with.
		if ((needsRegion && regions.isEmpty()) || (needsList && rules.isEmpty())) {
			return -1;
		}

		boolean inside = isInside(pos, regions);
		boolean listed = matches(state, rules);

		boolean hidden = switch (current) {
			case REGION_INSIDE_LISTED -> inside && listed;
			case REGION_INSIDE_UNLISTED -> inside && !listed;
			case REGION_INSIDE -> inside;
			case REGION_OUTSIDE_LISTED -> !inside && listed;
			case REGION_OUTSIDE_UNLISTED -> !inside && !listed;
			case REGION_OUTSIDE -> !inside;
			case BLACKLIST -> listed;
			case WHITELIST -> !listed;
			case OFF -> false;
		};

		if (!hidden) {
			return -1;
		}

		// Where the answer was yes is worth remembering: it is the one thing that says which
		// sections a later change has to be compiled into again.
		HiddenSections.note(pos);
		return alpha();
	}

	/**
	 * The alpha for a fluid, asked for separately because a fluid is not a block model. It is the
	 * block it sits in that decides: a fluid is never matched by a block rule of its own, so
	 * following the block is the only answer that keeps water and lava in step with the world
	 * around them, and it leaves one transparency to set rather than two.
	 */
	public static int getFluidAlpha(BlockState state, BlockPos pos) {
		return getAlpha(state, pos);
	}

	/**
	 * Fluids that are not on the translucent layer will ignore alpha, so any time we are actually
	 * fading things out the fluid layer has to move to translucent. This is global because the
	 * render-layer lookup does not know the position of the fluid.
	 */
	public static boolean shouldRenderFluidsTranslucent() {
		if (mode == Mode.OFF) {
			return false;
		}

		return transparency < MAX_TRANSPARENCY;
	}

	public static boolean isHidden(BlockState state, BlockPos pos) {
		return getAlpha(state, pos) >= 0;
	}

	/**
	 * The same question for callers that were handed the level rather than the state, which is
	 * what the light hooks have.
	 */
	public static boolean isHidden(BlockGetter level, BlockPos pos) {
		return getAlpha(level.getBlockState(pos), pos) >= 0;
	}

	public static String getWand() {
		return wand;
	}

	public static void setWand(String value) {
		String trimmed = value == null ? "" : value.trim();
		if (trimmed.isEmpty()) {
			trimmed = DEFAULT_WAND;
		}

		if (trimmed.equals(wand)) {
			return;
		}

		wand = trimmed;
		persist();
	}

	/**
	 * The same question as {@link #isHidden(BlockState, BlockPos)} for callers that only have a
	 * position to go on. Reads the level, so it is only safe where the level is reachable.
	 */
	public static boolean isHiddenAt(BlockPos pos) {
		return getAlphaAt(pos) >= 0;
	}

	/**
	 * The alpha for whatever block sits at that position, read from the level. This is what the
	 * render-thread hooks use - block entities, moving blocks - as opposed to the meshing hooks,
	 * which are handed the state and the position directly.
	 *
	 * @return the alpha, or {@code -1} when the block renders normally.
	 */
	public static int getAlphaAt(BlockPos pos) {
		if (mode == Mode.OFF) {
			return -1;
		}

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null) {
			return -1;
		}

		return getAlpha(minecraft.level.getBlockState(pos), pos);
	}

	/**
	 * The transparency the hidden things are drawn with, for the vertex writers that only learn
	 * that something is hidden but not how much.
	 */
	public static int getHiddenAlpha() {
		return alpha();
	}

	private static boolean matches(BlockState state, List<BlockMatchRule> rules) {
		for (BlockMatchRule rule : rules) {
			if (rule.matches(state)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Rebuilds the sections a hidden block was drawn in, and the whole world when there is nothing
	 * to go on. For a change that cannot have reached anywhere new: taking a rule off the list and
	 * turning the transparency down only change the answer where the answer was already yes.
	 */
	private static void rebuildHidden() {
		if (!HiddenSections.mark()) {
			rebuildChunks();
		}
	}

	/**
	 * Rebuilds what a change to the block list has reached, which is one of three answers and is
	 * known before anything is rebuilt.
	 *
	 * <p>A mode that never reads the list is not reached by it at all, so nothing is rebuilt. A
	 * change that can only uncover blocks rebuilds the sections something is drawn in, since the
	 * places it reaches are places that were already hidden and so are remembered. A change that
	 * can hide a block that was not hidden rebuilds the world, because what is remembered is which
	 * sections have held a hidden block, never which ones hold a block of a given kind - so a rule
	 * that reaches somewhere new is a rule with nowhere to look for where that is.</p>
	 *
	 * @param added whether the list grew by a rule or lost one.
	 */
	private static void rebuildAfterListChange(boolean added) {
		if (!mode.usesList()) {
			return;
		}

		// Growing a list that is hidden hides more, and shrinking a list that is not hidden hides
		// more too: the two that can reach a block that was not hidden before.
		if (mode.hidesListed() == added) {
			rebuildChunks();
			return;
		}

		rebuildHidden();
	}

	/**
	 * Rebuilds the whole world, for a change that can have reached anywhere: a rule going onto the
	 * list, a different mode, or a list replaced wholesale. Nothing knows where the newly hidden
	 * blocks are until the sections have been through again, so every section goes.
	 */
	public static void rebuildChunks() {
		HiddenSections.clear();

		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.levelRenderer != null) {
			minecraft.levelRenderer.allChanged();
		}
	}

	/**
	 * Reads a corner back from the config. It is three ints there because Gson has no business
	 * walking a BlockPos.
	 */
	private static BlockPos toBlockPos(int[] values) {
		return values == null || values.length != 3 ? null : new BlockPos(values[0], values[1], values[2]);
	}

	private static int[] toArray(BlockPos pos) {
		return pos == null ? null : new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	private static int[] toArray(Region region) {
		return new int[] {
			region.min().getX(), region.min().getY(), region.min().getZ(),
			region.max().getX(), region.max().getY(), region.max().getZ()
		};
	}

	/**
	 * Reads a region back from the config. Two corners as three ints each, for the same reason the
	 * single selection is.
	 */
	private static Region toRegion(int[] values) {
		return values == null || values.length != 6 ? null : new Region(
			new BlockPos(values[0], values[1], values[2]),
			new BlockPos(values[3], values[4], values[5])
		);
	}

	private static int alpha() {
		return Math.round(255F * (MAX_TRANSPARENCY - transparency) / MAX_TRANSPARENCY);
	}

	/**
	 * Whether the position falls in any of the regions. An empty list is outside, which is what
	 * makes the mode that dims the outside of the regions fall back to dimming nothing at all.
	 */
	private static boolean isInside(BlockPos pos, List<Region> regions) {
		for (Region added : regions) {
			if (added.contains(pos)) {
				return true;
			}
		}

		return false;
	}

	private static void publish() {
		ruleSnapshot = List.copyOf(RULES);
		regionSnapshot = List.copyOf(REGIONS);
	}

	private static void persist() {
		ModConfig config = ModConfig.get();
		config.mode = mode;
		config.transparency = transparency;
		config.wand = wand;
		config.rules = RULES.stream().map(BlockMatchRule::source).toList();
		config.regionPos1 = toArray(corner1);
		config.regionPos2 = toArray(corner2);
		config.regions = REGIONS.stream().map(SelectiveRenderingManager::toArray).toList();
		ModConfig.save();
	}
}
