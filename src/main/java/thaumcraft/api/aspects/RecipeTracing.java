package thaumcraft.api.aspects;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * Extra recipe sources for the aspect tracer, for addons whose items are made by their own recipe types. When an item
 * has no explicit aspects and no crucible or infusion recipe, Thaumcraft prices it from the cheapest way of making it:
 * crafting, arcane, stonecutting and smithing recipes, plus whatever the sources registered here offer. Each
 * {@link TracedRecipe} is treated exactly like a crafting recipe: three quarters of its ingredients' aspects, divided by
 * the output count, less what's handed back, plus {@link TracedRecipe#extra}. Sources are asked at trace time (recipes
 * exist then, not at setup); register from {@code ThaumcraftSetupEvent} or earlier. Nothing changes without a source.
 */
public final class RecipeTracing {

	@FunctionalInterface
	public interface RecipeTraceSource {
		/** Every way this source makes {@code item}: offer each to {@code out}. */
		void collect(RecipeManager recipes, RegistryAccess access, Item item, Consumer<TracedRecipe> out);
	}

	/**
	 * One way of making an item.
	 *
	 * @param ingredients what goes in; each ingredient's first item counts once per entry, as in crafting
	 * @param output      what comes out; its count divides the result
	 * @param remaining   what's handed back (buckets and the like); subtracted
	 * @param extra       aspects the process itself adds (like an arcane recipe's vis), added after the 0.75 loss; may be null
	 */
	public record TracedRecipe(List<Ingredient> ingredients, ItemStack output, List<ItemStack> remaining, AspectList extra) {

		public TracedRecipe(List<Ingredient> ingredients, ItemStack output) {
			this(ingredients, output, List.of(), null);
		}
	}

	private static final List<RecipeTraceSource> SOURCES = new CopyOnWriteArrayList<>();

	private RecipeTracing() {
	}

	public static void addSource(RecipeTraceSource source) {
		SOURCES.add(source);
	}

	/** The registered sources, in registration order - read by the tracer. */
	public static List<RecipeTraceSource> sources() {
		return SOURCES;
	}
}
