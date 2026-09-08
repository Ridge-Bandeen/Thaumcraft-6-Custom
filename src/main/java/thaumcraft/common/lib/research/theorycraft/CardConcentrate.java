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
import thaumcraft.common.items.resources.ItemCrystalEssence;

/** Replaces the old (1.12.2) {@code CardConcentrate} - direct translation. A real Crystal Essence of a
 * random compound {@link Aspect}, shown but not consumed; flat 15 ALCHEMY progress plus a bonus draw, and a
 * real 33% chance of 1 refunded inspiration. */
public class CardConcentrate extends TheorycraftCard {

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
		return "ALCHEMY";
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.concentrate.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.concentrate.text", ChatFormatting.BOLD + aspect.getName() + ChatFormatting.RESET).getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { ItemCrystalEssence.of(aspect) };
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal(getResearchCategory(), 15);
		data.bonusDraws++;
		if (player.getRandom().nextFloat() < 0.33f) {
			data.addInspiration(1);
		}
		return true;
	}
}
