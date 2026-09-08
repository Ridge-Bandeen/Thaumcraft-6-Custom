package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/** Replaces the old (1.12.2) {@code CardReactions} - direct translation. Two real Crystal Essences of two
 * different random compound {@link Aspect}s, shown but not consumed; flat 25 ALCHEMY progress and a real
 * 33% chance of 1 refunded inspiration. */
public class CardReactions extends TheorycraftCard {

	private Aspect aspect1;
	private Aspect aspect2;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("aspect1", aspect1.getTag());
		nbt.putString("aspect2", aspect2.getTag());
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		aspect1 = Aspect.getAspect(nbt.getString("aspect1"));
		aspect2 = Aspect.getAspect(nbt.getString("aspect2"));
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		RandomSource r = RandomSource.create(getSeed());
		int num = Mth.nextInt(r, 0, Aspect.getCompoundAspects().size() - 1);
		aspect1 = Aspect.getCompoundAspects().get(num);
		int num2 = num;
		while (num2 == num) {
			num2 = Mth.nextInt(r, 0, Aspect.getCompoundAspects().size() - 1);
		}
		aspect2 = Aspect.getCompoundAspects().get(num2);
		return true;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "ALCHEMY";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.reactions.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.reactions.text",
				ChatFormatting.BOLD + aspect1.getName() + ChatFormatting.RESET,
				ChatFormatting.BOLD + aspect2.getName() + ChatFormatting.RESET).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { ItemCrystalEssence.of(aspect1), ItemCrystalEssence.of(aspect2) };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 25);
		if (player.getRandom().nextFloat() < 0.33f) {
			data.addInspiration(1);
		}
		return true;
	}
}
