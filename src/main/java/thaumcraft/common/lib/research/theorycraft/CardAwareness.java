package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code CardAwareness} - direct translation. Flat 20 AUROMANCY progress; a real
 * 33% chance of a small ELDRITCH bump plus 1 real Normal Warp. */
public class CardAwareness extends TheorycraftCard {

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
		return Component.translatable("card.awareness.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.awareness.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 20);
		if (player.getRandom().nextFloat() < 0.33f) {
			data.addTotal("ELDRITCH", Mth.nextInt(player.getRandom(), 1, 5));
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.NORMAL);
		}
		return true;
	}
}
