package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;

/**
 * The essentia cost of a potion, for the Potion Sprayer - a port of the original's {@code
 * ConfigAspects#getPotionAspects}, which traced a potion back through its brewing ingredients: each reagent
 * contributes its own object aspects plus 3 Alchemy, and the total is then cut by 66%. A potion with no known
 * recipe costs 5 Magic + 5 Alchemy, a plain water bottle 5 Water. (The port had charged a flat 10 Alchemy for every
 * potion, because the object-tag database it depends on did not exist when the sprayer was ported.)
 *
 * <p>The reagent chain comes from vanilla's {@link PotionBrewing}: it can only be queried forwards ("what does this
 * ingredient turn that potion into"), so the reverse map is built once per brewing registry by trying every
 * potion-ingredient item against every potion, and remembering the first ingredient that produces each result.
 */
public final class PotionAspects {

	private record Source(Item ingredient, Holder<net.minecraft.world.item.alchemy.Potion> from) {
	}

	private static final Map<PotionBrewing, Map<ResourceLocation, Source>> CACHE = new WeakHashMap<>();

	private PotionAspects() {
	}

	public static AspectList of(Level level, ItemStack stack) {
		AspectList result = new AspectList();
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		Optional<Holder<net.minecraft.world.item.alchemy.Potion>> potion = contents == null ? Optional.empty() : contents.potion();
		if (potion.isPresent() && potion.get().is(Potions.WATER)) {
			result.add(Aspect.WATER, 5);
			return result;
		}

		List<Item> reagents = new ArrayList<>();
		potion.ifPresent(p -> collectReagents(level.potionBrewing(), p, reagents, new HashSet<>()));

		boolean traced = false;
		for (Item reagent : reagents) {
			AspectList aspects = AspectHelper.getObjectAspects(new ItemStack(reagent));
			if (aspects != null) {
				for (Aspect aspect : aspects.getAspects()) {
					result.add(aspect, aspects.getAmount(aspect));
				}
			}
			traced = true;
			result.add(Aspect.ALCHEMY, 3);
		}
		for (Aspect aspect : result.getAspects()) {
			result.remove(aspect, (int) (result.getAmount(aspect) * 0.66));
		}
		if (!traced) {
			result.add(Aspect.MAGIC, 5);
			result.add(Aspect.ALCHEMY, 5);
		}
		return result;
	}

	private static void collectReagents(PotionBrewing brewing, Holder<net.minecraft.world.item.alchemy.Potion> potion, List<Item> out, Set<ResourceLocation> visited) {
		ResourceLocation id = potion.unwrapKey().map(k -> k.location()).orElse(null);
		if (id == null || !visited.add(id)) return;
		Source source = sources(brewing).get(id);
		if (source == null) return;
		out.add(source.ingredient());
		if (!source.from().is(Potions.WATER)) {
			collectReagents(brewing, source.from(), out, visited);
		}
	}

	private static synchronized Map<ResourceLocation, Source> sources(PotionBrewing brewing) {
		return CACHE.computeIfAbsent(brewing, b -> {
			Map<ResourceLocation, Source> map = new HashMap<>();
			for (Item item : BuiltInRegistries.ITEM) {
				ItemStack ingredient = new ItemStack(item);
				if (!b.isPotionIngredient(ingredient)) continue;
				for (Holder.Reference<net.minecraft.world.item.alchemy.Potion> from : (Iterable<Holder.Reference<net.minecraft.world.item.alchemy.Potion>>) BuiltInRegistries.POTION.holders()::iterator) {
					ItemStack input = PotionContents.createItemStack(Items.POTION, from);
					ItemStack output = b.mix(ingredient, input);
					PotionContents made = output.get(DataComponents.POTION_CONTENTS);
					if (made == null || made.potion().isEmpty()) continue;
					Holder<net.minecraft.world.item.alchemy.Potion> to = made.potion().get();
					if (to.equals(from)) continue;
					to.unwrapKey().ifPresent(key -> map.putIfAbsent(key.location(), new Source(item, from)));
				}
			}
			return map;
		});
	}
}
