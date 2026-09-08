package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/** Replaces the old (1.12.2) {@code CardScripting} - direct translation, updated to the real modern {@link
 * TileResearchTable}'s own real accessors ({@link TileResearchTable#getInk()}/{@link
 * TileResearchTable#getPaper()}/{@link TileResearchTable#consumeInkFromTable()}/{@link
 * TileResearchTable#consumePaperFromTable()}, replacing the original's own raw {@code getStackInSlot}/
 * {@code consumepaperFromTable}). Consumes an extra real ink+paper (on top of the real draw's own paper
 * cost) for flat 25 GOLEMANCY progress - fails outright if the table doesn't have both real ink capacity and
 * real paper spare. */
public class CardScripting extends TheorycraftCard {

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
		return Component.translatable("card.scripting.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.scripting.text").getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		if (!(data.table instanceof TileResearchTable table)) return false;
		if (table.getInk().isEmpty() || table.getInk().getDamageValue() >= table.getInk().getMaxDamage()) return false;
		if (table.getPaper().isEmpty()) return false;
		table.consumeInkFromTable();
		table.consumePaperFromTable();
		data.addTotal(getResearchCategory(), 25);
		return true;
	}
}
