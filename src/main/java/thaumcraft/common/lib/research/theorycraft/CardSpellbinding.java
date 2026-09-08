package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardSpellbinding} - direct translation. Trades up to 5 real player XP
 * levels for real AUROMANCY progress (5 per level spent); {@link #initialize}/{@link #activate} both fail
 * outright if the player has no real XP levels at all. */
public class CardSpellbinding extends TheorycraftCard {

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "AUROMANCY";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.spellbinding.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.spellbinding.text").getString();
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		return player.experienceLevel > 0;
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		if (player.experienceLevel <= 0) return false;
		int levels = Math.min(5, player.experienceLevel);
		data.addTotal(getResearchCategory(), levels * 5);
		player.giveExperienceLevels(-levels);
		return true;
	}
}
