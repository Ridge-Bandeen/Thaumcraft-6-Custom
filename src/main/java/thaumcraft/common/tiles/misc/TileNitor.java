package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.client.fx.ClientFxHandlers;
import thaumcraft.common.blocks.misc.BlockNitor;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Nitor's client-side flame. The block itself is invisible (as in the original); this tile's per-tick client
 * update draws a tinted flame every tick and the white core every 10 ticks, exactly as the original's
 * {@code TileNitor#update}. It exists because vanilla's random {@code animateTick} fires far too rarely (about
 * once per 40 ticks per block) for a 10-15 tick flame - the port's earlier version, which dropped the tile, showed
 * a stretched cross-model plus a barely-flickering vanilla flame.
 */
public class TileNitor extends BlockEntity {

	private int count;

	public TileNitor(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.NITOR.get(), pos, state);
	}

	public static void clientTick(Level level, BlockPos pos, BlockState state, TileNitor tile) {
		if (!(state.getBlock() instanceof BlockNitor nitor)) return;
		var r = level.random;
		ClientFxHandlers.spawnNitorFlame(level,
				pos.getX() + 0.5 + r.nextGaussian() * 0.025, pos.getY() + 0.45 + r.nextGaussian() * 0.025, pos.getZ() + 0.5 + r.nextGaussian() * 0.025,
				r.nextGaussian() * 0.0025, r.nextFloat() * 0.06, r.nextGaussian() * 0.0025, nitor.dye.getTextureDiffuseColor() & 0xFFFFFF);
		if (tile.count++ % 10 == 0) {
			ClientFxHandlers.spawnNitorCore(level, pos.getX() + 0.5, pos.getY() + 0.49, pos.getZ() + 0.5);
		}
	}
}
