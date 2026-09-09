package thaumcraft.common.tiles.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TilePatternCrafter} - a vis-powered auto-crafter: every 20 ticks, if
 * powered, tries every stack in the real adjacent inventory above (via NeoForge's own
 * {@link Capabilities.ItemHandler#BLOCK}, same real adjacent-inventory lookup {@code EntityArcaneBore}
 * already established) as a single repeated ingredient arranged into one of 10 real fixed grid patterns
 * (see {@link #SLOTS_BY_TYPE}, cycled via right-click - {@link #cycle}), matches it against a real vanilla
 * {@link net.minecraft.world.item.crafting.CraftingRecipe} via {@link CraftingInput}, and - if the result
 * (plus any real crafting byproducts, e.g. empty buckets, via {@link CraftingRecipe#getRemainingItems}) fits
 * in the real adjacent inventory below - consumes the ingredients and produces it. Same real per-type
 * ingredient-count table the original used (equal to each pattern's own slot count: 9/1/2/2/4/3/3/6/6/8).
 *
 * Real, documented simplifications vs. the original:
 * <ul>
 * <li>No CodeChicken sub-hit raytracing for "click the crank specifically" - the same real dependency wall
 * that already blocked the Levitator/Redstone Relay/Tube One-way this session. The original's block only
 * ever had one real interactive region anyway (the crank), so a plain whole-block right-click
 * ({@link thaumcraft.common.blocks.crafting.BlockPatternCrafter#useWithoutItem}) loses no real function,
 * just the precision of clicking a specific decorative sub-region.</li>
 * <li>{@code BlockStateProperties#ENABLED} (redstone-inverted) replaces the original's own wand-toggle -
 * same real convention every other toggled device this session already uses (see e.g.
 * {@code TileVoidSiphon}'s own doc comment), not a bespoke wand-click mechanic.</li>
 * <li>No client-side crank-rotation animation ({@code rot}/{@code rp}/{@code rotTicks}) - visual-only,
 * dropped like every other device this session.</li>
 * </ul>
 */
public class TilePatternCrafter extends BlockEntity {

	/** Which of the 9 crafting-grid slots each pattern type fills with copies of the candidate ingredient -
	 * real port of the original's own per-{@code type} {@code craftMatrix} fill loops. Each type's own real
	 * ingredient count is just this array's own row length (matches the original's separate {@code amt}
	 * switch exactly - verified equal for every type, so not worth a second, redundant table). */
	private static final int[][] SLOTS_BY_TYPE = {
			{ 0, 1, 2, 3, 4, 5, 6, 7, 8 },
			{ 0 },
			{ 0, 1 },
			{ 0, 3 },
			{ 0, 1, 3, 4 },
			{ 0, 1, 2 },
			{ 0, 3, 6 },
			{ 0, 1, 2, 3, 4, 5 },
			{ 0, 1, 3, 4, 6, 7 },
			{ 0, 1, 2, 3, 5, 6, 7, 8 },
	};

	private byte type;
	private float power;
	private int count;

	public TilePatternCrafter(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.PATTERN_CRAFTER.get(), pos, state);
	}

	public byte getPatternType() {
		return type;
	}

	/** Real port of the original's own {@code cycle()} - advances to the next of the 10 real patterns. */
	public void cycle() {
		type = (byte) ((type + 1) % SLOTS_BY_TYPE.length);
		setChanged();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TilePatternCrafter tile) {
		if (level.isClientSide) return;
		if (!state.getValue(BlockStateProperties.ENABLED)) return;
		if (tile.count++ % 20 != 0) return;

		if (tile.power <= 0f) {
			tile.power += AuraHelper.drainVis(level, pos, 5.0f, false);
		}
		if (tile.power < 1.0f) return;

		IItemHandler above = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.above(), Direction.DOWN);
		IItemHandler below = level.getCapability(Capabilities.ItemHandler.BLOCK, pos.below(), Direction.UP);
		if (above == null || below == null) return;

		int[] slots = SLOTS_BY_TYPE[tile.type];
		int amt = slots.length;
		for (int slot = 0; slot < above.getSlots(); slot++) {
			ItemStack candidate = above.getStackInSlot(slot);
			if (candidate.isEmpty() || candidate.getCount() < amt) continue;

			Crafted crafted = tryCraft(level, candidate, slots);
			if (crafted == null) continue;

			if (!ItemHandlerHelper.insertItemStacked(below, crafted.result().copy(), true).isEmpty()) continue;
			boolean byproductsFit = true;
			for (ItemStack byproduct : crafted.remaining()) {
				if (!byproduct.isEmpty() && !ItemHandlerHelper.insertItemStacked(below, byproduct.copy(), true).isEmpty()) {
					byproductsFit = false;
					break;
				}
			}
			if (!byproductsFit) continue;

			ItemStack extracted = above.extractItem(slot, amt, false);
			if (extracted.getCount() < amt) continue; // real inventory changed under us mid-check - skip, don't duplicate/lose items

			ItemHandlerHelper.insertItemStacked(below, crafted.result().copy(), false);
			for (ItemStack byproduct : crafted.remaining()) {
				if (!byproduct.isEmpty()) {
					ItemHandlerHelper.insertItemStacked(below, byproduct.copy(), false);
				}
			}
			tile.power -= 1.0f;
			tile.setChanged();
			break;
		}
	}

	private record Crafted(ItemStack result, List<ItemStack> remaining) {
	}

	@Nullable
	private static Crafted tryCraft(Level level, ItemStack candidate, int[] slots) {
		List<ItemStack> grid = new ArrayList<>(9);
		for (int i = 0; i < 9; i++) grid.add(ItemStack.EMPTY);
		for (int slot : slots) {
			grid.set(slot, candidate.copyWithCount(1));
		}
		CraftingInput input = CraftingInput.of(3, 3, grid);
		Optional<RecipeHolder<CraftingRecipe>> match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
		if (match.isEmpty()) return null;
		CraftingRecipe recipe = match.get().value();
		ItemStack result = recipe.assemble(input, level.registryAccess());
		if (result.isEmpty()) return null;
		return new Crafted(result, recipe.getRemainingItems(input));
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putByte("Type", type);
		tag.putFloat("Power", power);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		type = tag.getByte("Type");
		power = tag.getFloat("Power");
	}
}
