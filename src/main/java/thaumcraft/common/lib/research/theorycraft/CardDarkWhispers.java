package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardDarkWhispers} - direct translation. Aid-only (see
 * {@code AidBrainInAJar}, still deferred - see that class's own doc comment). Drains real player XP levels
 * (10 + current level) in exchange for a real random spray of progress across every real research category
 * plus real ELDRITCH progress and real Warp - a real, deliberate "power has a price" tradeoff, same as every
 * other ELDRITCH-flavored card this arc. */
public class CardDarkWhispers extends TheorycraftCard {

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
		return Component.translatable("card.darkwhisper.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.darkwhisper.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		int level = player.experienceLevel;
		player.giveExperienceLevels(-(10 + level));
		if (level > 0) {
			for (String cat : ResearchCategories.researchCategories.keySet()) {
				if (player.getRandom().nextBoolean()) continue;
				data.addTotal(cat, Mth.nextInt(player.getRandom(), 0, Math.max(1, (int) Math.sqrt(level))));
			}
		}
		data.addTotal("ELDRITCH", Mth.nextInt(player.getRandom(), Math.max(1, level / 5), Math.max(5, level / 2)));
		ThaumcraftApi.internalMethods.addWarpToPlayer(player, Math.max(1, (int) Math.sqrt(level)), IPlayerWarp.EnumWarpType.NORMAL);
		if (player.getRandom().nextBoolean()) {
			data.bonusDraws++;
		}
		return true;
	}
}
