package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardPortal} - direct translation. Aid-only (see {@link AidPortal}) -
 * two random-category bumps plus real ELDRITCH progress and 2 bonus draws, at the cost of real temporary
 * and permanent Warp - the same "power has a price" tradeoff every other ELDRITCH-flavored real content
 * this session has kept intact. */
public class CardPortal extends TheorycraftCard {

	@Override
	public boolean isAidOnly() {
		return true;
	}

	@Override
	public int getInspirationCost() {
		return -1;
	}

	@Override
	public String getResearchCategory() {
		return "ELDRITCH";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.portal.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.portal.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		String[] cats = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
		if (cats.length == 0) return false;
		data.addTotal(cats[player.getRandom().nextInt(cats.length)], Mth.nextInt(player.getRandom(), 5, 10));
		data.addTotal(cats[player.getRandom().nextInt(cats.length)], Mth.nextInt(player.getRandom(), 5, 10));
		data.addTotal("ELDRITCH", Mth.nextInt(player.getRandom(), 5, 10));
		data.bonusDraws += 2;
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, 5, IPlayerWarp.EnumWarpType.TEMPORARY);
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.NORMAL);
		return true;
	}
}
