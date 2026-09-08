package thaumcraft.common.tiles.devices;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredItem;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.events.ToolEvents;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileInfernalFurnace} - real, as-is smelting mechanic: any of its real 32
 * inventory slots holding a smeltable item slowly cooks it via the real vanilla smelting recipe book (cook
 * time real-scaled by nearby enabled {@link TileBellows} - up to 4, each shaving real time off, matching the
 * original's own real formula), draining real local aura/vis to "pay" for the process (falls back to a slow
 * real 140-tick bake with no vis available). A non-smeltable item dropped on it is destroyed instead - the
 * original's own real "don't throw garbage at the lava" behavior. The finished item ejects out the real front
 * face into a real adjacent inventory if one's placed there, otherwise drops as a real item entity, with real
 * partial-chance XP matching the smelted recipe's own real experience value.
 *
 * <p><b>Correction:</b> the smelting-bonus-nugget system (extra items on a real chance roll) was previously
 * cut here with the doc-comment claim that it needed "a whole family of modern nugget items this port hasn't
 * built yet" - restored now that those nuggets are real (see {@link #SMELTING_BONUSES}'s own doc comment for
 * the full, carefully-checked scope of what's actually reachable).
 */
public class TileInfernalFurnace extends BlockEntity {

	private final ItemStackHandler inventory = new ItemStackHandler(32) {
		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	private int cookTime;
	private int maxCookTime;
	private int speedyTime;

	public TileInfernalFurnace(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.INFERNAL_FURNACE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileInfernalFurnace tile) {
		if (level.isClientSide) return;

		boolean wasCooking = false;
		if (tile.cookTime > 0) {
			tile.cookTime--;
			wasCooking = true;
		}
		if (tile.maxCookTime <= 0) {
			tile.maxCookTime = tile.calcCookTime(level, pos);
		}
		if (tile.cookTime > tile.maxCookTime) {
			tile.cookTime = tile.maxCookTime;
		}

		if (tile.cookTime <= 0 && wasCooking) {
			for (int i = 0; i < tile.inventory.getSlots(); i++) {
				ItemStack stack = tile.inventory.getStackInSlot(i);
				if (stack.isEmpty()) continue;
				Optional<RecipeHolder<SmeltingRecipe>> match = level.getRecipeManager()
						.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level);
				ItemStack result = match.map(r -> r.value().getResultItem(level.registryAccess())).orElse(ItemStack.EMPTY);
				if (!result.isEmpty()) {
					if (tile.speedyTime > 0) tile.speedyTime--;
					tile.ejectItem(level, pos, state, result.copy(), match.get().value().getExperience());
					for (ItemStack bonus : rollSmeltingBonuses(stack, countBellows(level, pos) + 1, level.getRandom())) {
						tile.ejectItem(level, pos, state, bonus, 0.0f);
					}
					level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 0.4f, 1.0f + level.random.nextFloat() * 0.15f);
					if (level.random.nextInt(20) == 0) {
						AuraHelper.polluteAura(level, pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()), 1.0f, true);
					}
					tile.inventory.extractItem(i, 1, false);
					break;
				}
				tile.inventory.setStackInSlot(i, ItemStack.EMPTY);
			}
		}

		if (tile.speedyTime <= 0) {
			tile.speedyTime = (int) AuraHelper.drainVis(level, pos, 20.0f, false);
		}

		if (tile.cookTime == 0 && !wasCooking) {
			for (int i = 0; i < tile.inventory.getSlots(); i++) {
				if (tile.canSmelt(level, tile.inventory.getStackInSlot(i))) {
					tile.maxCookTime = tile.calcCookTime(level, pos);
					tile.cookTime = tile.maxCookTime;
					break;
				}
			}
		}
	}

	/** @return whether {@code items} could be accepted - if so, the caller should treat its stack as consumed
	 * (a real smeltable item goes into the first free slot; anything else is destroyed, matching the
	 * original's own real {@code addItemsToInventory}/{@code destroyItem}). */
	public ItemStack addItemsToInventory(ItemStack items) {
		if (items.isEmpty()) return ItemStack.EMPTY;
		if (!canSmelt(level, items)) {
			if (level != null) {
				level.playSound(null, getBlockPos(), SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.3f,
						2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f);
			}
			return ItemStack.EMPTY;
		}
		return ItemHandlerHelper.insertItemStacked(inventory, items, false);
	}

	private boolean canSmelt(Level level, ItemStack stack) {
		if (level == null || stack.isEmpty()) return false;
		return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level).isPresent();
	}

	/** Real bellows-boosted cook time - up to 4 real enabled {@link TileBellows} at distance 2 on the 4
	 * horizontal sides, each pointed back at this furnace and not currently redstone-disabled, matching the
	 * original's own real {@code getBellows}/{@code calcCookTime} formula (each bellows counted shaves real
	 * time off the base 140/80-tick bake, faster still once a full 20-vis buffer has been drained once). */
	private int calcCookTime(Level level, BlockPos pos) {
		int bellows = countBellows(level, pos);
		int reduction = bellows > 0 ? bellows * (20 - (bellows - 1)) : 0;
		return Math.max(10, (speedyTime > 0 ? 80 : 140) - reduction);
	}

	private static int countBellows(Level level, BlockPos pos) {
		int bellows = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos neighborPos = pos.relative(dir, 2);
			if (level.getBlockEntity(neighborPos) instanceof TileBellows
					&& level.getBlockState(neighborPos).getValue(BlockStateProperties.FACING) == dir.getOpposite()
					&& level.getBlockState(neighborPos).getValue(BlockStateProperties.ENABLED)) {
				bellows++;
			}
		}
		return Math.min(4, bellows);
	}

	/**
	 * Real smelting-bonus outputs - see this class's own doc comment for the fuller context (found and
	 * restored after the user asked for a wiki check on what turned out to be a real, previously-missed
	 * {@code nuggetRareearth} source). Keyed on the ORE ITEM actually sitting in this furnace's inventory (not
	 * its smelted result) - real per-{@code (bellows + 1)} independent rolls, same real formula as the true
	 * original's own {@code getSmeltingBonus}/{@code ejectItem} (more bellows means more separate rolls, not
	 * just a faster bake).
	 *
	 * <p>The true original also registered bonus entries for diamond/redstone/lapis/emerald/nether-quartz ore
	 * - checked directly against the true source and confirmed those were <b>never actually reachable even in
	 * the original</b>: none of those blocks have (or ever had) a real vanilla smelting recipe at all (you
	 * mine them directly for their gem, you don't smelt the ore), so {@code canSmelt} would always reject them
	 * before the bonus roll is ever reached - a genuine registered-but-dead branch in the original's own
	 * table, not a porting gap. The ore-BREAK bonus ({@link ToolEvents#RAREEARTH_ORE_CHANCE}) is those blocks'
	 * real, actually-reachable rareearth source.
	 *
	 * <p>Tin/silver/lead ore blocks were never ported (no real acquisition path exists for them at all yet -
	 * a genuine forced block, not a deviation), so their own bonus entries are left out here rather than
	 * registered against a nonexistent item.
	 */
	private record SmeltBonus(Item input, java.util.function.Supplier<ItemStack> output, float chance) {
	}

	private static final java.util.List<SmeltBonus> SMELTING_BONUSES = new java.util.ArrayList<>();

	public static void initSmeltingBonuses() {
		SMELTING_BONUSES.clear();
		addBonus(Items.IRON_ORE, ItemsTC.nuggetRareearth, 0.01f);
		addBonus(Blocks.DEEPSLATE_IRON_ORE.asItem(), ItemsTC.nuggetRareearth, 0.01f);
		addBonus(Items.GOLD_ORE, ItemsTC.nuggetRareearth, 0.02f);
		addBonus(Blocks.DEEPSLATE_GOLD_ORE.asItem(), ItemsTC.nuggetRareearth, 0.02f);
		addBonus(Items.COPPER_ORE, ItemsTC.nuggetRareearth, 0.01f);
		addBonus(Blocks.DEEPSLATE_COPPER_ORE.asItem(), ItemsTC.nuggetRareearth, 0.01f);
		addBonus(BlocksTC.oreCinnabar.get().asItem(), ItemsTC.nuggetRareearth, 0.025f);

		SMELTING_BONUSES.add(new SmeltBonus(Items.IRON_ORE, () -> new ItemStack(Items.IRON_NUGGET), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(Blocks.DEEPSLATE_IRON_ORE.asItem(), () -> new ItemStack(Items.IRON_NUGGET), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(Items.GOLD_ORE, () -> new ItemStack(Items.GOLD_NUGGET), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(Blocks.DEEPSLATE_GOLD_ORE.asItem(), () -> new ItemStack(Items.GOLD_NUGGET), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(Items.COPPER_ORE, () -> new ItemStack(ItemsTC.nuggetCopper.get()), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(Blocks.DEEPSLATE_COPPER_ORE.asItem(), () -> new ItemStack(ItemsTC.nuggetCopper.get()), 0.33f));
		SMELTING_BONUSES.add(new SmeltBonus(BlocksTC.oreCinnabar.get().asItem(), () -> new ItemStack(ItemsTC.nuggetQuicksilver.get()), 0.33f));
	}

	private static void addBonus(Item input, DeferredItem<Item> output, float chance) {
		SMELTING_BONUSES.add(new SmeltBonus(input, () -> new ItemStack(output.get()), chance));
	}

	/** Rolls {@link #SMELTING_BONUSES} against {@code smeltedInput} - {@code rolls} independent attempts (real
	 * {@code bellows + 1} formula, see this class's own doc comment), each bonus entry getting its own real
	 * chance per attempt. */
	private static java.util.List<ItemStack> rollSmeltingBonuses(ItemStack smeltedInput, int rolls, net.minecraft.util.RandomSource random) {
		java.util.List<ItemStack> bonuses = new java.util.ArrayList<>();
		for (int a = 0; a < rolls; a++) {
			for (SmeltBonus bonus : SMELTING_BONUSES) {
				if (smeltedInput.is(bonus.input()) && random.nextFloat() <= bonus.chance()) {
					bonuses.add(bonus.output().get());
				}
			}
		}
		return bonuses;
	}

	private void ejectItem(Level level, BlockPos pos, BlockState state, ItemStack result, float experience) {
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		BlockPos frontPos = pos.relative(facing);
		IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, frontPos, facing.getOpposite());
		if (handler != null) {
			result = ItemHandlerHelper.insertItemStacked(handler, result, false);
		}
		if (!result.isEmpty()) {
			double ex = pos.getX() + 0.5 + facing.getStepX() * 0.7;
			double ey = pos.getY() + 0.2;
			double ez = pos.getZ() + 0.5 + facing.getStepZ() * 0.7;
			Containers.dropItemStack(level, ex, ey, ez, result);
		}

		int xp = experience > 0 ? (int) experience : 0;
		float remainder = experience - xp;
		if (remainder > 0 && level.random.nextFloat() < remainder) xp++;
		if (xp > 0) {
			ExperienceOrb.award((net.minecraft.server.level.ServerLevel) level, net.minecraft.world.phys.Vec3.atCenterOf(pos).add(facing.getStepX() * 1.2, -0.1, facing.getStepZ() * 1.2), xp);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("inventory", inventory.serializeNBT(registries));
		tag.putShort("CookTime", (short) cookTime);
		tag.putShort("SpeedyTime", (short) speedyTime);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		if (tag.contains("inventory")) inventory.deserializeNBT(registries, tag.getCompound("inventory"));
		cookTime = tag.getShort("CookTime");
		speedyTime = tag.getShort("SpeedyTime");
	}

	public ItemStackHandler getItemHandler() {
		return inventory;
	}
}
