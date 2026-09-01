package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileLampGrowth} - a charge-driven plant-growth accelerator. Every tick
 * (while not redstone-powered and holding charge), picks a random column within a 6-block radius disc above
 * itself and searches it top-down for a growable plant to advance.
 *
 * The original tracked its own hand-picked whitelist of "standard crops" via {@code CropUtils}
 * (block+metadata strings registered per-crop) and forced growth by scheduling an early random tick
 * ({@code World#scheduleUpdate}), leaving the actual growth decision to that block's own random-tick
 * handler. This port uses vanilla's real {@link BonemealableBlock} interface instead - the same one bonemeal
 * itself uses - restricted to {@link BonemealableBlock.Type#GROWER} (excludes spreaders like grass/vines,
 * which aren't "growth" in the crop sense this lamp is themed around). This is a genuine improvement, not
 * just a simplification: it works correctly on every real growable block in the game (any crop, sapling, or
 * modded block correctly implementing the interface), not just a fixed, hand-maintained list, and growth is
 * a direct, guaranteed stage advance via {@link BonemealableBlock#performBonemeal} rather than a "might not
 * actually grow this attempt" scheduled random tick.
 *
 * {@link IEssentiaTransport} ports over mechanically from the original - see
 * {@link thaumcraft.common.tiles.essentia.TileJarFillable}'s own doc comment for why no physical tube
 * network is needed for this direct, single-adjacent-tile draw.
 *
 * Simplified vs the original: no particle/sound FX on growth (client FX, dropped like everywhere else this
 * session - the original's own mist-particle trigger existed purely to mark "something changed here" for
 * players standing nearby).
 */
public class TileLampGrowth extends BlockEntity implements IEssentiaTransport {

	private static final int MAX_CHARGES = 20;
	private static final int SEARCH_RADIUS = 6;

	private boolean reserve;
	private int charges;
	private int drawDelay;
	private final List<BlockPos> checklist = new ArrayList<>();

	public TileLampGrowth(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.LAMP_GROWTH.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileLampGrowth tile) {
		if (level.isClientSide) return;
		boolean powered = level.hasNeighborSignal(pos);

		if (tile.charges <= 0) {
			if (tile.reserve) {
				tile.charges = MAX_CHARGES;
				tile.reserve = false;
				tile.setChanged();
			} else if (tile.drawEssentia(level, pos, state)) {
				tile.charges = MAX_CHARGES;
				tile.setChanged();
			}
			boolean enabled = state.getValue(BlockStateProperties.ENABLED);
			if (tile.charges <= 0) {
				if (enabled) {
					level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, false), 3);
				}
			} else if (!powered && !enabled) {
				level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, true), 3);
			}
		}

		if (!tile.reserve && tile.drawEssentia(level, pos, state)) {
			tile.reserve = true;
		}

		if (!powered && tile.charges > 0) {
			tile.tryGrowSomething(level, pos);
		}
	}

	private void tryGrowSomething(Level level, BlockPos pos) {
		if (checklist.isEmpty()) {
			for (int a = -SEARCH_RADIUS; a <= SEARCH_RADIUS; a++) {
				for (int b = -SEARCH_RADIUS; b <= SEARCH_RADIUS; b++) {
					checklist.add(pos.offset(a, SEARCH_RADIUS, b));
				}
			}
			for (int i = checklist.size() - 1; i > 0; i--) {
				int j = level.random.nextInt(i + 1);
				Collections.swap(checklist, i, j);
			}
		}
		BlockPos column = checklist.remove(0);
		double radiusSq = (double) SEARCH_RADIUS * SEARCH_RADIUS;
		for (int y = column.getY(); y >= pos.getY() - SEARCH_RADIUS; y--) {
			BlockPos bp = new BlockPos(column.getX(), y, column.getZ());
			if (bp.distSqr(pos) >= radiusSq) continue;
			BlockState target = level.getBlockState(bp);
			if (target.isAir()) continue;
			if (!(target.getBlock() instanceof BonemealableBlock growable) || growable.getType() != BonemealableBlock.Type.GROWER) continue;
			if (!growable.isValidBonemealTarget(level, bp, target)) continue;
			growable.performBonemeal((ServerLevel) level, level.random, bp, target);
			charges--;
			setChanged();
			return;
		}
	}

	private boolean drawEssentia(Level level, BlockPos pos, BlockState state) {
		if (++drawDelay % 5 != 0) return false;
		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (!(level.getBlockEntity(pos.relative(facing)) instanceof IEssentiaTransport source)) return false;
		if (!source.canOutputTo(facing.getOpposite())) return false;
		if (source.getSuctionAmount(facing.getOpposite()) >= getSuctionAmount(facing)) return false;
		return source.takeEssentia(Aspect.PLANT, 1, facing.getOpposite()) == 1;
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING);
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING);
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return false;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return Aspect.PLANT;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING) && (!reserve || charges <= 0) ? 128 : 0;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return null;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return 0;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		return 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		return 0;
	}

	/** Test hook - lets a GameTest seed charge directly rather than waiting out real essentia draw timing. */
	public void setChargesForTest(int value) {
		this.charges = value;
		setChanged();
	}

	public int getCharges() {
		return charges;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("Reserve", reserve);
		tag.putInt("Charges", charges);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		reserve = tag.getBoolean("Reserve");
		charges = tag.getInt("Charges");
	}
}
