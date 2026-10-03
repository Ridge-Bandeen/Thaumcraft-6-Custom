package thaumcraft.common.lib.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * The original's {@code BlockUtils.findBlocks}: from a block, walk to ever more distant blocks of the same kind - each
 * step scanning the cube of {@code reach} around the current one (west to east, top down, north to south) and
 * moving to the first match farther from the start than any so far - until no step leads farther or it strays more
 * than 24 blocks sideways or 48 up or down. Burrowing tools and the Lumber seal break where it ends: the far end of a
 * vein, the top of a tree.
 */
public final class BlockSearch {

	private BlockSearch() {
	}

	public static BlockPos findFurthest(Level level, BlockPos start, Block match, int reach) {
		BlockPos last = start;
		double lastDistance = 0.0;
		while (true) {
			boolean moved = false;
			scan:
			for (int xx = -reach; xx <= reach; xx++) {
				for (int yy = reach; yy >= -reach; yy--) {
					for (int zz = -reach; zz <= reach; zz++) {
						BlockPos p = last.offset(xx, yy, zz);
						if (Math.abs(p.getX() - start.getX()) > 24 || Math.abs(p.getY() - start.getY()) > 48 || Math.abs(p.getZ() - start.getZ()) > 24) {
							return last;
						}
						var state = level.getBlockState(p);
						if (state.is(match) && state.getDestroySpeed(level, p) >= 0.0f) {
							double xd = p.getX() - start.getX();
							double yd = p.getY() - start.getY();
							double zd = p.getZ() - start.getZ();
							double d = xd * xd + yd * yd + zd * zd;
							if (d > lastDistance) {
								lastDistance = d;
								last = p;
								moved = true;
								break scan;
							}
						}
					}
				}
			}
			if (!moved) return last;
		}
	}
}
