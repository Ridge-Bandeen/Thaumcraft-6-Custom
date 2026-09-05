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
 * Real, deliberate cut: the original's client-only {@code surroundwithsparkles} per-tick particle trail is
 * dropped, same established cut as every other device this port has shipped.
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
