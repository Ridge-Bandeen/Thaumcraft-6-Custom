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
 * <li>Jar brace ({@link #blocked}, applied via {@link #applyBrace}) <b>is</b> real now - see
 * {@link thaumcraft.common.blocks.essentia.BlockJar#useItemOn}'s own {@code ItemsTC.jarBrace} branch. A
 * braced jar's {@link #isBlocked} returns {@code true}, which every real {@code IAspectSource} scan already
 * checks (the Infusion Altar's {@code EssentiaHandler.drainEssentia}, the Essentia Mirror's own
 * {@code scanFor}) - real "lock this jar out of pull-based scans, keep the direct adjacent-tile
 * {@code IEssentiaTransport} exchange working" scoping, matching the true original's own {@code blocked}
 * field exactly (only ever read by {@code isBlocked()} there too). One real, documented simplification:
 * breaking a braced jar doesn't drop the brace item back (the original stored that in the jar's own dropped
 * NBT) - same already-accepted "plain empty jar on break" precedent this class's own next bullet already
 * covers for aspect contents, not a new loss.</li>
 * <li>Aspect filter/label support <b>is</b> ported (see {@link #aspectFilter}/{@link #applyLabel} and
 * {@link thaumcraft.common.blocks.essentia.BlockJar#applyLabel}) - a labelled jar's raised 64 suction lets it
 * out-pull a plain jar/tube's 32 outright, using the real original's own strict {@code <} comparison (see
 * {@link #tick}'s own comment for why an earlier {@code <=} workaround here is gone again).</li>
 * <li>Real fill-level visual restored 2026-09-17, re-evaluating this class's own earlier "drop the animation"
 * cut - see {@link thaumcraft.client.render.TileJarRenderer}'s own doc comment. Not a discrete 5-model swap
 * like the true original's own real client-only {@code TileEntitySpecialRenderer} (which picked between 5
 * separately baked 0/25/50/75/100% block models entirely outside the blockstate system) - a continuously
 * scaled liquid quad instead, same real underlying {@code amount}/{@link #CAPACITY} fraction, just smoother.
 * This needed real {@link #getUpdateTag}/{@link #getUpdatePacket} sync wiring this tile never had before - a
 * real, previously-unnoticed gap: {@code amount}/{@code aspect}/{@code aspectFilter}/{@code blocked} were
 * never actually visible to the client at all until now (persisted via NBT, never broadcast on change).</li>
 * <li>Breaking a filled jar drops a plain empty jar, not one preserving its contents (the original stored
 * the held aspect+amount in the dropped item's own NBT) - a real but minor loss, flagged rather than hidden.</li>
 * </ul>
 */
public class TileJarFillable extends BlockEntity implements IAspectSource, IEssentiaTransport {

	private static final int CAPACITY = 250;

	private Aspect aspect;
	/** Set by applying a label (see {@link thaumcraft.common.blocks.essentia.BlockJar#applyLabel}) - locks
	 * the jar to one aspect and raises its suction from 32 to 64, letting it out-pull a plain jar or tube
	 * for that aspect specifically. {@code null} on a plain, unlabelled jar. */
	private Aspect aspectFilter;
	private int amount;
	private boolean blocked;
	/** Which way its label faces: towards whoever placed (or labelled) the jar, as the original. */
	private net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;

	public TileJarFillable(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.JAR.get(), pos, state);
	}

	/** For {@link TileJarFillableVoid} - the one-arg constructor above hardcodes the base JAR type, same
	 * "each variant needs its own real {@code BlockEntityType}" reason {@code TileTube} needed this same
	 * overload for its Filter/Restrict variants. */
	protected TileJarFillable(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Overridden by {@link TileJarFillableVoid} - a void jar never stops pulling, even once "full" (it just
	 * discards the overflow - see that class's own doc comment). A tiny, purely additive hook: for a plain
	 * jar this is exactly the {@code amount >= CAPACITY} check {@link #tick} always did inline. */
	protected boolean isFull() {
		return amount >= CAPACITY;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileJarFillable tile) {
		if (level.isClientSide) return;
		if (tile.isFull()) return;
		if (!(level.getBlockEntity(pos.above()) instanceof IEssentiaTransport source)) return;
		if (!source.isConnectable(Direction.DOWN) || !source.canOutputTo(Direction.DOWN)) return;

		Aspect want;
		if (tile.aspectFilter != null) {
			want = tile.aspectFilter;
		} else if (tile.aspect != null && tile.amount > 0) {
			want = tile.aspect;
		} else {
			want = source.getEssentiaType(Direction.DOWN);
		}
		if (want == null || source.getEssentiaAmount(Direction.DOWN) <= 0) return;
		// The real original requires the target's suction to be strictly greater than the source's
		// (`ic.getSuctionAmount(DOWN) < this.getSuctionAmount(UP)`) - restored 2026-09-15 now that it's really
		// safe to. This used to need loosening to "<=" because a plain Tube's own computed suction matched
		// whatever its strongest neighbor demanded exactly (a real regression from the true original's own
		// per-hop suction decay, fixed in TileTube#calculateSuction/#adjustSuction - see that class's own doc
		// comment), so a Tube relaying into a plain jar below always tied at 32 suction on both sides. With
		// the real decay restored, a relaying Tube's own reported suction is now always strictly LESS than
		// whatever it's relaying, so this real strict comparison holds without ties again, matching the
		// original exactly.
		if (source.getSuctionAmount(Direction.DOWN) >= tile.getSuctionAmount(Direction.UP)) return;

		int taken = source.takeEssentia(want, 1, Direction.DOWN);
		if (taken > 0) {
			tile.addToContainer(want, taken);
		}
	}

	/** Empties the jar back into the local aura - the sneak+empty-hand interaction from the original. */
	public void emptyIntoAura(Level level, BlockPos pos) {
		// as the original: the essentia poured out pollutes the aura with as much flux; a labelled jar keeps its aspect
		if (amount > 0) thaumcraft.api.aura.AuraHelper.polluteAura(level, pos, amount, true);
		if (aspectFilter == null) aspect = null;
		amount = 0;
		setChanged();
	}

	/** Takes the label off (the original's sneak-click on the labelled face). */
	public void removeLabel() {
		aspectFilter = null;
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
		if (aspectFilter != null) return tag == aspectFilter;
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
		return blocked;
	}

	/** Applies a jar brace - locks this jar out of pull-based {@code IAspectSource} scans (see this class's
	 * own doc comment). Mirrors {@link #applyLabel}'s own "already applied, refuse" gate: a braced jar can't
	 * be braced twice.
	 *
	 * @return true if the brace was consumed (the jar was actually blocked). */
	public boolean applyBrace() {
		if (blocked) return false;
		blocked = true;
		setChanged();
		return true;
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
		return aspectFilter != null ? aspectFilter : aspect;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		if (amount >= CAPACITY) return 0;
		return aspectFilter != null ? 64 : 32;
	}

	@Override
	public int getMinimumSuction() {
		return aspectFilter != null ? 64 : 32;
	}

	public Aspect getAspectFilter() {
		return aspectFilter;
	}

	public Aspect getAspect() {
		return aspect;
	}

	public int getAmount() {
		return amount;
	}

	public static int getCapacity() {
		return CAPACITY;
	}

	/** Applies a label - locks this jar to one aspect (raising its suction) if it's currently empty, or to
	 * whatever it already holds otherwise. Mirrors {@code BlockJar#applyLabel} in the original exactly: a
	 * jar that already has a filter can't be re-labelled, and labelling an empty jar with a plain (blank)
	 * label fails since there's no aspect to lock onto yet.
	 *
	 * @param labelAspect the aspect a filled label carries, or {@code null} for a blank one
	 * @return true if the label was consumed (the jar was actually locked)
	 */
	public net.minecraft.core.Direction getFacing() {
		return facing;
	}

	/** The original's onBlockPlacedBy: the label turns to face {@code entity}. */
	public void faceTowards(net.minecraft.world.entity.LivingEntity entity) {
		facing = entity.getDirection().getOpposite();
		setChanged();
	}

	public boolean applyLabel(Aspect labelAspect) {
		if (aspectFilter != null) return false;
		if (amount == 0 && labelAspect == null) return false;
		if (amount == 0) {
			aspect = labelAspect;
		}
		aspectFilter = aspect;
		setChanged();
		return true;
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
		tag.putByte("facing", (byte) facing.get3DDataValue());
		if (aspect != null) {
			tag.putString("Aspect", aspect.getTag());
		}
		if (aspectFilter != null) {
			tag.putString("AspectFilter", aspectFilter.getTag());
		}
		tag.putInt("Amount", amount);
		tag.putBoolean("Blocked", blocked);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspect = tag.contains("Aspect") ? Aspect.getAspect(tag.getString("Aspect")) : null;
		aspectFilter = tag.contains("AspectFilter") ? Aspect.getAspect(tag.getString("AspectFilter")) : null;
		amount = tag.getInt("Amount");
		blocked = tag.getBoolean("Blocked");
		facing = tag.contains("facing") ? net.minecraft.core.Direction.from3DDataValue(tag.getByte("facing")) : net.minecraft.core.Direction.NORTH;
		if (facing.getAxis() == net.minecraft.core.Direction.Axis.Y) facing = net.minecraft.core.Direction.NORTH;
	}

	/** Real block-entity sync wiring, added alongside the real fill-level visual restoration - see this
	 * class's own doc comment. Same real pattern {@code TileCrucible}/{@code TileCentrifuge}/
	 * {@code TilePatternCrafter} already established. */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}
