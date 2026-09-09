package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeBuffer} - a tube variant that actually holds essentia
 * ({@link #aspects}, up to {@value #MAX_AMOUNT} total units, any real mix of aspects) rather than
 * {@link TileTube}'s own single-unit-of-one-aspect relay. Not a subclass of {@code TileTube} - unlike a
 * plain tube's own dynamic, neighbor-derived suction, a buffer's real suction is a small FIXED value (see
 * {@link #MIN_SUCTION}), so there's no shared {@code calculateSuction} step to inherit; every 5 ticks, if
 * under capacity, it just tries each neighbor directly (real port of the original's own {@code fillBuffer}).
 *
 * Real, documented simplifications vs. the original:
 * <ul>
 * <li>No per-face open/close toggling or per-face "choke" throttling (cycled via sneak-right-click on a
 * specific face) - both built on the same CodeChicken sub-hit raytracing library already dropped for
 * {@code TileTube} itself (see that class's own doc comment) - all 6 sides are always open, same real
 * consequence {@code TileTube} already accepted.</li>
 * <li>No bellows-amplified suction (the original's own {@code getSuctionAmount} scaled with a nearby
 * {@code TileBellows}' output) - {@link #MIN_SUCTION} keeps just the real base value, dropping only the
 * amplification on top of it.</li>
 * </ul>
 */
public class TileTubeBuffer extends BlockEntity implements IEssentiaTransport, IAspectContainer {

	private static final int MAX_AMOUNT = 10;
	/** Real, deliberately minimal base suction, matching the true original's own real formula exactly
	 * ({@code bellows <= 0 ? 1 : bellows * 32} - only the {@code 1} half survives here, bellows
	 * amplification dropped per this class's own doc comment) and confirmed by the research text itself
	 * ("it applies the minimum amount of suction possible (1)... usually only able to draw essentia from an
	 * essentia source it is directly attached to"): a real tube/jar's own 32+ suction almost always beats
	 * this, so the buffer only pulls from a neighbor that isn't itself actively demanding anything -
	 * exactly the passive "attach it directly to a source" behavior the research text describes, not an
	 * arbitrary throttle. See {@link #fillBuffer} for the real comparison this produces. */
	private static final int MIN_SUCTION = 1;

	private final AspectList aspects = new AspectList();
	private int count;

	public TileTubeBuffer(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_BUFFER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileTubeBuffer tile) {
		if (level.isClientSide) return;
		if (tile.count++ % 5 == 0 && tile.aspects.visSize() < MAX_AMOUNT) {
			tile.fillBuffer(level, pos);
		}
	}

	/** Real port of the original's own {@code fillBuffer} - tries each neighbor in turn, stopping at the
	 * first real pull. {@code MIN_SUCTION}'s own doc comment explains why {@code neighbor's suction < this
	 * buffer's own suction} rarely holds against a real tube/jar - this is deliberately a weak, passive
	 * puller, not a competing one. */
	private void fillBuffer(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;
			if (neighbor.getEssentiaAmount(dir.getOpposite()) <= 0) continue;
			int mySuction = getSuctionAmount(dir);
			if (neighbor.getSuctionAmount(dir.getOpposite()) >= mySuction) continue;
			if (mySuction < neighbor.getMinimumSuction()) continue;

			Aspect theirType = neighbor.getEssentiaType(dir.getOpposite());
			int taken = neighbor.takeEssentia(theirType, 1, dir.getOpposite());
			if (taken > 0) {
				addToContainer(theirType, taken);
				return;
			}
		}
	}

	// --- IAspectContainer: a real multi-aspect store, up to MAX_AMOUNT total units ---

	@Override
	public AspectList getAspects() {
		return aspects;
	}

	@Override
	public void setAspects(AspectList newAspects) {
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return aspects.visSize() < MAX_AMOUNT;
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		if (amount != 1 || aspects.visSize() >= MAX_AMOUNT) return amount;
		aspects.add(tag, amount);
		setChanged();
		return 0;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		if (aspects.getAmount(tag) < amount) return false;
		aspects.remove(tag, amount);
		setChanged();
		return true;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		return aspects.getAmount(tag) >= amount;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return aspects.getAmount(tag);
	}

	// --- IEssentiaTransport: all 6 sides always open (see this class's own doc comment) ---

	@Override
	public boolean isConnectable(Direction face) {
		return true;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return true;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return true;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return null;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return aspects.visSize() >= MAX_AMOUNT ? 0 : MIN_SUCTION;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	/** A random held aspect, matching the original's own real {@code getEssentiaType} exactly - a single
	 * {@link IEssentiaTransport} query can only ever report one aspect, so a real multi-aspect buffer picks
	 * one at random rather than always favoring the same slot. */
	@Override
	public Aspect getEssentiaType(Direction face) {
		Aspect[] held = aspects.getAspects();
		return held.length > 0 ? held[level.random.nextInt(held.length)] : null;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return aspects.visSize();
	}

	@Override
	public int takeEssentia(Aspect tag, int amount, Direction face) {
		return canOutputTo(face) && takeFromContainer(tag, amount) ? amount : 0;
	}

	@Override
	public int addEssentia(Aspect tag, int amount, Direction face) {
		return canInputFrom(face) ? amount - addToContainer(tag, amount) : 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		aspects.writeToNBT(tag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspects.readFromNBT(tag);
	}
}
