package thaumcraft.common.lib.research.theorycraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.items.curios.ItemCurio;

/** Replaces the old (1.12.2) {@code CardCurio} - a real Curio the player is carrying (from real inventory,
 * consumed); the original's own real per-variant bonus (arcane/preserved/ancient/eldritch/knowledge/twisted/
 * rites, via the old metadata damage value) is dropped and replaced with a flat bonus matching its own real
 * "default" fallback numbers - {@link ItemCurio}'s own doc comment already established that the modern
 * {@code ItemCurio} carries no variants at all (one real registry entry, not the original's 8 metadata
 * subtypes), so there's nothing left to switch on. See {@link CardInfuse}'s own doc comment for the real,
 * pre-existing NBT-round-trip gap this shares. */
public class CardCurio extends TheorycraftCard {

	private ItemStack curio = ItemStack.EMPTY;

	@Override
	public CompoundTag serialize() {
		CompoundTag nbt = super.serialize();
		nbt.put("stack", curio.save(net.minecraft.core.RegistryAccess.EMPTY, new CompoundTag()));
		return nbt;
	}

	@Override
	public void deserialize(CompoundTag nbt) {
		super.deserialize(nbt);
		curio = ItemStack.parseOptional(net.minecraft.core.RegistryAccess.EMPTY, nbt.getCompound("stack"));
	}

	@Override
	public int getInspirationCost() {
		return 1;
	}

	@Override
	public String getLocalizedName() {
		return Component.translatable("card.curio.name").getString();
	}

	@Override
	public String getLocalizedText() {
		return Component.translatable("card.curio.text").getString();
	}

	@Override
	public ItemStack[] getRequiredItems() {
		return new ItemStack[] { curio };
	}

	@Override
	public boolean[] getRequiredItemsConsumed() {
		return new boolean[] { true };
	}

	@Override
	public boolean initialize(Player player, ResearchTableData data) {
		Random r = new Random(getSeed());
		List<ItemStack> curios = new ArrayList<>();
		for (ItemStack stack : player.getInventory().items) {
			if (!stack.isEmpty() && stack.getItem() instanceof ItemCurio) {
				curios.add(stack.copyWithCount(1));
			}
		}
		if (!curios.isEmpty()) {
			curio = curios.get(r.nextInt(curios.size()));
		}
		return !curio.isEmpty();
	}

	@Override
	public boolean activate(Player player, ResearchTableData data) {
		data.addTotal("BASICS", 5);
		String[] cats = ResearchCategories.researchCategories.keySet().toArray(new String[0]);
		if (cats.length > 0) {
			data.addTotal(cats[player.getRandom().nextInt(cats.length)], 5);
		}
		// the curio's own field: the original switched on the variant name
		ItemCurio.Variant variant = curio.getItem() instanceof ItemCurio c ? c.getVariant() : null;
		if (variant == null) {
			data.addTotal("BASICS", Mth.nextInt(player.getRandom(), 25, 35));
		} else if (variant == ItemCurio.Variant.RITES) {
			data.addTotal("ELDRITCH", Mth.nextInt(player.getRandom(), 15, 20));
			data.addTotal("AUROMANCY", Mth.nextInt(player.getRandom(), 10, 15));
		} else {
			data.addTotal(variant.category, Mth.nextInt(player.getRandom(), 25, 35));
		}
		if (player.getRandom().nextBoolean()) data.bonusDraws++;
		if (player.getRandom().nextBoolean()) data.bonusDraws++;
		return true;
	}
}
