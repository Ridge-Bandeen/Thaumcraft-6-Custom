package thaumcraft.api.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Replaces the old (1.12.2) {@code CardRethink} - direct translation. Trades up to 10 total progress
 * (spread across every active category) for 1 refunded inspiration, a bonus draw, and 1-10 real BASICS
 * progress. The inner loop's break-after-remove ordering (removing a category from {@code categoryTotals}
 * mid-iteration, then immediately breaking rather than continuing the same iterator) is preserved exactly -
 * it never actually continues iterating after the structural change, so it's safe as written. */
public class CardRethink extends TheorycraftCard {

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		int a = 0;
		for (String category : data.categoryTotals.keySet()) {
			a += data.getTotal(category);
		}
		return a >= 10;
	}

	@Override
	public int getInspirationCost() {
		return -1;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.rethink.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.rethink.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		if (!initialize(player, data)) return false;
		int a = 0;
		for (String category : data.categoryTotals.keySet()) {
			a += data.getTotal(category);
		}
		a = Math.min(a, 10);
		int tries = 0;
		while (a > 0 && tries < 1000) {
			tries++;
			for (String category : data.categoryTotals.keySet()) {
				data.addTotal(category, -1);
				a--;
				if (a <= 0 || !data.hasTotal(category)) break;
			}
		}
		data.bonusDraws++;
		data.addTotal("BASICS", Mth.nextInt(player.getRandom(), 1, 10));
		return true;
	}
}
