package thaumcraft.api.crafting;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Processors the Infernal Furnace asks, in registration order, before its own {@code thaumcraft:infernal_smelting}
 * and then vanilla smelting recipes - for addons whose items are processed by some other system (another mod's
 * heating recipes, say). The furnace still adds its smelting bonuses (the {@code thaumcraft:smelting_bonus} data map)
 * on top of a processor's outputs, and an item no processor or recipe accepts is still destroyed, as the original.
 * Register from {@code ThaumcraftSetupEvent}.
 */
public final class InfernalFurnaceProcessors {

	public interface Processor {
		/** Whether the furnace should take this item in (and not burn it up). */
		boolean accepts(ServerLevel level, ItemStack input);

		/** The outputs for one of {@code input} (count 1), ejected out of the furnace's front; {@code null} when this
		 * processor doesn't handle it, so the next processor / recipe is tried. */
		@Nullable
		List<ItemStack> process(ServerLevel level, BlockPos pos, ItemStack input);

		/** How long one of {@code input} takes, given the furnace's own time (bellows and vis included). */
		default int cookTime(ServerLevel level, ItemStack input, int furnaceTime) {
			return furnaceTime;
		}
	}

	private static final List<Processor> PROCESSORS = new CopyOnWriteArrayList<>();

	private InfernalFurnaceProcessors() {
	}

	public static void register(Processor processor) {
		PROCESSORS.add(processor);
	}

	public static List<Processor> all() {
		return PROCESSORS;
	}

	@Nullable
	public static Processor accepting(ServerLevel level, ItemStack input) {
		for (Processor p : PROCESSORS) {
			if (p.accepts(level, input)) return p;
		}
		return null;
	}
}
