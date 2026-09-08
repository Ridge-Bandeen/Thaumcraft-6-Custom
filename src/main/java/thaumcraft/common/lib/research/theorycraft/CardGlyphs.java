package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardGlyphs} - direct translation. Aid-only (see {@code AidGlyphedStone},
 * still deferred - see that class's own doc comment) - a real random-category bump plus real ELDRITCH
 * progress, at the cost of 5 real Temporary Warp. */
public class CardGlyphs extends TheorycraftCard {

	@Override
	public boolean isAidOnly() {
		return true;
	}

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
		return Component.translatable("card.glyph.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.glyph.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		String[] cats = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
		if (cats.length == 0) return false;
		data.addTotal(cats[player.getRandom().nextInt(cats.length)], Mth.nextInt(player.getRandom(), 10, 20));
		data.addTotal("ELDRITCH", Mth.nextInt(player.getRandom(), 10, 20));
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, 5, IPlayerWarp.EnumWarpType.TEMPORARY);
		return true;
	}
}
