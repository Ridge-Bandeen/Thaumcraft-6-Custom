package thaumcraft.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Extra heat sources for the Crucible (and the Thaumatorium, which is heated through one), for blocks a tag can't
 * describe - one that only heats while lit, say. The block under the crucible counts as hot if it's in
 * {@code #thaumcraft:crucible_heaters}, is Nitor, or matches any predicate registered here. Register from
 * {@code ThaumcraftSetupEvent} or earlier; nothing changes without one.
 */
public final class CrucibleHeaters {

	private static final List<Predicate<BlockState>> SOURCES = new CopyOnWriteArrayList<>();

	private CrucibleHeaters() {
	}

	public static void addHeatSource(Predicate<BlockState> source) {
		SOURCES.add(source);
	}

	/** Whether any registered predicate counts this block as a heat source. */
	public static boolean isExtraHeatSource(BlockState state) {
		for (Predicate<BlockState> source : SOURCES) {
			if (source.test(state)) return true;
		}
		return false;
	}
}
