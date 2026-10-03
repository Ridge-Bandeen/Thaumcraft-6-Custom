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
 * As the original, its tank takes any fluid, and its screen toggles between two modes: "mix" (the default) spends
 * a Bath Salt and a bucket of water on each block of Purifying Fluid, while "just the fluid" pours out a block of
 * whatever is in the tank (any fluid that can be placed) for each bucket. A block goes straight above it, or - once
 * a full one is already there - beside a full one somewhere in the 5x5 above.
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

	public final FluidTank tank = new FluidTank(CAPACITY) {
		@Override
		protected void onContentsChanged() {
			setChanged();
		}
	};

	private int counter;
	/** The original's mix flag: make Purifying Fluid (true) or pour out the tank's own fluid (false). */
	private boolean mix = true;

	public boolean isMix() {
		return mix;
	}

	public void toggleMix() {
		mix = !mix;
		setChanged();
	}

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
		if (tile.counter++ % 40 != 0) return;
		if (level.hasNeighborSignal(pos) || !tile.hasIngredients()) return;
		BlockState place = tile.placeState();
		if (place == null) return;
		if (tile.isFull(level.getBlockState(pos.above()))) {
			for (int xx = -2; xx <= 2; xx++) {
				for (int zz = -2; zz <= 2; zz++) {
					BlockPos p = pos.offset(xx, 1, zz);
					if (tile.isValidLocation(level, p, true, place)) {
						tile.consumeIngredients();
						level.setBlockAndUpdate(p, place);
						return;
					}
				}
			}
		} else if (tile.isValidLocation(level, pos.above(), false, place)) {
			tile.consumeIngredients();
			level.setBlockAndUpdate(pos.above(), place);
		}
	}

	private boolean hasIngredients() {
		if (mix) {
			return tank.getFluid().is(Fluids.WATER) && tank.getFluidAmount() >= WATER_COST && items.getStackInSlot(0).getItem() instanceof ItemBathSalts;
		}
		return !tank.isEmpty() && tank.getFluidAmount() >= WATER_COST && !tank.getFluid().getFluid().defaultFluidState().createLegacyBlock().isAir();
	}

	private void consumeIngredients() {
		if (mix) items.extractItem(0, 1, false);
		tank.drain(WATER_COST, IFluidHandler.FluidAction.EXECUTE);
	}

	/** A full block of what this spa makes: Purifying Fluid, or a source of the tank's fluid. */
	@javax.annotation.Nullable
	private BlockState placeState() {
		if (mix) {
			return thaumcraft.api.blocks.BlocksTC.purifyingFluid.get().defaultBlockState().setValue(BlockPurifyingFluid.LEVEL, BlockPurifyingFluid.MAX_LEVEL);
		}
		return tank.isEmpty() ? null : tank.getFluid().getFluid().defaultFluidState().createLegacyBlock();
	}

	private boolean isFull(BlockState state) {
		if (mix) {
			return state.is(thaumcraft.api.blocks.BlocksTC.purifyingFluid.get()) && state.getValue(BlockPurifyingFluid.LEVEL) == BlockPurifyingFluid.MAX_LEVEL;
		}
		return !tank.isEmpty() && state.getFluidState().isSource() && state.getFluidState().getType().isSame(tank.getFluid().getFluid());
	}

	/** The original's isValidLocation: on a solid top, replaceable, not already full - and, when it must be,
	 * beside a full block. Water never goes where it would boil away. */
	private boolean isValidLocation(Level level, BlockPos p, boolean mustBeAdjacent, BlockState place) {
		if (place.getFluidState().is(net.minecraft.tags.FluidTags.WATER) && level.dimensionType().ultraWarm()) return false;
		BlockState here = level.getBlockState(p);
		if (!level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return false;
		if (!here.isAir() && !here.canBeReplaced()) return false;
		if (isFull(here)) return false;
		if (!mustBeAdjacent) return true;
		for (Direction d : Direction.values()) {
			if (isFull(level.getBlockState(p.relative(d)))) return true;
		}
		return false;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		CompoundTag tankTag = new CompoundTag();
		tank.writeToNBT(registries, tankTag);
		tag.put("Tank", tankTag);
		tag.putBoolean("mix", mix);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		if (tag.contains("Tank")) {
			tank.readFromNBT(registries, tag.getCompound("Tank"));
		}
		mix = !tag.contains("mix") || tag.getBoolean("mix");
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
