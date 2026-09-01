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
 * Simplified vs the original: drops the original's 5x5x3 neighbor-scanning auto-fill behavior entirely (it
 * would tick outward every few ticks, topping off adjacent cauldrons/tanks/Botania petal apothecaries on its
 * own, no player interaction needed) - a real, separate subsystem layered on top of the device's actual
 * identity, and (for the Botania branch specifically) a cross-mod integration this port doesn't include.
 * What's kept is the core mechanic: an infinite water source a player fills containers from directly, plus
 * the real regenerate-from-aura rate (same formula as the original - up to 10% of the missing amount per
 * tick, converted from drained vis 1:1000 the same way {@link TileVisGenerator} converts vis to energy).
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

	public static void tick(Level level, BlockPos pos, BlockState state, TileWaterJug tile) {
		if (level.isClientSide) return;
		if (tile.tank.getFluidAmount() >= CAPACITY) return;

		float missing = (CAPACITY - tile.tank.getFluidAmount()) / (float) CAPACITY;
		float drawFraction = Math.min(missing, 0.1f);
		float drained = AuraHandler.drainVis(level, pos, drawFraction, false);
		int gained = (int) (CAPACITY * drained);
		if (gained > 0) {
			tile.tank.fill(new FluidStack(Fluids.WATER, gained), IFluidHandler.FluidAction.EXECUTE);
		}
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
}
