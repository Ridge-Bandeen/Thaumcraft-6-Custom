package thaumcraft.common.lib.research.theorycraft;

import java.util.Random;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.items.consumables.ItemPhial;

/** Replaces the old (1.12.2) {@code CardChannel} - direct translation. A real filled Phial of a random real
 * compound {@link Aspect}, shown but not consumed (matches the original's own real default - only the ones
 * that explicitly override {@code getRequiredItemsConsumed} consume anything); flat 25 INFUSION progress. */
public class CardChannel extends TheorycraftCard {

	private Aspect aspect;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.putString("aspect", aspect.getTag());
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		aspect = Aspect.getAspect(nbt.getString("aspect"));
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		Random r = new Random(getSeed());
		aspect = Aspect.getCompoundAspects().get(r.nextInt(Aspect.getCompoundAspects().size()));
		return true;
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getResearchCategory() {
		return "INFUSION";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.channel.name", ChatFormatting.DARK_BLUE + aspect.getName() + ChatFormatting.RESET + "" + ChatFormatting.BOLD).getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.channel.text", ChatFormatting.BOLD + aspect.getName() + ChatFormatting.RESET).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { ItemPhial.makeFilledPhial(aspect) };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 25);
		return true;
	}
}
