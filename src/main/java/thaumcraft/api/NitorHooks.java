package thaumcraft.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side tick hooks on placed Nitor, for addons that make it do something in the world (a heat source for
 * another mod's devices, say). Thaumcraft's own Nitor does nothing on the server, so a placed Nitor only ticks there
 * at all once a hook is registered. Register before worlds load (from {@link ThaumcraftSetupEvent} or earlier): the
 * hooks are read when a Nitor's ticker is created.
 */
public final class NitorHooks {

	@FunctionalInterface
	public interface ServerTicker {
		void tick(Level level, BlockPos pos, BlockState state, BlockEntity nitor);
	}

	private static final List<ServerTicker> TICKERS = new CopyOnWriteArrayList<>();

	private NitorHooks() {
	}

	public static void addServerTicker(ServerTicker ticker) {
		TICKERS.add(ticker);
	}

	public static boolean hasServerTickers() {
		return !TICKERS.isEmpty();
	}

	/** Runs every registered hook for one Nitor - called from its ticker. */
	public static void tick(Level level, BlockPos pos, BlockState state, BlockEntity nitor) {
		for (ServerTicker ticker : TICKERS) {
			ticker.tick(level, pos, state, nitor);
		}
	}
}
