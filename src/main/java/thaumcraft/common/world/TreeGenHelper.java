package thaumcraft.common.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the original's hand-written tree generators ({@link GreatwoodTreeFeature}, {@link SilverwoodTreeFeature}) need
 * from 1.21 that 1.12 gave them for free. Placing leaves by hand leaves them at distance 7 (1.12 leaves only checked
 * for logs when they decayed; 1.21's store the distance), so the placed leaves are re-measured from the placed logs
 * afterwards, as vanilla's own TreeFeature does. Writes outside a worldgen region are dropped rather than logged as
 * errors, and reads outside it count as obstructions, like the original's try/catch around every lookup.
 */
final class TreeGenHelper {

	private final WorldGenLevel level;
	private final Set<BlockPos> logs = new HashSet<>();
	private final Set<BlockPos> leaves = new HashSet<>();

	TreeGenHelper(WorldGenLevel level) {
		this.level = level;
	}

	boolean canAccess(BlockPos pos) {
		return !(level instanceof WorldGenRegion region) || region.ensureCanWrite(pos);
	}

	/** Null when the position can't be read (outside the region being generated). */
	BlockState get(BlockPos pos) {
		if (level instanceof WorldGenRegion region && !region.ensureCanWrite(pos)) return null;
		try {
			return level.getBlockState(pos);
		} catch (RuntimeException e) {
			return null;
		}
	}

	void set(BlockPos pos, BlockState state) {
		if (!canAccess(pos)) return;
		BlockPos key = pos.immutable();
		level.setBlock(key, state, Block.UPDATE_ALL);
		if (state.getBlock() instanceof LeavesBlock) {
			leaves.add(key);
			logs.remove(key);
		} else {
			logs.add(key);
			leaves.remove(key);
		}
	}

	/** Vanilla TreeFeature#updateLeaves: breadth-first from every placed log through the placed leaves. */
	void finish() {
		Map<BlockPos, Integer> distance = new HashMap<>();
		Deque<BlockPos> frontier = new ArrayDeque<>();
		for (BlockPos log : logs) {
			for (Direction dir : Direction.values()) {
				BlockPos next = log.relative(dir);
				if (leaves.contains(next) && !distance.containsKey(next)) {
					distance.put(next, 1);
					frontier.add(next);
				}
			}
		}
		while (!frontier.isEmpty()) {
			BlockPos pos = frontier.poll();
			int d = distance.get(pos);
			if (d >= LeavesBlock.DECAY_DISTANCE - 1) continue;
			for (Direction dir : Direction.values()) {
				BlockPos next = pos.relative(dir);
				if (leaves.contains(next) && !distance.containsKey(next)) {
					distance.put(next, d + 1);
					frontier.add(next);
				}
			}
		}
		for (Map.Entry<BlockPos, Integer> e : distance.entrySet()) {
			BlockState state = level.getBlockState(e.getKey());
			if (state.getBlock() instanceof LeavesBlock) {
				level.setBlock(e.getKey(), state.setValue(LeavesBlock.DISTANCE, e.getValue()), 19);
			}
		}
	}
}
