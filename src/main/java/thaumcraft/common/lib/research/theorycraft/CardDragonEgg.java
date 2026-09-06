package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardDragonEgg} - direct translation. Aid-only (see {@link AidDragonEgg})
 * - sprays 10 small (2-5) progress bumps across completely random categories. */
public class CardDragonEgg extends TheorycraftCard {

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public boolean isAidOnly() {
		return true;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.dragonegg.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.dragonegg.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		String[] cats = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
		if (cats.length == 0) return false;
		for (int i = 0; i < 10; i++) {
			String cat = cats[player.getRandom().nextInt(cats.length)];
			data.addTotal(cat, Mth.nextInt(player.getRandom(), 2, 5));
		}
		return true;
	}
}
