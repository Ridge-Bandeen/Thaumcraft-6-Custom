package thaumcraft.common.tiles.crafting;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
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
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.CrucibleRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileThaumatorium}. The original was an auto-crafting altar built on the
 * same {@link CrucibleRecipe} type as {@link TileCrucible}: a 3-tall structure ({@code BlocksTC.crucible}
 * required below it, a thin cosmetic "top" block above), gated on the player curating up to N (Brain-in-a-Jar
 * upgradeable) recipes through a dedicated GUI, then sucking essentia from connected jars/tubes toward
 * whichever selected recipe's aspect requirement matched the currently inserted catalyst.
 *
 * Real simplification, following {@code BlockInfernalFurnace}'s own "drop the multi-block illusion, pick the
 * right vanilla-shaped block directly" precedent (see its doc comment): a single standalone block, no GUI
 * (matching {@link thaumcraft.common.tiles.devices.TilePotionSprayer}/{@link TileVoidSiphon}'s no-menu
 * convention - right-click inserts/removes the catalyst item directly), no Brain-in-a-Jar upgrade (nothing
 * left to upgrade - see below), no required-crucible-below structure, no separate "top" block.
 *
 * The automation this device actually adds over the plain {@link TileCrucible} - the entire point of it - is
 * preserved: instead of a player manually feeding essentia crystals in one at a time until a recipe's aspect
 * cost happens to be met, this tile looks at whatever catalyst item is inserted, finds the first registered
 * {@code CRUCIBLE_TYPE} recipe whose catalyst {@link net.minecraft.world.item.crafting.Ingredient} matches it
 * (deterministic registration order stands in for the original's player-curated selection - a real
 * simplification if two recipes ever share a catalyst but need different aspects, though no shipped recipe
 * collides today; see the port-tracking memory), and actively sucks essentia from every connected
 * {@link IEssentiaTransport} neighbor (any side but down, which is reserved for the heat source) toward
 * exactly that recipe's requirement - same suction-and-fill shape as
 * {@link thaumcraft.common.tiles.devices.TilePotionSprayer#fill} - until it's met, then crafts and ejects
 * the result, consuming one catalyst.
 */
public class TileThaumatorium extends BlockEntity implements IAspectContainer, IEssentiaTransport {

	private final ItemStackHandler catalyst = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return level != null && findMatchingRecipe(level, stack).isPresent();
		}
	};

	private AspectList aspects = new AspectList();
	private Aspect currentSuction;
	private int counter;

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

	private static boolean isHot(Level level, BlockPos pos) {
		BlockState below = level.getBlockState(pos.below());
		return below.is(Blocks.LAVA) || below.is(Blocks.FIRE) || below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.SOUL_FIRE);
	}

	private static Optional<RecipeHolder<CrucibleRecipe>> findMatchingRecipe(Level level, ItemStack held) {
		if (held.isEmpty()) return Optional.empty();
		for (RecipeHolder<CrucibleRecipe> holder : level.getRecipeManager().getAllRecipesFor(ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get())) {
			if (holder.value().catalystMatches(held)) return Optional.of(holder);
		}
		return Optional.empty();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileThaumatorium tile) {
		if (level.isClientSide) return;
		tile.counter++;
		if (tile.counter % 5 != 0) return;

		ItemStack held = tile.catalyst.getStackInSlot(0);
		if (held.isEmpty() || !isHot(level, pos)) {
			tile.currentSuction = null;
			return;
		}

		Optional<RecipeHolder<CrucibleRecipe>> match = findMatchingRecipe(level, held);
		if (match.isEmpty()) {
			tile.currentSuction = null;
			return;
		}

		CrucibleRecipe recipe = match.get().value();
		AspectList target = recipe.getAspects();
		Aspect missing = null;
		for (Aspect tag : target.getAspects()) {
			if (tile.aspects.getAmount(tag) < target.getAmount(tag)) {
				missing = tag;
				break;
			}
		}

		if (missing == null) {
			CrucibleRecipeInput input = new CrucibleRecipeInput(held, tile.aspects);
			ItemStack out = recipe.assemble(input, level.registryAccess());
			tile.aspects = recipe.removeMatching(tile.aspects);
			tile.catalyst.extractItem(0, 1, false);
			tile.ejectItem(level, pos, out);
			tile.currentSuction = null;
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.0f);
			tile.setChanged();
		} else {
			tile.currentSuction = missing;
			tile.fill(level, pos);
		}
	}

	private void fill(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (dir == Direction.DOWN) continue;
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
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

	private void ejectItem(Level level, BlockPos pos, ItemStack stack) {
		if (stack.isEmpty()) return;
		ItemEntity out = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, stack);
		out.setDeltaMovement(0, 0.075, 0);
		level.addFreshEntity(out);
	}

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
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		catalyst.deserializeNBT(registries, tag.getCompound("Items"));
		aspects = new AspectList();
		aspects.readFromNBT(tag);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
