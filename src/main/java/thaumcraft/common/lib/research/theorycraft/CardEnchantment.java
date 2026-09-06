package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardEnchantment} - direct translation. Aid-only (see
 * {@link AidEnchantmentTable}) - trades 5 real player XP levels for real INFUSION+AUROMANCY progress;
 * {@link #activate} fails outright (no partial effect) if the player doesn't have 5 levels to spend. */
public class CardEnchantment extends TheorycraftCard {

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
		return Component.translatable("card.enchantment.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.enchantment.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		if (player.experienceLevel < 5) return false;
		player.giveExperienceLevels(-5);
		data.addTotal("INFUSION", Mth.nextInt(player.getRandom(), 15, 20));
		data.addTotal("AUROMANCY", Mth.nextInt(player.getRandom(), 15, 20));
		return true;
	}
}
