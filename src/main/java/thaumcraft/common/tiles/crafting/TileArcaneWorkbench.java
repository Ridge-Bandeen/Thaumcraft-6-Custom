package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.common.container.ArcaneWorkbenchMenu;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileArcaneWorkbench}. The 15-slot layout (9 main grid + 6 crystal ring,
 * indices 0-8 / 9-14) is unchanged from the original {@code InventoryArcaneWorkbench} - {@link ArcaneWorkbenchMenu}
 * addresses the same slot ranges.
 *
 * Vis cost checking/spending now happens directly in {@link ArcaneWorkbenchMenu} against the real
 * {@code AuraHandler} (the original routed it through this class's own getAura()/spendAura() methods,
 * which also supported drawing from a wider area via an Arcane Workbench Charger placed above - that
 * block isn't ported, so the menu always draws from this tile's own chunk for now).
 */
public class TileArcaneWorkbench extends BaseContainerBlockEntity {

	public static final int GRID_SIZE = 9;
	public static final int CRYSTAL_SIZE = 6;
	public static final int SLOT_COUNT = GRID_SIZE + CRYSTAL_SIZE;

	private NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

	public TileArcaneWorkbench(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ARCANE_WORKBENCH.get(), pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.thaumcraft.arcane_workbench");
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new ArcaneWorkbenchMenu(containerId, inventory, this);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, items, registries);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
	}
}
