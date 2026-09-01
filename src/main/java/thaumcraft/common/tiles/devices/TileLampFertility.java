package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileLampFertility} - a charge-driven animal-breeding accelerator, same
 * charge/essentia shell as {@link TileLampGrowth} (see that class's own doc comment for why
 * {@code IEssentiaTransport} needs no physical tube network) but with its own threshold values (charges cap
 * at 10, not 20; disables at 1 charge or below; breeding costs 5 charges) and target search: every 300 ticks
 * (while holding charge and not redstone-powered), scans a 7-block radius for two same-species adult
 * {@link Animal}s not already in love and puts them in love, matching the original's exact algorithm.
 *
 * Real improvement over the original, not just a port: the original manually checked
 * {@code getGrowingAge() == 0 && !isInLove()} for "eligible to breed" - this uses {@link Animal#isBaby()}
 * (negated) the same way, but the adjacent {@link Animal#canFallInLove()} accessor (vanilla's own real
 * "not currently on love cooldown" check) is used in place of the original's simpler {@code !isInLove()},
 * correctly also excluding animals still on their post-breeding cooldown, not just currently-hearting ones.
 *
 * Simplified: no particle/sound FX (client FX, dropped like everywhere else this session).
 */
public class TileLampFertility extends BlockEntity implements IEssentiaTransport {

	private static final int MAX_CHARGES = 10;
	private static final int SEARCH_RADIUS = 7;
	private static final int BREED_INTERVAL = 300;

	private int charges;
	private int count;
	private int drawDelay;

	public TileLampFertility(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.LAMP_FERTILITY.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileLampFertility tile) {
		if (level.isClientSide) return;
		boolean powered = level.hasNeighborSignal(pos);

		if (tile.charges < MAX_CHARGES) {
			if (tile.drawEssentia(level, pos, state)) {
				tile.charges++;
				tile.setChanged();
			}
			boolean enabled = state.getValue(BlockStateProperties.ENABLED);
			if (tile.charges <= 1) {
				if (enabled) {
					level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, false), 3);
				}
			} else if (!powered && !enabled) {
				level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, true), 3);
			}
		}

		if (!powered && tile.charges > 1 && tile.count++ % BREED_INTERVAL == 0) {
			tile.tryBreedAnimals(level, pos);
		}
	}

	private void tryBreedAnimals(Level level, BlockPos pos) {
		AABB area = new AABB(pos).inflate(SEARCH_RADIUS);
		List<Animal> animals = level.getEntitiesOfClass(Animal.class, area);
		for (Animal candidate : animals) {
			if (candidate.isBaby() || !candidate.canFallInLove()) continue;
			List<Animal> sameSpecies = new ArrayList<>();
			for (Animal other : animals) {
				if (other.getClass().equals(candidate.getClass())) {
					sameSpecies.add(other);
				}
			}
			if (sameSpecies.size() > 9) continue;
			Animal partner = null;
			for (Animal other : sameSpecies) {
				if (other.isBaby() || !other.canFallInLove()) continue;
				if (partner != null) {
					charges -= 5;
					other.setInLove(null);
					partner.setInLove(null);
					setChanged();
					return;
				}
				partner = other;
			}
		}
	}

	private boolean drawEssentia(Level level, BlockPos pos, BlockState state) {
		if (++drawDelay % 5 != 0) return false;
		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (!(level.getBlockEntity(pos.relative(facing)) instanceof IEssentiaTransport source)) return false;
		if (!source.canOutputTo(facing.getOpposite())) return false;
		if (source.getSuctionAmount(facing.getOpposite()) >= getSuctionAmount(facing)) return false;
		return source.takeEssentia(Aspect.DESIRE, 1, facing.getOpposite()) == 1;
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
		return Aspect.DESIRE;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return face == getBlockState().getValue(BlockStateProperties.FACING) ? 128 - charges * 10 : 0;
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

	/** Test hook - lets a GameTest trigger a breeding attempt directly without waiting out the real
	 * 300-tick interval. */
	public void tryBreedAnimalsForTest(Level level, BlockPos pos) {
		tryBreedAnimals(level, pos);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Charges", charges);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		charges = tag.getInt("Charges");
	}
}
