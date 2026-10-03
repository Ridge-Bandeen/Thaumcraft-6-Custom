package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.items.casters.foci.FocusEffectRift;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileHole} - the temporary, walkable "tunnel" block {@link
 * FocusEffectRift} punches through solid terrain. Remembers the real block it replaced ({@code oldBlock},
 * restored once the countdown expires) and, for the real block actually struck by the cast (the one with a
 * real {@code direction}), propagates the tunnel one block further and opens a real 3x3 burst of single-tick
 * holes in the perpendicular plane - both exactly the original's own real recursive {@code
 * FocusEffectRift#createHole} calls.
 *
 * As the original, on the client its edges glitter blue ({@link #surroundWithSparkles}, twice a tick) wherever the
 * hole meets solid blocks; everything else happens on the server.
 */
public class TileHole extends BlockEntity {

	public BlockState oldBlock = Blocks.AIR.defaultBlockState();
	public short countdown = 0;
	public short countdownMax = 120;
	public byte count = 0;
	public Direction direction;

	public TileHole(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.HOLE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileHole tile) {
		if (level.isClientSide) {
			for (int a = 0; a < 2; a++) tile.surroundWithSparkles(level, pos);
			return;
		}
		if (tile.countdown == 0 && tile.count > 1 && tile.direction != null) {
			Direction.Axis axis = tile.direction.getAxis();
			for (int a = 0; a < 9; a++) {
				if (a / 3 != 1 || a % 3 != 1) {
					BlockPos burstPos = switch (axis) {
						case Y -> pos.offset(-1 + a / 3, 0, -1 + a % 3);
						case Z -> pos.offset(-1 + a / 3, -1 + a % 3, 0);
						case X -> pos.offset(0, -1 + a / 3, -1 + a % 3);
					};
					FocusEffectRift.createHole(level, burstPos, null, (byte) 1, tile.countdownMax);
				}
			}
			if (!FocusEffectRift.createHole(level, pos.relative(tile.direction.getOpposite()), tile.direction, (byte) (tile.count - 1), tile.countdownMax)) {
				tile.count = 0;
			}
		}

		tile.countdown++;
		if (tile.countdown % 20 == 0) tile.setChanged();
		if (tile.countdown >= tile.countdownMax) {
			level.setBlock(pos, tile.oldBlock, 3);
		}
	}

	/** The original's surroundwithsparkles: a sparkle on every edge where an open face of the hole meets a solid block. */
	private void surroundWithSparkles(Level level, BlockPos pos) {
		for (Direction d1 : Direction.values()) {
			BlockState b1 = level.getBlockState(pos.relative(d1));
			if (b1.is(thaumcraft.api.blocks.BlocksTC.hole.get()) || b1.isSolidRender(level, pos.relative(d1))) continue;
			for (Direction d2 : Direction.values()) {
				if (d1.getAxis() == d2.getAxis()) continue;
				if (!level.getBlockState(pos.relative(d2)).isSolidRender(level, pos.relative(d2))
						&& !level.getBlockState(pos.relative(d1).relative(d2)).isSolidRender(level, pos.relative(d1).relative(d2))) continue;
				float sx = 0.5f * d1.getStepX();
				float sy = 0.5f * d1.getStepY();
				float sz = 0.5f * d1.getStepZ();
				if (sx == 0.0f) sx = 0.5f * d2.getStepX();
				if (sy == 0.0f) sy = 0.5f * d2.getStepY();
				if (sz == 0.0f) sz = 0.5f * d2.getStepZ();
				sx = sx == 0.0f ? level.random.nextFloat() : sx + 0.5f;
				sy = sy == 0.0f ? level.random.nextFloat() : sy + 0.5f;
				sz = sz == 0.0f ? level.random.nextFloat() : sz + 0.5f;
				thaumcraft.client.fx.ClientFxHandlers.sparkle(level, pos.getX() + sx, pos.getY() + sy, pos.getZ() + sz, 0.25f, 0.25f, 1.0f);
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("oldBlock", NbtUtils.writeBlockState(oldBlock));
		tag.putShort("countdown", countdown);
		tag.putShort("countdownMax", countdownMax);
		tag.putByte("count", count);
		tag.putByte("direction", direction == null ? -1 : (byte) direction.ordinal());
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("oldBlock") && level != null) {
			oldBlock = NbtUtils.readBlockState(level.holderLookup(Registries.BLOCK), tag.getCompound("oldBlock"));
		}
		countdown = tag.getShort("countdown");
		countdownMax = tag.getShort("countdownMax");
		count = tag.getByte("count");
		byte db = tag.getByte("direction");
		direction = db >= 0 && db < Direction.values().length ? Direction.values()[db] : null;
	}
}
