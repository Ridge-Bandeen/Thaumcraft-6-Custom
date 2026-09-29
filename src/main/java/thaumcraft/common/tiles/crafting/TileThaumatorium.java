package thaumcraft.common.tiles.crafting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.CrucibleRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileThaumatorium}: an auto-crafting altar built on the same
 * {@link CrucibleRecipe} type as {@link TileCrucible}. It stands on a crucible (a real crucible below, a thin
 * cosmetic "top" block above), which itself stands on a heat source - so the heat is checked TWO blocks down.
 *
 * <p>Rebuilt 2026-09-25 (the port had a first-match, GUI-less stand-in that ignored research, and looked for its
 * heat source one block down - where the crucible always is, so a legitimately built Thaumatorium never ran):
 * <ul>
 * <li>The player curates a list of recipes in the GUI ({@link thaumcraft.common.container.ThaumatoriumMenu}) - only
 * recipes whose research they know and whose catalyst matches the item in the slot are offered. The list holds
 * {@link #getMaxRecipes()} entries: 1, plus 2 for every Brain in a Jar mounted against the column.</li>
 * <li>Every 5 ticks, while heated (heat source two blocks down) and not powered by redstone, it takes the selected
 * recipe whose catalyst matches the slot, sucks essentia from connected jars/tubes toward that recipe's aspects
 * until they are met, then crafts, consuming one catalyst and clearing the stored essentia.</li>
 * </ul>
 * Not ported: crafting-event firing for the curating player, and ejecting into an adjacent inventory (the result
 * drops above the device).
 *
 * <p>{@link #setChanged()} is overridden below for the same real reason {@link TilePedestal#setChanged()} is - the
 * renderer's floating preview and the GUI both read this tile's state on the client.
 */
public class TileThaumatorium extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	private final ItemStackHandler catalyst = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return level != null && anyRecipeUses(level, stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	private AspectList aspects = new AspectList();
	private Aspect currentSuction;
	private int counter;
	private boolean heated;
	private int maxRecipes = 1;
	/** The recipes the player curated, by recipe id. */
	private final List<ResourceLocation> selected = new ArrayList<>();
	/** Who selected each recipe - the original's recipePlayer list: they count as having crafted what it makes. */
	private final java.util.Map<ResourceLocation, java.util.UUID> selectedBy = new java.util.HashMap<>();

	public TileThaumatorium(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.THAUMATORIUM.get(), pos, state);
	}

	public ItemStack getItem() {
		return catalyst.getStackInSlot(0);
	}

	public void setItem(ItemStack stack) {
		catalyst.setStackInSlot(0, stack);
	}

	public boolean accepts(ItemStack stack) {
		return catalyst.isItemValid(0, stack);
	}

	public IItemHandler getItemHandler() {
		return catalyst;
	}

	public Aspect getCurrentSuction() {
		return currentSuction;
	}

	public int getMaxRecipes() {
		return maxRecipes;
	}

	public List<ResourceLocation> getSelected() {
		return selected;
	}

	// ---- recipe curation ---------------------------------------------------------------------------------

	/** Every crucible recipe in a stable order (by id), so the GUI and the server agree on what a menu-button index
	 * means. */
	public static List<RecipeHolder<CrucibleRecipe>> allRecipes(Level level) {
		List<RecipeHolder<CrucibleRecipe>> all = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get()));
		all.sort(Comparator.comparing(h -> h.id().toString()));
		return all;
	}

	/** The recipes the GUI lists for {@code player}: those they know the research for whose catalyst matches the item
	 * in the slot, plus everything already selected - sorted by output name like the original. */
	public List<RecipeHolder<CrucibleRecipe>> listedRecipes(Level level, Player player) {
		List<RecipeHolder<CrucibleRecipe>> out = new ArrayList<>();
		ItemStack held = getItem();
		for (RecipeHolder<CrucibleRecipe> holder : allRecipes(level)) {
			boolean chosen = selected.contains(holder.id());
			boolean offered = !held.isEmpty() && knows(player, holder.value()) && holder.value().catalystMatches(held);
			if (chosen || offered) out.add(holder);
		}
		out.sort(Comparator.comparing(h -> h.value().getRecipeOutput().getHoverName().getString()));
		return out;
	}

	private static boolean knows(Player player, CrucibleRecipe recipe) {
		String research = recipe.getResearch();
		return research == null || research.isEmpty() || ThaumcraftCapabilities.knowsResearchStrict(player, research);
	}

	/** Server side of a click on a recipe icon: deselects it if selected, else selects it (room permitting, and only
	 * for a recipe the player may use with the current catalyst). */
	public void toggleSelection(Player player, int recipeIndex) {
		if (level == null) return;
		List<RecipeHolder<CrucibleRecipe>> all = allRecipes(level);
		if (recipeIndex < 0 || recipeIndex >= all.size()) return;
		RecipeHolder<CrucibleRecipe> holder = all.get(recipeIndex);
		if (selected.remove(holder.id())) {
			selectedBy.remove(holder.id());
			setChanged();
			return;
		}
		if (selected.size() >= maxRecipes) return;
		if (!knows(player, holder.value()) || !holder.value().catalystMatches(getItem())) return;
		selected.add(holder.id());
		selectedBy.put(holder.id(), player.getUUID());
		setChanged();
	}

	private Optional<RecipeHolder<CrucibleRecipe>> selectedRecipeFor(Level level, ItemStack held) {
		if (held.isEmpty()) return Optional.empty();
		for (ResourceLocation id : selected) {
			Optional<RecipeHolder<?>> holder = level.getRecipeManager().byKey(id);
			if (holder.isPresent() && holder.get().value() instanceof CrucibleRecipe recipe && recipe.catalystMatches(held)) {
				@SuppressWarnings("unchecked")
				RecipeHolder<CrucibleRecipe> typed = (RecipeHolder<CrucibleRecipe>) holder.get();
				return Optional.of(typed);
			}
		}
		return Optional.empty();
	}

	private static boolean anyRecipeUses(Level level, ItemStack stack) {
		if (stack.isEmpty()) return false;
		for (RecipeHolder<CrucibleRecipe> holder : allRecipes(level)) {
			if (holder.value().catalystMatches(stack)) return true;
		}
		return false;
	}

	/** What the floating preview above the device shows: the output of the selected recipe the current catalyst would
	 * craft (the original cycled through its curated list; this shows the one that will actually run). Safe
	 * client-side - recipes and this tile's selection are synced. */
	public ItemStack getPreviewResult() {
		if (level == null) return ItemStack.EMPTY;
		return selectedRecipeFor(level, getItem()).map(holder -> holder.value().getRecipeOutput()).orElse(ItemStack.EMPTY);
	}

	// ---- operation ---------------------------------------------------------------------------------------

	/** The heat source sits under the crucible under this block. Lava, fire, magma - and, as for the crucible,
	 * nitor. */
	private static boolean isHot(Level level, BlockPos pos) {
		BlockState heat = level.getBlockState(pos.below(2));
		return heat.is(Blocks.LAVA) || heat.is(Blocks.FIRE) || heat.is(Blocks.MAGMA_BLOCK) || heat.is(Blocks.SOUL_FIRE)
				|| heat.getBlock() instanceof thaumcraft.common.blocks.misc.BlockNitor;
	}

	private static boolean isPowered(Level level, BlockPos pos) {
		return level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.below()) || level.hasNeighborSignal(pos.above());
	}

	/** Brain in a Jar boxes mounted against the column add two recipe slots each (the device has one to start with). */
	private static int countUpgrades(Level level, BlockPos pos) {
		int slots = 1;
		for (int yy = 0; yy <= 1; yy++) {
			for (Direction dir : Direction.values()) {
				if (dir == Direction.DOWN) continue;
				BlockPos bp = pos.offset(dir.getStepX(), yy + dir.getStepY(), dir.getStepZ());
				BlockState state = level.getBlockState(bp);
				if (state.is(BlocksTC.brainBox.get())
						&& state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING) == dir.getOpposite()) {
					slots += 2;
				}
			}
		}
		return slots;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileThaumatorium tile) {
		if (level.isClientSide) return;
		if (tile.counter == 0 || tile.counter % 40 == 0) {
			tile.heated = isHot(level, pos);
			int slots = countUpgrades(level, pos);
			if (slots != tile.maxRecipes) {
				tile.maxRecipes = slots;
				while (tile.selected.size() > slots) tile.selected.remove(tile.selected.size() - 1);
				tile.setChanged();
			}
		}
		tile.counter++;
		if (tile.counter % 5 != 0) return;
		if (!tile.heated || isPowered(level, pos) || tile.selected.isEmpty()) return;

		ItemStack held = tile.catalyst.getStackInSlot(0);
		if (held.isEmpty()) {
			tile.currentSuction = null;
			return;
		}

		Optional<RecipeHolder<CrucibleRecipe>> match = tile.selectedRecipeFor(level, held);
		if (match.isEmpty()) {
			tile.currentSuction = null;
			return;
		}

		CrucibleRecipe recipe = match.get().value();
		AspectList target = recipe.getAspects();
		Aspect missing = null;
		for (Aspect tag : target.getAspectsSortedByName()) {
			if (tile.aspects.getAmount(tag) < target.getAmount(tag)) {
				missing = tag;
				break;
			}
		}

		if (missing == null) {
			CrucibleRecipeInput input = new CrucibleRecipeInput(held, tile.aspects);
			if (!recipe.matches(tile.aspects, held)) return;
			ItemStack out = recipe.assemble(input, level.registryAccess());
			tile.aspects = new AspectList(); // the original wiped the whole store, not just the recipe's share
			tile.catalyst.extractItem(0, 1, false);
			java.util.UUID by = tile.selectedBy.get(match.get().id());
			if (by != null && level.getPlayerByUUID(by) instanceof Player crafter) {
				net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(crafter, out.copy(), new net.minecraft.world.SimpleContainer(held.copyWithCount(1)));
			}
			tile.ejectItem(level, pos, out);
			tile.currentSuction = null;
			level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.25f, 2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f);
			tile.setChanged();
		} else {
			tile.currentSuction = missing;
			tile.fill(level, pos);
		}
	}

	/** Pulls one essentia from a connected jar or tube - scanning both this block's neighbours and the top block's
	 * (the device is two blocks tall), never straight down. */
	private void fill(Level level, BlockPos pos) {
		for (int y = 0; y <= 1; y++) {
			BlockPos base = pos.above(y);
			for (Direction dir : Direction.values()) {
				if (dir == Direction.DOWN || (y == 0 && dir == Direction.UP)) continue;
				if (!(level.getBlockEntity(base.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
				if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;
				if (neighbor.getEssentiaAmount(dir.getOpposite()) <= 0) continue;
				if (neighbor.getSuctionAmount(dir.getOpposite()) >= getSuctionAmount(dir)) continue;
				if (getSuctionAmount(dir) < neighbor.getMinimumSuction()) continue;
				int taken = neighbor.takeEssentia(currentSuction, 1, dir.getOpposite());
				if (taken > 0) {
					aspects.add(currentSuction, taken);
					setChanged();
					return;
				}
			}
		}
	}

	/** The original's {@code InventoryUtils#ejectStackAt}: the result goes out the FRONT (the side the device faces) - into an
	 * inventory there (a chest, hopper...) if it takes it, otherwise dropped in front of the device with a push outward
	 * (from behind the front block instead when that one is solid). It had been going into any side neighbour or straight up. */
	private void ejectItem(Level level, BlockPos pos, ItemStack stack) {
		if (stack.isEmpty()) return;
		Direction facing = getBlockState().hasProperty(thaumcraft.common.blocks.crafting.BlockThaumatorium.FACING)
				? getBlockState().getValue(thaumcraft.common.blocks.crafting.BlockThaumatorium.FACING) : Direction.NORTH;
		BlockPos front = pos.relative(facing);
		IItemHandler handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, front, facing.getOpposite());
		if (handler != null) {
			stack = net.neoforged.neoforge.items.ItemHandlerHelper.insertItem(handler, stack, false);
			if (stack.isEmpty()) return;
		}
		BlockPos base = level.getBlockState(front).isCollisionShapeFullBlock(level, front) ? pos.relative(facing.getOpposite()) : pos;
		ItemEntity out = new ItemEntity(level, base.getX() + 0.5 + facing.getStepX(), base.getY(), base.getZ() + 0.5 + facing.getStepZ(), stack);
		out.setDeltaMovement(0.3 * facing.getStepX(), 0.0, 0.3 * facing.getStepZ());
		level.addFreshEntity(out);
	}

	// ---- IAspectContainer / IEssentiaTransport -------------------------------------------------------------

	@Override
	public AspectList getAspects() {
		return aspects;
	}

	@Override
	public void setAspects(AspectList aspects) {
		this.aspects = aspects;
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return true;
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		aspects.add(tag, amount);
		setChanged();
		return 0;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		return aspects.reduce(tag, amount);
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		return aspects.getAmount(tag) >= amount;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return aspects.getAmount(tag);
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face != Direction.DOWN;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face != Direction.DOWN;
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return false;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
		currentSuction = aspect;
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return currentSuction;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return currentSuction != null ? 128 : 0;
	}

	@Override
	public Aspect getEssentiaType(Direction face) {
		return null;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return 0;
	}

	@Override
	public int takeEssentia(Aspect aspect, int amount, Direction face) {
		return 0;
	}

	@Override
	public int addEssentia(Aspect aspect, int amount, Direction face) {
		if (!canInputFrom(face)) return 0;
		aspects.add(aspect, amount);
		setChanged();
		return amount;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", catalyst.serializeNBT(registries));
		aspects.writeToNBT(tag);
		tag.putInt("MaxRecipes", maxRecipes);
		ListTag list = new ListTag();
		for (ResourceLocation id : selected) list.add(StringTag.valueOf(id.toString()));
		tag.put("Selected", list);
		CompoundTag by = new CompoundTag();
		selectedBy.forEach((id, uuid) -> by.putUUID(id.toString(), uuid));
		tag.put("SelectedBy", by);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		catalyst.deserializeNBT(registries, tag.getCompound("Items"));
		aspects = new AspectList();
		aspects.readFromNBT(tag);
		maxRecipes = tag.contains("MaxRecipes") ? tag.getInt("MaxRecipes") : 1;
		selected.clear();
		for (int i = 0; i < tag.getList("Selected", 8).size(); i++) {
			ResourceLocation id = ResourceLocation.tryParse(tag.getList("Selected", 8).getString(i));
			if (id != null) selected.add(id);
		}
		selectedBy.clear();
		CompoundTag by = tag.getCompound("SelectedBy");
		for (String key : by.getAllKeys()) {
			ResourceLocation id = ResourceLocation.tryParse(key);
			if (id != null && by.hasUUID(key)) selectedBy.put(id, by.getUUID(key));
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** See {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment - same real
	 * fix, same reason. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}
