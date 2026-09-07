package thaumcraft.common.items.casters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket;

/**
 * Common-side logic shared by the real focus-radial HUD (client: {@code FocusRadialOverlay}, enumerates and
 * renders the same real list this class computes) and its real server-side handler - replaces the relevant
 * parts of the old (1.12.2) {@code CasterManager}.
 *
 * {@link #findCarriedFoci} drops the original's Focus Pouch scan - {@code ItemFocusPouch} isn't ported yet,
 * same real "no Baubles/bag items, plain inventory scan" simplification {@link thaumcraft.api.items.WornGearHelper}
 * already established elsewhere in this port. Sorted by real display name (a stable, real ordering) rather
 * than the original's own {@code getSortingHelper} string key, which this port's {@code ItemFocus} never
 * carried over - display name serves the same real "consistent radial ordering" purpose.
 */
public final class CasterFocusHelper {

	private CasterFocusHelper() {
	}

	/** Every real {@link ItemFocus} the player is carrying (main inventory, hotbar, and offhand), sorted by
	 * real display name. */
	public static List<ItemStack> findCarriedFoci(Player player) {
		List<ItemStack> foci = new ArrayList<>();
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.getItem() instanceof ItemFocus) {
				foci.add(stack);
			}
		}
		foci.sort(Comparator.comparing(s -> s.getHoverName().getString()));
		return foci;
	}

	/** Real swap: binds the real focus at the player's real inventory {@code slot} to {@code casterStack},
	 * returning whatever was previously bound back to the player's inventory (dropped at their feet if it's
	 * full) - matches the original's own {@code CasterManager#changeFocus} "give the old one back" behavior.
	 * {@code slot < 0} unbinds without binding a replacement (the original's own "REMOVE" case). A stale or
	 * no-longer-valid slot (out of range, no longer a real {@link ItemFocus}) is simply a no-op - the server
	 * never trusts the client's claimed slot beyond that real check. */
	public static void changeFocus(ItemStack casterStack, Player player, int slot) {
		if (!(casterStack.getItem() instanceof ICaster caster)) return;

		ItemStack picked = ItemStack.EMPTY;
		if (slot >= 0) {
			Inventory inv = player.getInventory();
			if (slot >= inv.getContainerSize()) return;
			ItemStack candidate = inv.getItem(slot);
			if (!(candidate.getItem() instanceof ItemFocus)) return;
			picked = candidate;
		}

		ItemStack old = caster.getFocusStack(casterStack);
		caster.setFocus(casterStack, picked.isEmpty() ? null : picked);
		if (!picked.isEmpty() && !player.getAbilities().instabuild) {
			picked.shrink(1);
		}
		if (!old.isEmpty() && !player.getInventory().add(old)) {
			player.drop(old, false);
		}
	}

	/** Real server-side handler for {@link ServerboundChangeCasterFocusPacket} - applies the swap to
	 * whichever hand the player is really holding a real {@link ICaster} in (main hand checked first,
	 * matching the original's own {@code PacketFocusChangeToServer} priority). */
	public static void handleChangeFocus(ServerboundChangeCasterFocusPacket payload, ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof ICaster) {
			changeFocus(main, player, payload.slot());
			return;
		}
		ItemStack off = player.getOffhandItem();
		if (off.getItem() instanceof ICaster) {
			changeFocus(off, player, payload.slot());
		}
	}
}
