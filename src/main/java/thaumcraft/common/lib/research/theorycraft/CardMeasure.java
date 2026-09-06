package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardMeasure} - direct translation. Flat 15 INFUSION progress plus a
 * bonus draw - {@link CardCalibrate}'s own INFUSION counterpart. */
public class CardMeasure extends TheorycraftCard {

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "INFUSION";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.measure.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.measure.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 15);
		data.bonusDraws++;
		return true;
	}
}
