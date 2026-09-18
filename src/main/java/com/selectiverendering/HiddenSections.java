package com.selectiverendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.AABB;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which chunk sections the mod has drawn a hidden block in, so that a change can rebuild those
 * instead of the whole world.
 *
 * <p>Whether a block is drawn see-through is decided while its section is compiled, and the answer
 * is baked into the geometry: the alpha goes into the vertex colours, the quads move onto the
 * translucent layer, and which faces a block keeps is decided by whether the block beside it is
 * hidden. A change to any of that is not visible until the sections it lands in are compiled
 * again, which is why every setter used to rebuild the world.</p>
 *
 * <p>Which sections those are is not something the mod is ever asked. It is asked about one block
 * at a time, on a build thread, and by then the answer is being worked out for a section that is
 * already being rebuilt. So the sections are remembered as they go past, and a change rebuilds what
 * was remembered.</p>
 *
 * <p>Remembering rather than working it out is the right way round here because the two ways of
 * being wrong are not alike. Remembering too much costs a rebuild that did not have to happen.
 * Forgetting a section leaves it showing what it was told before the change, which is a block that
 * stays see-through after it was taken off the list, and no amount of rebuilding later puts that
 * right on its own. So what is kept is a section a hidden block was drawn in at some point, not one
 * where one is drawn now: whether it still is, is the question the compile being asked for answers.
 * A section that stopped being hidden is still rebuilt, and comes back with nothing in it.</p>
 *
 * <p>The ring of sections around each one goes with it. A face is kept or dropped by looking at the
 * block on the other side of it and that block can be in the next section, so a section is never
 * the only one that has to be redone: leave the ring out and the faces along the seam between two
 * sections are the ones that come out wrong.</p>
 */
public final class HiddenSections {
	/**
	 * Past this many sections, rebuilding just these costs more than rebuilding everything: each
	 * one brings the twenty-six around it, so a couple of thousand of them cover the world several
	 * times over. Where the two cross depends on the view distance, which is not worth dragging in
	 * to place a number that is only ever wrong about time and never about correctness.
	 */
	private static final int TOO_MANY_TO_BE_WORTH_IT = 1500;

	/**
	 * Nothing is ever taken out: a section is remembered when it is compiled, and one that has
	 * since left the view is not something the mod is told about either, so a long session would
	 * otherwise keep hold of every section the player has been near. Reaching this stops adding;
	 * it does not stop the tracking, and the next rebuild of the world empties it.
	 */
	private static final int MOST_REMEMBERED = 1 << 18;

	/**
	 * Written from the build threads and read from the render thread, so it is the concurrent kind
	 * of set: a rebuild may be asked for while a section is still being compiled elsewhere.
	 */
	private static final Set<Long> SECTIONS = ConcurrentHashMap.newKeySet();

	private HiddenSections() {
	}

	/**
	 * Remembers the section a hidden block was drawn in. Called from
	 * {@link SelectiveRenderingManager#getAlpha}, which every path that bakes the answer goes
	 * through, whichever renderer and whichever thread is doing it.
	 */
	public static void note(BlockPos pos) {
		if (SECTIONS.size() >= MOST_REMEMBERED) {
			return;
		}

		SECTIONS.add(SectionPos.asLong(pos.getX(), pos.getY(), pos.getZ()));
	}

	/**
	 * Rebuilds every section a hidden block was drawn in, and the ring around each of them.
	 *
	 * @return false when there was nothing to rebuild, which is also what a world with nothing
	 *         hidden in it looks like. The caller takes that as a sign to rebuild the world, since
	 *         a set that is empty because nothing has been compiled yet is a set that cannot be
	 *         trusted to know where the hidden blocks are.
	 */
	public static boolean mark() {
		LevelRenderer renderer = renderer();
		if (renderer == null || SECTIONS.isEmpty() || SECTIONS.size() > TOO_MANY_TO_BE_WORTH_IT) {
			return false;
		}

		for (long section : SECTIONS) {
			renderer.setSectionDirtyWithNeighbors(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
		}

		return true;
	}

	/**
	 * Rebuilds the sections a box covers and the ring around them, for a region being added or
	 * dropped. A region only ever speaks about the places it covers: whether the mode hides the
	 * inside of it or everything else, the answers that change are the ones inside the box, and the
	 * faces that change with them are the ones along its edge - which is the ring.
	 *
	 * <p>Nothing is rebuilt when the mode does not read the regions at all, since adding one then
	 * changes no answer anywhere.</p>
	 */
	public static void mark(AABB box) {
		LevelRenderer renderer = renderer();
		if (renderer == null) {
			return;
		}

		// A box sits on whole blocks, so its far corner is the last block that is still inside it
		// rather than the first one outside.
		int minX = SectionPos.blockToSectionCoord((int) Math.floor(box.minX));
		int minY = SectionPos.blockToSectionCoord((int) Math.floor(box.minY));
		int minZ = SectionPos.blockToSectionCoord((int) Math.floor(box.minZ));
		int maxX = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxX) - 1);
		int maxY = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxY) - 1);
		int maxZ = SectionPos.blockToSectionCoord((int) Math.ceil(box.maxZ) - 1);

		for (int y = minY; y <= maxY; y++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int x = minX; x <= maxX; x++) {
					renderer.setSectionDirtyWithNeighbors(x, y, z);
				}
			}
		}
	}

	/**
	 * Forgets every section, for a rebuild of the whole world. Everything is compiled again after
	 * one, so what is worth remembering gets remembered again as it goes past, and what is not is
	 * no longer anywhere at all.
	 */
	public static void clear() {
		SECTIONS.clear();
	}

	private static LevelRenderer renderer() {
		return Minecraft.getInstance().levelRenderer;
	}
}
