package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardFocus} - direct translation. Flat 15 AUROMANCY progress plus a
 * bonus draw. */
public class CardFocus extends TheorycraftCard {

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
		return Component.translatable("card.focus.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.focus.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 15);
		data.bonusDraws++;
		return true;
	}
}
