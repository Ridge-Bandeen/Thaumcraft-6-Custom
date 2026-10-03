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
 * Replaces the old (1.12.2) {@code TileAlembic}. A small essentia container that never fills itself - it
 * only ever receives essentia via {@link #processAlembics}, called by a {@link TileSmelter} sitting below
 * a stack of one or more alembics (see that class's own doc comment). {@code canInputFrom} is always false,
 * same as the original - unlike a Warded Jar, an alembic can't be filled by a tube/jar network at all.
 *
 * As the original, a label goes on one of its side faces ({@link #getLabelFace}, DOWN while unlabelled) and locks
 * it to one aspect - what it already holds, else the label's; that face no longer connects, and the label is drawn
 * on it ({@link thaumcraft.client.render.TileAlembicRenderer}). See
 * {@link thaumcraft.common.blocks.essentia.BlockAlembic} for taking it off and emptying the alembic.
 */
public class TileAlembic extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	public static final int MAX_AMOUNT = 128;

	private Aspect aspect;
	private Aspect aspectFilter;
	private int amount;
	/** The labelled face (the original's {@code facing}); DOWN, which never connects anyway, while unlabelled. */
	private Direction labelFace = Direction.DOWN;

	public TileAlembic(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ALEMBIC.get(), pos, state);
	}

	public Aspect getAspectFilter() {
		return aspectFilter;
	}

	public Direction getLabelFace() {
		return labelFace;
	}

	/** The original's applyLabel: only on a side face of an unlabelled alembic; it locks to what the alembic holds,
	 * else to the label's aspect (an empty alembic needs a marked label). */
	public boolean applyLabel(@javax.annotation.Nullable Aspect labelAspect, Direction side) {
		if (side.getAxis() == Direction.Axis.Y || aspectFilter != null) return false;
		Aspect lock = amount > 0 ? aspect : labelAspect;
		if (lock == null) return false;
		aspectFilter = lock;
		labelFace = side;
		setChanged();
		return true;
	}

	public void clearLabel() {
		aspectFilter = null;
		labelFace = Direction.DOWN;
		setChanged();
	}

	/** Sneak-click with an empty hand (not on the label): the essentia is poured out. @return how much there was. */
	public int empty() {
		int was = amount;
		aspect = null;
		amount = 0;
		setChanged();
		return was;
	}

	@Override
	public AspectList getAspects() {
		return aspect != null ? new AspectList().add(aspect, amount) : new AspectList();
	}

	@Override
	public void setAspects(AspectList aspects) {
	}

	@Override
	public int addToContainer(Aspect tag, int add) {
		if (aspectFilter != null && tag != aspectFilter) return add;
		int added = 0;
		if ((amount < MAX_AMOUNT && tag == aspect) || amount == 0) {
			aspect = tag;
			added = Math.min(add, MAX_AMOUNT - amount);
			amount += added;
			setChanged();
		}
		return add - added;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int take) {
		if (amount <= 0 || aspect == null) {
			aspect = null;
			amount = 0;
			return false;
		}
		if (aspect == tag && amount >= take) {
			amount -= take;
			if (amount <= 0) {
				aspect = null;
				amount = 0;
			}
			setChanged();
			return true;
		}
		return false;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return amount > 0 && aspect != null && ot.getAmount(aspect) > 0;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int take) {
		return amount >= take && tag == aspect;
	}

	@Override
	public int containerContains(Aspect tag) {
		return tag == aspect ? amount : 0;
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return true;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face != Direction.DOWN && face != labelFace;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return false;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return face != Direction.DOWN && face != labelFace;
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
		return 0;
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
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		return canOutputTo(face) && takeFromContainer(aspect, amount) ? amount : 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		return 0;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	/**
	 * Called by {@link TileSmelter} when it has one unit of {@code aspect} ready to give away - climbs the
	 * stack of alembics directly above {@code pos} (the smelter's own position, or a smelter-aux position
	 * sitting beside it in the original; this port's simplified Smelter only calls it with its own
	 * position), first trying one already holding a matching aspect with room, then falling back to the
	 * first one with a matching (or absent) filter and room.
	 */
	public static boolean processAlembics(Level level, BlockPos pos, Aspect aspect) {
		int deep = 1;
		while (level.getBlockEntity(pos.above(deep)) instanceof TileAlembic alembic) {
			if (alembic.amount > 0 && alembic.aspect == aspect && alembic.addToContainer(aspect, 1) == 0) {
				return true;
			}
			deep++;
		}
		deep = 1;
		while (level.getBlockEntity(pos.above(deep)) instanceof TileAlembic alembic) {
			if ((alembic.aspectFilter == null || alembic.aspectFilter == aspect) && alembic.addToContainer(aspect, 1) == 0) {
				return true;
			}
			deep++;
		}
		return false;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspect = tag.contains("aspect") ? Aspect.getAspect(tag.getString("aspect")) : null;
		aspectFilter = tag.contains("AspectFilter") ? Aspect.getAspect(tag.getString("AspectFilter")) : null;
		amount = tag.getInt("amount");
		labelFace = tag.contains("facing") ? Direction.from3DDataValue(tag.getByte("facing")) : Direction.DOWN;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (aspect != null) tag.putString("aspect", aspect.getTag());
		if (aspectFilter != null) tag.putString("AspectFilter", aspectFilter.getTag());
		tag.putInt("amount", amount);
		tag.putByte("facing", (byte) labelFace.get3DDataValue());
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
