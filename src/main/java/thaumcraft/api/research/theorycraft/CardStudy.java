package thaumcraft.api.research.theorycraft;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces the old (1.12.2) {@code CardStudy} - direct translation, aid-only, adds real progress (15-25) to
 * a random available category.
 */
public class CardStudy extends TheorycraftCard {

	String cat = "BASICS";

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("cat", cat);
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		cat = nbt.getString("cat");
	}

	@Override
	public String getResearchCategory() {
		return cat;
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		Random r = new Random(getSeed());
		ArrayList<String> list = data.getAvailableCategories(player);
		cat = list.get(r.nextInt(list.size()));
		return cat != null;
	}

	@Override
	public boolean isAidOnly() {
		return true;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.study.name",
				ChatFormatting.DARK_BLUE.toString() + ChatFormatting.BOLD + Component.translatable("tc.research_category." + cat).getString() + ChatFormatting.RESET).getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.study.text",
				ChatFormatting.BOLD.toString() + Component.translatable("tc.research_category." + cat).getString() + ChatFormatting.RESET).getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(cat, Mth.nextInt(player.getRandom(), 15, 25));
		return true;
	}
}
