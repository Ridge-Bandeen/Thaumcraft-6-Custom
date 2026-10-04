package thaumcraft.common.items.casters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.ICaster;
import thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket;

/**
 * The original's {@code CasterManager#changeFocus}, shared by the focus radial (client) and its server handler. As the
 * original, the foci on offer are those in the main inventory and inside any Focus Pouch carried there; each has a code
 * - its inventory slot, or {@code 1000 x pouch number + pouch slot} - and they are ordered by name and spell. Taking a
 * focus out (or swapping one) puts the old focus into a carried pouch with room first, else the inventory; if neither
 * has room nothing changes.
 */
public final class CasterFocusHelper {

	private static final int MAIN_SLOTS = 36;

	/** A carried focus and its code (see the class comment). */
	public record Carried(ItemStack stack, int code) {
	}

	private CasterFocusHelper() {
	}

	/** Inventory slots of the carried pouches, by pouch number (1, 2, ...). */
	private static Map<Integer, Integer> pouches(Player player) {
		Map<Integer, Integer> out = new LinkedHashMap<>();
		Inventory inv = player.getInventory();
		int n = 0;
		for (int a = 0; a < MAIN_SLOTS; a++) {
			if (inv.getItem(a).getItem() instanceof ItemFocusPouch) out.put(++n, a);
		}
		return out;
	}

	private static String sortKey(ItemStack stack) {
		FocusPackage p = stack.getItem() instanceof ItemFocus f ? f.getPackage(stack) : null;
		return stack.getHoverName().getString() + (p != null ? p.getSortingHelper() : 0);
	}

	/** Every focus carried in the main inventory or a pouch there, in the original's order. */
	public static List<Carried> findCarried(Player player) {
		List<Carried> out = new ArrayList<>();
		Inventory inv = player.getInventory();
		Map<Integer, Integer> pouches = pouches(player);
		for (int a = 0; a < MAIN_SLOTS; a++) {
			ItemStack item = inv.getItem(a);
			if (item.getItem() instanceof ItemFocus) out.add(new Carried(item, a));
		}
		for (var e : pouches.entrySet()) {
			NonNullList<ItemStack> contents = ItemFocusPouch.getContents(inv.getItem(e.getValue()));
			for (int q = 0; q < contents.size(); q++) {
				if (contents.get(q).getItem() instanceof ItemFocus) out.add(new Carried(contents.get(q), q + e.getKey() * 1000));
			}
		}
		out.sort(Comparator.comparing(c -> sortKey(c.stack())));
		return out;
	}

	public static List<ItemStack> findCarriedFoci(Player player) {
		return findCarried(player).stream().map(Carried::stack).toList();
	}

	/** The original's addFocusToPouch: into the first carried pouch with an empty slot. */
	private static boolean addToPouch(Player player, ItemStack focus, Map<Integer, Integer> pouches) {
		for (int slot : pouches.values()) {
			ItemStack pouch = player.getInventory().getItem(slot);
			NonNullList<ItemStack> contents = ItemFocusPouch.getContents(pouch);
			for (int q = 0; q < contents.size(); q++) {
				if (contents.get(q).isEmpty()) {
					contents.set(q, focus.copy());
					ItemFocusPouch.setContents(pouch, contents);
					return true;
				}
			}
		}
		return false;
	}

	private static boolean stash(Player player, ItemStack focus, Map<Integer, Integer> pouches) {
		return addToPouch(player, focus, pouches) || player.getInventory().add(focus.copy());
	}

	/** Binds the focus with this code to {@code casterStack}; a negative code (or nothing carried) takes the focus out. */
	public static void changeFocus(ItemStack casterStack, Player player, int code) {
		if (!(casterStack.getItem() instanceof ICaster caster)) return;
		Map<Integer, Integer> pouches = pouches(player);
		List<Carried> carried = findCarried(player);
		if (code < 0 || carried.isEmpty()) {
			if (caster.getFocus(casterStack) != null && stash(player, caster.getFocusStack(casterStack), pouches)) {
				caster.setFocus(casterStack, null);
				player.level().playSound(null, player.blockPosition(), thaumcraft.common.registry.ThaumcraftSounds.TICKS.get(), SoundSource.PLAYERS, 0.3f, 0.9f);
			}
			return;
		}
		if (carried.stream().noneMatch(c -> c.code() == code)) return; // stale or forged
		ItemStack item;
		if (code < 1000) {
			if (code >= MAIN_SLOTS) return;
			item = player.getInventory().getItem(code).copy();
			player.getInventory().setItem(code, ItemStack.EMPTY);
		} else {
			Integer pouchSlot = pouches.get(code / 1000);
			if (pouchSlot == null) return;
			ItemStack pouch = player.getInventory().getItem(pouchSlot);
			NonNullList<ItemStack> contents = ItemFocusPouch.getContents(pouch);
			int q = code % 1000;
			if (q >= contents.size() || !(contents.get(q).getItem() instanceof ItemFocus)) return;
			item = contents.get(q).copy();
			contents.set(q, ItemStack.EMPTY);
			ItemFocusPouch.setContents(pouch, contents);
		}
		if (item.isEmpty()) return;
		player.level().playSound(null, player.blockPosition(), thaumcraft.common.registry.ThaumcraftSounds.TICKS.get(), SoundSource.PLAYERS, 0.3f, 1.0f);
		if (caster.getFocus(casterStack) != null && stash(player, caster.getFocusStack(casterStack), pouches)) {
			caster.setFocus(casterStack, null);
		}
		if (caster.getFocus(casterStack) == null) {
			caster.setFocus(casterStack, item);
		} else {
			stash(player, item, pouches);
		}
	}

	/** Server-side handler for {@link ServerboundChangeCasterFocusPacket}: the caster in the main hand, else the off hand. */
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
