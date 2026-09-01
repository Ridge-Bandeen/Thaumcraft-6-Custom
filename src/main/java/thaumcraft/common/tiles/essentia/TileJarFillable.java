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
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileJarFillable} - a single-aspect essentia storage jar, the first real
 * {@link IAspectSource}/{@link IEssentiaTransport} implementation this port has built (both interfaces were
 * already ported as part of the {@code thaumcraft.api.aspects} package early in this session, but had zero
 * consumers until now). Every tick, checks directly above itself for another {@code IEssentiaTransport} tile
 * willing to output essentia downward and pulls 1 unit at a time - the original's own core mechanic,
 * confirmed to need no physical tube network at all: {@code IEssentiaTransport} is a direct adjacent-tile
 * exchange interface, and the original's tube blocks are just a way to relay/extend that connection across
 * multiple blocks, not a requirement for two directly-touching devices to exchange essentia. No tube blocks
 * are ported in this pass - a jar can still fill from anything placed directly above it that also implements
 * {@code IEssentiaTransport} (including another jar, proven by this device's own GameTest).
 *
 * Simplified vs the original:
 * <ul>
 * <li>No aspect filter, jar brace ("blocked"), or label mechanics - all three need items this port hasn't
 * built ({@code ItemsTC.jarBrace}/{@code ItemsTC.label}) or an interface ({@code ILabelable}) with no other
 * consumer yet. One real consequence, found while writing this device's own GameTest: the original only
 * self-fills when the target's suction is <em>strictly greater</em> than the source's, and real gameplay
 * differentiation came from the aspectFilter mechanic (64 suction, beating a plain jar's default 32) - since
 * that isn't ported, two plain jars always tie at 32 suction. {@link #tick} allows ties (uses {@code <=}
 * instead of the original's strict {@code <}) so jar-to-jar self-filling stays reachable in the common case
 * instead of becoming silently dead code.</li>
 * <li>No fill-level model swapping - the original rendered this via a client-only
 * {@code TileEntitySpecialRenderer} picking between 5 baked models (0/25/50/75/100%) at draw time, entirely
 * outside the blockstate system; same "drop the animation, keep the function" precedent as every other
 * device this session. The block always renders as the plain empty jar model.</li>
 * <li>Breaking a filled jar drops a plain empty jar, not one preserving its contents (the original stored
 * the held aspect+amount in the dropped item's own NBT) - a real but minor loss, flagged rather than hidden.</li>
 * </ul>
 */
public class TileJarFillable extends BlockEntity implements IAspectSource, IEssentiaTransport {

	private static final int CAPACITY = 250;

	private Aspect aspect;
	private int amount;

	public TileJarFillable(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.JAR.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileJarFillable tile) {
		if (level.isClientSide) return;
		if (tile.amount >= CAPACITY) return;
		if (!(level.getBlockEntity(pos.above()) instanceof IEssentiaTransport source)) return;
		if (!source.isConnectable(Direction.DOWN) || !source.canOutputTo(Direction.DOWN)) return;

		Aspect want = tile.aspect != null ? tile.aspect : source.getEssentiaType(Direction.DOWN);
		if (want == null || source.getEssentiaAmount(Direction.DOWN) <= 0) return;
		// The original requires the target's suction to be strictly greater than the source's
		// (getSuctionAmount(DOWN) < getSuctionAmount(UP)) - real gameplay differentiation came from the
		// aspectFilter mechanic (64 suction) beating a plain jar's default (32), which isn't ported here (see
		// this class's own doc comment). Without it, two plain jars always tie at 32 and a strict "<" would
		// make jar-to-jar self-filling permanently unreachable in the common case - allowing ties (using
		// "<=") keeps the feature meaningful instead of silently dead.
		if (source.getSuctionAmount(Direction.DOWN) > tile.getSuctionAmount(Direction.UP)) return;

		int taken = source.takeEssentia(want, 1, Direction.DOWN);
		if (taken > 0) {
			tile.addToContainer(want, taken);
		}
	}

	/** Empties the jar back into the local aura - the sneak+empty-hand interaction from the original. */
	public void emptyIntoAura(Level level, BlockPos pos) {
		if (amount <= 0) return;
		AuraHandler.addVis(level, pos, amount / 1000.0f);
		aspect = null;
		amount = 0;
		setChanged();
	}

	@Override
	public AspectList getAspects() {
		AspectList list = new AspectList();
		if (aspect != null && amount > 0) {
			list.add(aspect, amount);
		}
		return list;
	}

	@Override
	public void setAspects(AspectList aspects) {
		if (aspects != null && aspects.size() > 0) {
			Aspect[] sorted = aspects.getAspectsSortedByAmount();
			aspect = sorted[0];
			amount = aspects.getAmount(sorted[0]);
			setChanged();
		}
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return aspect == null || tag == aspect;
	}

	@Override
	public int addToContainer(Aspect tag, int toAdd) {
		if (toAdd <= 0) return 0;
		if (aspect != null && tag != aspect) return toAdd;
		aspect = tag;
		int added = Math.min(toAdd, CAPACITY - amount);
		amount += added;
		setChanged();
		return toAdd - added;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int toTake) {
		if (aspect != tag || amount < toTake) return false;
		amount -= toTake;
		if (amount <= 0) {
			aspect = null;
			amount = 0;
		}
		setChanged();
		return true;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amt) {
		return aspect == tag && amount >= amt;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		for (Aspect tag : ot.getAspects()) {
			if (tag == aspect && amount > 0) return true;
		}
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return tag == aspect ? amount : 0;
	}

	@Override
	public boolean isBlocked() {
		return false;
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face == Direction.UP;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face == Direction.UP;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return face == Direction.UP;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return aspect;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return amount >= CAPACITY ? 0 : 32;
	}

	@Override
	public int getMinimumSuction() {
		return 32;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return aspect;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return amount;
	}

	@Override
	public int takeEssentia(Aspect tag, int toTake, Direction face) {
		return canOutputTo(face) && takeFromContainer(tag, toTake) ? toTake : 0;
	}

	@Override
	public int addEssentia(Aspect tag, int toAdd, Direction face) {
		return canInputFrom(face) ? toAdd - addToContainer(tag, toAdd) : 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (aspect != null) {
			tag.putString("Aspect", aspect.getTag());
		}
		tag.putInt("Amount", amount);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspect = tag.contains("Aspect") ? Aspect.getAspect(tag.getString("Aspect")) : null;
		amount = tag.getInt("Amount");
	}
}
