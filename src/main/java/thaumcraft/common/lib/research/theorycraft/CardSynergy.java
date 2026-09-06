package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardSynergy} - direct translation. Needs at least 15 combined
 * ARTIFICE+ALCHEMY+INFUSION progress; converts up to 15 of it (spread across those three) into 30 real
 * GOLEMANCY progress. */
public class CardSynergy extends TheorycraftCard {

	private static final String[] SOURCE_CATS = {"ARTIFICE", "ALCHEMY", "INFUSION"};

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "GOLEMANCY";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.synergy.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.synergy.text").getString();
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		return combinedTotal(data) >= 15;
	}

	private int combinedTotal(ResearchTableData data) {
		int total = 0;
		for (String cat : SOURCE_CATS) total += data.getTotal(cat);
		return total;
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		int total = combinedTotal(data);
		if (total < 15) return false;
		int toSpend = 15;
		int tries = 0;
		while (toSpend > 0 && tries < 1000) {
			tries++;
			for (String cat : SOURCE_CATS) {
				if (data.getTotal(cat) > 0) {
					data.addTotal(cat, -1);
					if (--toSpend <= 0) break;
				}
			}
		}
		data.addTotal("GOLEMANCY", 30);
		data.penaltyStart++;
		return true;
	}
}
