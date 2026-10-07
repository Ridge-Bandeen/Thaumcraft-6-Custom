package thaumcraft.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * Server-side hooks on each Arcane Bellows pulse, for addons whose own devices take air from bellows (another mod's
 * forges, say). An enabled bellows pulses every 2 ticks; each pulse first nudges a vanilla furnace in front of it as
 * Thaumcraft always has, then runs these listeners. Nothing changes without a listener.
 */
public final class BellowsHooks {

	@FunctionalInterface
	public interface PulseListener {
		/** @param facing the way the bellows blows: the block it feeds is {@code bellowsPos.relative(facing)} */
		void onPulse(ServerLevel level, BlockPos bellowsPos, Direction facing);
	}

	private static final List<PulseListener> LISTENERS = new CopyOnWriteArrayList<>();

	private BellowsHooks() {
	}

	public static void addPulseListener(PulseListener listener) {
		LISTENERS.add(listener);
	}

	public static boolean hasPulseListeners() {
		return !LISTENERS.isEmpty();
	}

	/** Runs every registered listener for one pulse - called from the bellows' ticker. */
	public static void pulse(ServerLevel level, BlockPos bellowsPos, Direction facing) {
		for (PulseListener listener : LISTENERS) {
			listener.onPulse(level, bellowsPos, facing);
		}
	}
}
