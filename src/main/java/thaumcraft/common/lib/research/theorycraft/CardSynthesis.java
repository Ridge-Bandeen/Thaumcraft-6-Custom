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

/** Replaces the old (1.12.2) {@code CardSynthesis} - direct translation. Real Crystal Essences of a random
 * compound {@link Aspect}'s own 2 real components, both consumed; hands back a real crystal of the compound
 * aspect itself (to the player's inventory, or dropped if it's full), plus flat 40 ALCHEMY progress and a
 * real 33% chance of 1 refunded inspiration. */
public class CardSynthesis extends TheorycraftCard {

	private Aspect aspect1;
	private Aspect aspect2;
	private Aspect aspect3;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("aspect1", aspect1.getTag());
		nbt.putString("aspect2", aspect2.getTag());
		nbt.putString("aspect3", aspect3.getTag());
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		aspect1 = Aspect.getAspect(nbt.getString("aspect1"));
		aspect2 = Aspect.getAspect(nbt.getString("aspect2"));
		aspect3 = Aspect.getAspect(nbt.getString("aspect3"));
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		RandomSource r = RandomSource.create(getSeed());
		int num = Mth.nextInt(r, 0, Aspect.getCompoundAspects().size() - 1);
		aspect3 = Aspect.getCompoundAspects().get(num);
		aspect1 = aspect3.getComponents()[0];
		aspect2 = aspect3.getComponents()[1];
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
		return Component.translatable("card.synthesis.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.synthesis.text",
				ChatFormatting.BOLD + aspect1.getName() + ChatFormatting.RESET,
				ChatFormatting.BOLD + aspect2.getName() + ChatFormatting.RESET).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { ItemCrystalEssence.of(aspect1), ItemCrystalEssence.of(aspect2) };
	}

	@Override
	public boolean[] getRequiredItemsConsumed() {
		return new boolean[] { true, true };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		ItemStack result = ItemCrystalEssence.of(aspect3);
		data.addTotal(getResearchCategory(), 40);
		if (player.getRandom().nextFloat() < 0.33f) {
			data.addInspiration(1);
		}
		if (!player.getInventory().add(result)) {
			player.drop(result, true);
		}
		return true;
	}
}
