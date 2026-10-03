package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileVisGenerator} - drains 1 vis per recharge cycle from the local aura
 * and converts it 1:1000 into stored energy, then actively pushes it (up to {@link #MAX_PUSH} per tick) into
 * whatever {@link IEnergyStorage} sits at its output face. {@code IEnergyStorage} is one of the few Forge-era
 * capability interfaces that survived into NeoForge 1.21.1 essentially unchanged (same method set, now
 * reached via {@link Capabilities.EnergyStorage#BLOCK} instead of a {@code CapabilityInject} field) - this
 * device ports over almost mechanically as a result, unlike most of this session's other devices.
 *
 * Same "push-only" design as the original: {@link #receiveEnergy}/{@link #extractEnergy} (the passive,
 * externally-triggered paths a hopper/cable would use) both always return 0 - the only way energy actually
 * moves is this tile's own active {@link #pushEnergy} call during its tick, matching {@code canReceive()
 * == false}. The capability is still exposed (see {@code Thaumcraft#registerCapabilities}) purely so an
 * external machine can read {@link #getEnergyStored()}/{@link #getMaxEnergyStored()} - again matching the
 * original.
 *
 * As the original, while it holds energy, sparks crackle at its output face ({@code BlockVisGenerator#animateTick});
 * the client is told whenever it goes from empty to charged or back.
 */
public class TileVisGenerator extends BlockEntity implements IEnergyStorage {

	private static final int CAPACITY = 1000;
	private static final int MAX_PUSH = 20;

	private int energy;

	public TileVisGenerator(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.VIS_GENERATOR.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileVisGenerator tile) {
		if (level.isClientSide) return;
		if (!state.getValue(BlockStateProperties.ENABLED)) return;
		tile.recharge(level, pos);
		tile.pushEnergy(level, pos, state);
	}

	private void recharge(Level level, BlockPos pos) {
		if (energy == 0) {
			float vis = AuraHandler.drainVis(level, pos, 1.0f, false);
			if (vis > 0.0f) {
				energy = (int) (vis * 1000.0f);
				setChanged();
			}
		}
	}

	private void pushEnergy(Level level, BlockPos pos, BlockState state) {
		if (energy <= 0) return;
		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockPos targetPos = pos.relative(facing);
		IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, facing.getOpposite());
		if (target == null || !target.canReceive()) return;
		int toSend = Math.min(energy, MAX_PUSH);
		int accepted = target.receiveEnergy(toSend, false);
		if (accepted > 0) {
			energy -= accepted;
			setChanged();
		}
	}

	@Override
	public int receiveEnergy(int toReceive, boolean simulate) {
		return 0;
	}

	@Override
	public int extractEnergy(int toExtract, boolean simulate) {
		return 0;
	}

	@Override
	public int getEnergyStored() {
		return energy;
	}

	@Override
	public int getMaxEnergyStored() {
		return CAPACITY;
	}

	@Override
	public boolean canExtract() {
		return true;
	}

	@Override
	public boolean canReceive() {
		return false;
	}

	public Direction getFacing() {
		return getBlockState().getValue(BlockStateProperties.FACING);
	}

	/** Test/diagnostic hook - lets a GameTest set stored energy directly rather than waiting out real aura
	 * drain ticks. */
	public void setEnergyForTest(int value) {
		this.energy = value;
		setChanged();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Energy", energy);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		energy = tag.getInt("Energy");
	}

	private boolean syncedCharged;
	/** Client: whether it holds energy. */
	private boolean clientCharged;

	public boolean isChargedForDisplay() {
		return level != null && level.isClientSide ? clientCharged : energy > 0;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide && (energy > 0) != syncedCharged) {
			syncedCharged = energy > 0;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putBoolean("charged", energy > 0);
		return tag;
	}

	@Override
	public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
		clientCharged = tag.getBoolean("charged");
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(net.minecraft.network.Connection net, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt,
			HolderLookup.Provider registries) {
		handleUpdateTag(pkt.getTag(), registries);
	}
}
