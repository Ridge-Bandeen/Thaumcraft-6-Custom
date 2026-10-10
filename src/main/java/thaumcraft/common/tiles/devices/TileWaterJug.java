package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileWaterJug} ("Everfull Urn") - a bottomless water source that slowly
 * regenerates from the local aura and can be bottled/bucketed from directly. {@code IFluidHandler} is
 * another Forge-era capability that survived into NeoForge 1.21.1 close to unchanged (same "reuse the real
 * template class" story as {@link net.neoforged.neoforge.energy.IEnergyStorage} did for
 * {@link thaumcraft.common.tiles.devices.TileVisGenerator}) - {@link FluidTank} here is NeoForge's own
 * reusable reference implementation, not a hand-rolled tank.
 *
 * As the original, every 5 ticks it looks at one more spot of the 5x3x5 box around it (centred on it, one block
 * down to one up), remembering every fluid tank (fillable from above) and cauldron it finds; then it pours 25 mB
 * into the first remembered tank that takes any, or raises a cauldron's water a level for 333 mB, with a water
 * trail to it (block event 1). And every 5 ticks it refills up to a tenth of what it's missing from the aura, vis to
 * water 1:1000. (The original's Botania petal apothecary branch has nothing to fill here.)
 */
public class TileWaterJug extends BlockEntity {

	private static final int CAPACITY = 1000;

	public final FluidTank tank = new FluidTank(CAPACITY, stack -> stack.getFluid() == Fluids.WATER) {
		@Override
		protected void onContentsChanged() {
			setChanged();
		}
	};

	public TileWaterJug(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.WATER_JUG.get(), pos, state);
	}

	private int counter;
	private int zone;
	/** Zones (spots of the 5x3x5 box) holding something to fill; rediscovered by the scan after a reload. */
	private final java.util.List<Integer> handlers = new java.util.ArrayList<>();
	/** Client: the zone the last water trail went to, and how long it has left. */
	private int zc;
	private int tcount;

	private BlockPos zonePos(int z) {
		return worldPosition.offset(z / 5 % 5 - 2, z / 5 / 5 % 3 - 1, z % 5 - 2);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileWaterJug tile) {
		tile.counter++;
		if (level.isClientSide) {
			if (tile.tcount > 0) {
				if (tile.tcount % 5 == 0) {
					thaumcraft.client.fx.ClientFxHandlers.waterTrail(level, pos, tile.zonePos(tile.zc), tile.counter);
				}
				tile.tcount--;
			}
			return;
		}
		if (tile.counter % 5 != 0) return;

		tile.zone++;
		BlockPos scan = tile.zonePos(tile.zone);
		int key = tile.zone % 75;
		if (!scan.equals(pos) && !tile.handlers.contains(key) && (fluidHandler(level, scan) != null || isCauldron(level.getBlockState(scan)))) {
			tile.handlers.add(key);
		}
		int i = 0;
		while (i < tile.handlers.size() && tile.tank.getFluidAmount() >= 25) {
			int zz = tile.handlers.get(i);
			BlockPos target = tile.zonePos(zz);
			BlockState bs = level.getBlockState(target);
			IFluidHandler fh = fluidHandler(level, target);
			if (fh != null) {
				int q = fh.fill(new FluidStack(Fluids.WATER, 25), IFluidHandler.FluidAction.EXECUTE);
				if (q > 0) {
					tile.tank.drain(q, IFluidHandler.FluidAction.EXECUTE);
					level.blockEvent(pos, state.getBlock(), 1, zz);
					break;
				}
			} else {
				if (!isCauldron(bs) || tile.tank.getFluidAmount() < 333) {
					tile.handlers.remove(i);
					continue;
				}
				int lvl = bs.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON) ? bs.getValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL) : 0;
				if (lvl < 3) {
					level.setBlock(target, net.minecraft.world.level.block.Blocks.WATER_CAULDRON.defaultBlockState()
							.setValue(net.minecraft.world.level.block.LayeredCauldronBlock.LEVEL, lvl + 1), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
					level.updateNeighbourForOutputSignal(target, level.getBlockState(target).getBlock());
					level.blockEvent(pos, state.getBlock(), 1, zz);
					tile.tank.drain(333, IFluidHandler.FluidAction.EXECUTE);
				}
			}
			i++;
		}

		if (tile.tank.getFluidAmount() < CAPACITY) {
			float drawFraction = Math.min((CAPACITY - tile.tank.getFluidAmount()) / (float) CAPACITY, 0.1f);
			float drained = AuraHandler.drainVis(level, pos, drawFraction, false);
			int gained = (int) (CAPACITY * drained);
			if (gained > 0) {
				tile.tank.fill(new FluidStack(Fluids.WATER, gained), IFluidHandler.FluidAction.EXECUTE);
			}
		}
	}

	@javax.annotation.Nullable
	private static IFluidHandler fluidHandler(Level level, BlockPos pos) {
		return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, net.minecraft.core.Direction.UP);
	}

	private static boolean isCauldron(BlockState state) {
		return state.is(net.minecraft.world.level.block.Blocks.CAULDRON) || state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON);
	}

	/** Block event 1: a water trail to zone {@code param} on the clients. */
	public boolean onBlockEvent(int id, int param) {
		if (id != 1) return false;
		zc = param;
		tcount = 5;
		return true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		CompoundTag tankTag = new CompoundTag();
		tank.writeToNBT(registries, tankTag);
		tag.put("Tank", tankTag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("Tank")) {
			tank.readFromNBT(registries, tag.getCompound("Tank"));
		}
	}

	/** The client only needs to know when this turns full or not (the original synced the whole tile); sent then. */
	private boolean syncedFull;

	@Override
	public void setChanged() {
		super.setChanged();
		boolean full = isFull();
		if (level != null && !level.isClientSide && full != syncedFull) {
			syncedFull = full;
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** Whether the tank is full - the original's urn then splashes water over its top (see BlockWaterJug#animateTick). */
	public boolean isFull() {
		return tank.getFluidAmount() >= tank.getCapacity();
	}
}
