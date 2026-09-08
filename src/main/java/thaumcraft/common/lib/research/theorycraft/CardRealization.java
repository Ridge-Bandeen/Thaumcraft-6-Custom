package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardRealization} - direct translation. Two real random-category bumps
 * plus real ELDRITCH progress, at the cost of 5 real Temporary Warp and a 50% chance of 1 real Normal
 * Warp. */
public class CardRealization extends TheorycraftCard {

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "ELDRITCH";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.realization.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.realization.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		String[] cats = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
		if (cats.length == 0) return false;
		data.addTotal(cats[player.getRandom().nextInt(cats.length)], Mth.nextInt(player.getRandom(), 5, 10));
		data.addTotal(cats[player.getRandom().nextInt(cats.length)], Mth.nextInt(player.getRandom(), 5, 10));
		data.addTotal("ELDRITCH", 15);
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, 5, IPlayerWarp.EnumWarpType.TEMPORARY);
		if (player.getRandom().nextBoolean()) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.NORMAL);
		}
		return true;
	}
}
