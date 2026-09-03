package thaumcraft.api.research.theorycraft;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;

/**
 * Replaces the old (1.12.2) {@code CardAnalyze}. Converts a category's OBSERVATION knowledge (gained by
 * scanning things - {@link thaumcraft.api.research.ScanningManager}) into real theory progress.
 *
 * Real bug fixed while translating, not preserved: the original's own {@code initialize} checked
 * {@code researchCategories.get(cat)} - {@code cat} is still {@code null} at that point in the loop (it's
 * only assigned afterward), so every real 1.12.2 game always evaluated knowledge against a null category
 * and this card could never actually find a candidate to draw against. Fixed to check the category
 * actually being iterated ({@code rc}), which is clearly what was intended.
 */
public class CardAnalyze extends TheorycraftCard {

	String cat = null;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("cat", cat == null ? "" : cat);
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
		ArrayList<String> cats = new ArrayList<>();
		for (ResearchCategory rc : ResearchCategories.researchCategories.values()) {
			if (rc.key.equals("BASICS")) continue;
			if (ThaumcraftCapabilities.getKnowledge(player).getKnowledge(EnumKnowledgeType.OBSERVATION, rc) > 0) {
				cats.add(rc.key);
			}
		}
		if (!cats.isEmpty()) {
			cat = cats.get(r.nextInt(cats.size()));
		}
		return cat != null;
	}

	@Override
	public int getInspirationCost() {
		return 2;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.analyze.name",
				ChatFormatting.DARK_BLUE.toString() + ChatFormatting.BOLD + Component.translatable("tc.research_category." + cat).getString() + ChatFormatting.RESET).getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.analyze.text",
				ChatFormatting.BOLD.toString() + Component.translatable("tc.research_category." + cat).getString() + ChatFormatting.RESET,
				ChatFormatting.BOLD.toString() + Component.translatable("tc.research_category.BASICS").getString() + ChatFormatting.RESET).getString();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		ResearchCategory rc = ResearchCategories.getResearchCategory(cat);
		int k = ThaumcraftCapabilities.getKnowledge(player).getKnowledge(EnumKnowledgeType.OBSERVATION, rc);
		if (k >= 1) {
			data.addTotal("BASICS", 5);
			ThaumcraftCapabilities.getKnowledge(player).addKnowledge(EnumKnowledgeType.OBSERVATION, rc, -EnumKnowledgeType.OBSERVATION.getProgression());
			data.addTotal(cat, Mth.nextInt(player.getRandom(), 25, 50));
			return true;
		}
		return false;
	}
}
