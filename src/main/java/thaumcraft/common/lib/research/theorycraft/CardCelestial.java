package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/**
 * Replaces the old (1.12.2) {@code CardCelestial} - direct translation. Requires two real, different {@link
 * thaumcraft.common.items.curios.ItemCelestialNotes} variants (consumed automatically - real items handled
 * by {@code ResearchTableMenu} itself, matching {@code CardSculpting}'s own established pattern) and real
 * {@code CELESTIALSCANNING} research. Real payoff, keyed off which two variants came up: sun involved ->
 * real research penalty; moon involved -> a real bonus draw; a star quadrant involved -> real bonus ELDRITCH
 * progress plus a real temporary warp (matching the true original's own real
 * {@code ThaumcraftApi.internalMethods.addWarpToPlayer} call exactly).
 */
public class CardCelestial extends TheorycraftCard {

	private int variant1;
	private int variant2;
	private String category = "BASICS";

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putInt("v1", variant1);
		nbt.putInt("v2", variant2);
		nbt.putString("cat", category);
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		variant1 = nbt.getInt("v1");
		variant2 = nbt.getInt("v2");
		category = nbt.getString("cat");
	}

	@Override
	public String getResearchCategory() {
		return category;
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		if (data.categoryTotals.isEmpty() || !ThaumcraftCapabilities.knowsResearch(player, "CELESTIALSCANNING")) {
			return false;
		}
		RandomSource random = RandomSource.create(getSeed());
		variant1 = Mth.nextInt(random, 0, 12);
		variant2 = variant1;
		while (variant1 == variant2) {
			variant2 = Mth.nextInt(random, 0, 12);
		}
		int highestTotal = 0;
		String highestCategory = null;
		for (String cat : data.categoryTotals.keySet()) {
			int total = data.getTotal(cat);
			if (total > highestTotal) {
				highestTotal = total;
				highestCategory = cat;
			}
		}
		category = highestCategory;
		return category != null;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.celestial.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.celestial.text",
				Component.translatable("tc.research_category." + category).getString()).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] {
				new ItemStack(ItemsTC.celestialNotes.get(variant1).get()),
				new ItemStack(ItemsTC.celestialNotes.get(variant2).get())
		};
	}

	@Override
	public boolean[] getRequiredItemsConsumed() {
		return new boolean[] { true, true };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), Mth.nextInt(player.getRandom(), 25, 50));
		boolean sun = variant1 == 0 || variant2 == 0;
		boolean moon = variant1 > 4 || variant2 > 4;
		boolean stars = (variant1 > 0 && variant1 < 5) || (variant2 > 0 && variant2 < 5);
		if (stars) {
			int amount = Mth.nextInt(player.getRandom(), 0, 5);
			data.addTotal("ELDRITCH", amount * 2);
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, amount, IPlayerWarp.EnumWarpType.TEMPORARY);
		}
		if (sun) {
			data.penaltyStart++;
		}
		if (moon) {
			data.bonusDraws++;
		}
		return true;
	}
}
