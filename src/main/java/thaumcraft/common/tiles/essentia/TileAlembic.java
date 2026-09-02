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
 * Can be locked to one aspect via {@link thaumcraft.api.blocks.ILabelable} - see
 * {@link thaumcraft.common.blocks.essentia.BlockAlembic#applyLabel}. Simplified from the original's version
 * of that mechanic: the original locked the label to a specific *face* (one of the four horizontal sides,
 * rendered via a TESR-drawn label texture + reserved that face from essentia I/O) - this port drops the
 * per-face targeting and TESR rendering entirely (same "no custom BlockEntityRenderer" boundary as every
 * other device this session) and just locks the whole tile to one aspect, the same shape as
 * {@link TileJarFillable}'s own label mechanic.
 */
public class TileAlembic extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	public static final int MAX_AMOUNT = 128;

	private Aspect aspect;
	private Aspect aspectFilter;
	private int amount;

	public TileAlembic(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ALEMBIC.get(), pos, state);
	}

	public Aspect getAspectFilter() {
		return aspectFilter;
	}

	/** {@code BlockAlembic#applyLabel} calls this - locks the alembic to one aspect. Returns false (same as
	 * {@link TileJarFillable#applyLabel}) if there's nothing to lock to or it's already locked. */
	public boolean applyLabel(Aspect newFilter) {
		if (aspectFilter != null || newFilter == null) return false;
		if (amount > 0 && aspect != newFilter) return false;
		aspectFilter = newFilter;
		setChanged();
		return true;
	}

	public void clearLabel() {
		aspectFilter = null;
		setChanged();
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
		return face != Direction.DOWN;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return false;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return face != Direction.DOWN;
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
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (aspect != null) tag.putString("aspect", aspect.getTag());
		if (aspectFilter != null) tag.putString("AspectFilter", aspectFilter.getTag());
		tag.putInt("amount", amount);
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
