package thaumcraft.api.research;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Replaces the old (1.12.2) {@code ScanOreDictionary} - restored 2026-09-27 with the original's full scan list
 * (see {@code thaumcraft.common.lib.research.ThaumcraftScans}). The original matched a block/item/dropped item by
 * ore-dictionary name ({@code "ingotIron"}, {@code "plateBrass"}...); the ore dictionary is gone, so each entry here
 * is either an item tag (the modern {@code c:} tags cover the vanilla names) or a specific {@link Item} (for
 * Thaumcraft's own plates and metals, which the original registered under ore names nothing else used). Same three
 * things matched as the original: a block in the world (by its item form), an item stack, a dropped item.
 */
public class ScanOreDictionary implements IScanThing {

	private final String research;
	private final List<Object> entries;

	/** @param entries each a {@code TagKey<Item>} or an {@link Item} */
	public ScanOreDictionary(String research, Object... entries) {
		this.research = research;
		this.entries = List.of(entries);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean checkThing(Player player, Object obj) {
		ItemStack stack = ItemStack.EMPTY;
		if (obj instanceof BlockPos pos) stack = new ItemStack(player.level().getBlockState(pos).getBlock().asItem());
		else if (obj instanceof ItemStack is) stack = is;
		else if (obj instanceof ItemEntity item) stack = item.getItem();
		if (stack.isEmpty()) return false;
		for (Object entry : entries) {
			if (entry instanceof TagKey<?> tag && stack.is((TagKey<Item>) tag)) return true;
			if (entry instanceof Item item && stack.is(item)) return true;
		}
		return false;
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
