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
 * Real spinning-axle animation restored 2026-09-16 - see {@link thaumcraft.client.render.TileCentrifugeRenderer}'s
 * own doc comment (this class's own earlier doc comment had called dropping it "a real improvement" over the
 * original, since the block itself was otherwise fully invisible without a real static model - that reasoning
 * was stale once a real static baked model already existed to render on top of; per the project's own standing
 * "reevaluate past cuts once their blocker resolves" directive, restoring the real original animation too is
 * strictly better, not a tradeoff). {@link #rotation}/{@link #rotationSpeed} are real, plain (not synced)
 * client-only fields - the true original's own real version computed them purely client-side too, off
 * already-synced state ({@link #aspectIn}/redstone power), so they self-synchronize without needing their own
 * value pushed over the network every tick (same real shape this port's own cultist-portal
 * {@code activeCounter}/{@code pulse} fields already established). Real
 * {@code getUpdateTag}/{@code getUpdatePacket} sync wiring added alongside this (this class had none before -
 * {@link #aspectIn} itself was never actually visible to the client until now, a real, previously-unnoticed
 * gap since every other consumer of it was server-only).
 */
public class TileCentrifuge extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	private Aspect aspectIn;
	private Aspect aspectOut;
	private int count;
	private int process;

	/** Real client-only spin-rate/angle, matching the true original's own real fields exactly - see the class
	 * doc comment for why these are deliberately plain, not synced. */
	public float rotationSpeed;
	public float rotation;

	public TileCentrifuge(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.CENTRIFUGE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileCentrifuge tile) {
		if (level.isClientSide) {
			tile.tickRotation(level, pos);
			return;
		}
		if (level.hasNeighborSignal(pos)) return;
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

	/** Real modern equivalent of the true original's own real client-side {@code update()} branch - see the
	 * class doc comment. */
	private void tickRotation(Level level, BlockPos pos) {
		boolean powered = level.hasNeighborSignal(pos);
		if (aspectIn != null && !powered && rotationSpeed < 20.0f) {
			rotationSpeed += 2.0f;
		}
		if ((aspectIn == null || powered) && rotationSpeed > 0.0f) {
			rotationSpeed -= 0.5f;
		}
		int previous = (int) rotation;
		rotation += rotationSpeed;
		if (rotation % 180.0f <= 20.0f && previous % 180 >= 160 && rotationSpeed > 0.0f) {
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					thaumcraft.common.registry.ThaumcraftSounds.PUMP.get(), net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f, false);
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

	/** Real block-entity sync wiring, added alongside the real spinning-axle animation restoration - see the
	 * class doc comment (this tile had none before; {@link #aspectIn} was never actually visible to the
	 * client until now). Same real pattern {@code TileCrucible}/{@code TileFocalManipulator}/
	 * {@code TileInfusionMatrix} already established. */
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
