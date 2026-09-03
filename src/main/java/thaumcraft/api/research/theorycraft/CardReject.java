package thaumcraft.api.research.theorycraft;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Replaces the old (1.12.2) {@code CardReject} - direct translation. Blocks a currently-active category
 * from further progress/draws (its existing progress is kept). */
public class CardReject extends TheorycraftCard {

	private String cat1;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("cat", cat1 == null ? "" : cat1);
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		cat1 = nbt.getString("cat");
	}

	@Override
	public int getInspirationCost() {
		return 0;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.reject.name",
				ChatFormatting.DARK_BLUE.toString() + ChatFormatting.BOLD + Component.translatable("tc.research_category." + cat1).getString() + ChatFormatting.RESET + ChatFormatting.BOLD).getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.reject.text",
				ChatFormatting.BOLD.toString() + Component.translatable("tc.research_category." + cat1).getString() + ChatFormatting.RESET).getString();
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		ArrayList<String> s = new ArrayList<>();
		for (String c : data.categoryTotals.keySet()) {
			if (!data.categoriesBlocked.contains(c)) s.add(c);
		}
		if (s.isEmpty()) return false;
		Random r = new Random(getSeed());
		cat1 = s.get(r.nextInt(s.size()));
		return cat1 != null;
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		if (cat1 == null) return false;
		data.addTotal("BASICS", 5);
		data.categoriesBlocked.add(cat1);
		return true;
	}
}
