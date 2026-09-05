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
 * Replaces the old (1.12.2) {@code TileCentrifuge} - a real aspect-decomposition device: pulls one unit of a
 * real non-primal (compound) aspect from below via the same real suction-based {@link IEssentiaTransport}
 * protocol {@link TileTube} already established, holds it for 2 real seconds, then splits it into one of its
 * own 2 real component aspects (random pick, matching the original exactly), ready to be drawn from above.
 * Powering the block with redstone pauses it entirely, same as the original.
 *
 * Real, deliberate cut: the original's client-only spinning-axle animation (drawn by a custom
 * TileEntitySpecialRenderer, since the block itself rendered fully invisible) is dropped in favor of the
 * real, already-present static baked model - see {@link thaumcraft.common.blocks.essentia.BlockCentrifuge}'s
 * own doc comment for why that's a real improvement (visible at all) over reproducing the original's own
 * render setup exactly.
 */
public class TileCentrifuge extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	private Aspect aspectIn;
	private Aspect aspectOut;
	private int count;
	private int process;

	public TileCentrifuge(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.CENTRIFUGE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileCentrifuge tile) {
		if (level.isClientSide || level.hasNeighborSignal(pos)) return;
		if (tile.aspectOut == null && tile.aspectIn == null && ++tile.count % 5 == 0) {
			tile.drawEssentia(level, pos);
		}
		if (tile.process > 0) {
			tile.process--;
		}
		if (tile.aspectOut == null && tile.aspectIn != null && tile.process == 0) {
			tile.processEssentia();
		}
	}

	private void processEssentia() {
		Aspect[] comps = aspectIn.getComponents();
		aspectOut = comps[level.getRandom().nextInt(2)];
		aspectIn = null;
		setChanged();
	}

	private void drawEssentia(Level level, BlockPos pos) {
		if (!(level.getBlockEntity(pos.below()) instanceof IEssentiaTransport neighbor)) return;
		if (!neighbor.canOutputTo(Direction.UP)) return;
		if (neighbor.getEssentiaAmount(Direction.UP) <= 0) return;
		Aspect type = neighbor.getEssentiaType(Direction.UP);
		if (type == null || type.isPrimal()) return;
		if (neighbor.getSuctionAmount(Direction.UP) >= getSuctionAmount(Direction.DOWN)) return;
		if (getSuctionAmount(Direction.DOWN) < neighbor.getMinimumSuction()) return;
		if (neighbor.takeEssentia(type, 1, Direction.UP) != 1) return;
		aspectIn = type;
		process = 39;
		setChanged();
	}

	@Override
	public AspectList getAspects() {
		AspectList al = new AspectList();
		if (aspectOut != null) al.add(aspectOut, 1);
		return al;
	}

	@Override
	public void setAspects(AspectList aspects) {
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return true;
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		if (amount > 0 && aspectOut == null) {
			aspectOut = tag;
			setChanged();
			amount--;
		}
		return amount;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		if (aspectOut != null && tag == aspectOut) {
			aspectOut = null;
			setChanged();
			return true;
		}
		return false;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		return amount == 1 && tag == aspectOut;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		for (Aspect a : ot.getAspects()) {
			if (a == aspectOut) return true;
		}
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return tag == aspectOut ? 1 : 0;
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face == Direction.UP || face == Direction.DOWN;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face == Direction.DOWN;
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
		return null;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		if (face != Direction.DOWN) return 0;
		if (level != null && level.hasNeighborSignal(getBlockPos())) return 0;
		return aspectIn == null ? 128 : 64;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return aspectOut;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return aspectOut != null ? 1 : 0;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		return canOutputTo(face) && takeFromContainer(aspect, amount) ? amount : 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		if (aspectIn == null && !aspect.isPrimal()) {
			aspectIn = aspect;
			process = 39;
			setChanged();
			return 1;
		}
		return 0;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspectIn = tag.contains("aspectIn") ? Aspect.getAspect(tag.getString("aspectIn")) : null;
		aspectOut = tag.contains("aspectOut") ? Aspect.getAspect(tag.getString("aspectOut")) : null;
		process = tag.getInt("process");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (aspectIn != null) tag.putString("aspectIn", aspectIn.getTag());
		if (aspectOut != null) tag.putString("aspectOut", aspectOut.getTag());
		tag.putInt("process", process);
	}
}
