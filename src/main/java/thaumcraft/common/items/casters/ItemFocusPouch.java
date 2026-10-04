package thaumcraft.common.items.casters;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import thaumcraft.common.container.FocusPouchMenu;

/**
 * Replaces the old (1.12.2) {@code ItemFocusPouch} - a real 18-slot portable focus container. Baubles is
 * dropped (same standing decision every other formerly-Baubles item in this port made - see
 * {@code WornGearHelper}'s own doc comment), so this is just a plain carried item now, opened directly by
 * right-clicking it rather than worn in a belt slot; the original's own real "store extra foci you're not
 * actively swapping between" purpose is unaffected by that change.
 *
 * Real contents storage: the vanilla {@link DataComponents#CONTAINER} component ({@link
 * ItemContainerContents}) - the same real, built-in mechanism Bundles/shulker-box items use for "this item
 * carries its own sub-inventory" - replacing the original's own {@code NBTTagCompound}/{@code
 * ItemStackHelper} round-trip. {@link FocusPouchMenu} reads/writes it.
 *
 * As the original, the focus radial offers the foci inside carried pouches too, and a focus swapped out of a
 * caster goes into a pouch with room first (see {@link CasterFocusHelper}).
 */
public class ItemFocusPouch extends Item {

	public ItemFocusPouch(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			player.openMenu(new MenuProvider() {
				@Override
				public Component getDisplayName() {
					return stack.getHoverName();
				}

				@Override
				public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
					return new FocusPouchMenu(id, inv, stack);
				}
			});
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** Real accessor - {@link FocusPouchMenu} seeds its own real {@code ItemStackHandler} from this at open
	 * time. */
	public static NonNullList<ItemStack> getContents(ItemStack pouch) {
		NonNullList<ItemStack> list = NonNullList.withSize(18, ItemStack.EMPTY);
		ItemContainerContents contents = pouch.get(DataComponents.CONTAINER);
		if (contents != null) {
			contents.copyInto(list);
		}
		return list;
	}

	/** Real accessor - {@link FocusPouchMenu} writes its own real {@code ItemStackHandler} back through this
	 * when its screen closes. */
	public static void setContents(ItemStack pouch, NonNullList<ItemStack> list) {
		pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list));
	}
}
