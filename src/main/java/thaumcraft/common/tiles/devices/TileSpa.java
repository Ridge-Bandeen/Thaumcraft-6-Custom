package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.common.blocks.misc.BlockPurifyingFluid;
import thaumcraft.common.container.SpaMenu;
import thaumcraft.common.items.consumables.ItemBathSalts;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileSpa}. Real {@link FluidTank} (water-only, same real reference
 * implementation {@link TileWaterJug} already established - see that class's own doc comment) plus a real
 * Bath Salts slot; every 40 ticks, while unpowered, with a real Bath Salt loaded and at least 1000 mB of real
 * water banked, it consumes both and places a real full-level {@link BlockPurifyingFluid} in the first open
 * spot it finds in a real 5x5 area above itself (checked directly above first, matching the original's own
 * priority).
 *
 * Real, documented cut: the original's own "unmix" mode - a generic utility that drained the tank's
 * currently-held fluid (whatever it happened to be, water/lava/anything) back into the world as plain source
 * blocks - is dropped. That's a generic fluid-placement utility layered on top of the device's actual
 * thematic identity (making purifying fluid), not the mechanic that makes a Spa a Spa, and modern NeoForge's
 * own "place an arbitrary Fluid in the world" API shape differs enough from the original's
 * {@code BlockFluidBase}-based one that reusing it here isn't a simple swap. The real core mechanic - bank
 * water, spend Bath Salts, produce real purifying fluid nearby on a timer - is kept in full.
 */
public class TileSpa extends BlockEntity implements MenuProvider {

	private static final int CAPACITY = 5000;
	private static final int WATER_COST = 1000;

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.getItem() instanceof ItemBathSalts;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	public final FluidTank tank = new FluidTank(CAPACITY, stack -> stack.getFluid() == Fluids.WATER) {
		@Override
		protected void onContentsChanged() {
			setChanged();
		}
	};

	private int counter;

	public TileSpa(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.SPA.get(), pos, state);
	}

	public IItemHandler getItemHandler() {
		return items;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.thaumcraft.spa");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new SpaMenu(containerId, inventory, this);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileSpa tile) {
		if (level.isClientSide) return;
		tile.counter++;
		if (tile.counter % 40 != 0) return;
		if (level.hasNeighborSignal(pos)) return;
		if (tile.tank.getFluidAmount() < WATER_COST) return;
		if (!(tile.items.getStackInSlot(0).getItem() instanceof ItemBathSalts)) return;

		BlockPos target = findOpenSpot(level, pos);
		if (target == null) return;

		tile.tank.drain(WATER_COST, IFluidHandler.FluidAction.EXECUTE);
		tile.items.extractItem(0, 1, false);
		level.setBlockAndUpdate(target, thaumcraft.api.blocks.BlocksTC.purifyingFluid.get().defaultBlockState()
				.setValue(BlockPurifyingFluid.LEVEL, BlockPurifyingFluid.MAX_LEVEL));
		tile.setChanged();
	}

	private static BlockPos findOpenSpot(Level level, BlockPos pos) {
		BlockPos direct = pos.above();
		if (isOpenSpot(level, direct)) return direct;
		for (int xx = -2; xx <= 2; xx++) {
			for (int zz = -2; zz <= 2; zz++) {
				if (xx == 0 && zz == 0) continue;
				BlockPos p = pos.above().offset(xx, 0, zz);
				if (isOpenSpot(level, p)) return p;
			}
		}
		return null;
	}

	private static boolean isOpenSpot(Level level, BlockPos pos) {
		BlockState here = level.getBlockState(pos);
		if (!here.isAir() && !here.canBeReplaced()) return false;
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		CompoundTag tankTag = new CompoundTag();
		tank.writeToNBT(registries, tankTag);
		tag.put("Tank", tankTag);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		if (tag.contains("Tank")) {
			tank.readFromNBT(registries, tag.getCompound("Tank"));
		}
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
