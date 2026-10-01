package thaumcraft.common.tiles.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.GolemPlacerData;
import thaumcraft.common.container.GolemBuilderMenu;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.golems.ItemGolemPlacer;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileGolemBuilder} - assembles a chosen {@link IGolemProperties} into a
 * real {@link ItemGolemPlacer} stack over time, paid in {@code Aspect.MECHANISM} essentia drawn from an
 * adjacent essentia network (same real direct-neighbor-scan {@link IEssentiaTransport} pattern
 * {@code TileTube} already established, not the original's still-1.12.2 {@code ThaumcraftApiHelper.getConnectableTile}).
 *
 * Purpose-built {@link BlockEntity} + real {@link ItemStackHandler} (single output slot), same "no shared
 * inventory-tile base" pattern every device this session uses.
 *
 * Components come from the inventories beside the press (not above) or else the crafting player, as the original's
 * {@code InventoryUtils.consumeItemsFromAdjacentInventoryOrPlayer} ({@link #checkCraft}, {@link #startCraft}) - with its
 * quirk kept: a component partly found beside the press is taken from there AND in full from the player.
 *
 * Real behavioural improvement over the original, documented rather than silent: the original's completion
 * check ({@code cost <= 0} -> try to place the finished placer in the output slot) only ever ran inside the
 * same {@code cost > 0} guard as the essentia-draw step, so a completed golem whose output slot was full
 * when {@code cost} first hit zero would never retry and stayed stuck forever. {@link #tick} checks
 * completion as its own unconditional step every 5 ticks instead, so a golem completes as soon as the
 * output slot frees up.
 *
 * <p>Real fixed-point found and fixed 2026-09-16, alongside the real client-only press-ram animation and
 * model renderer restoration ({@link thaumcraft.client.render.TileGolemBuilderRenderer}): this tile had NO
 * real {@code saveAdditional}/{@code loadAdditional} overrides at all before - a real, previously-unnoticed
 * bug, not a cosmetic gap - meaning an in-progress craft ({@link #golem}/{@link #cost}/{@link #maxCost}) and
 * even the real finished {@link ItemGolemPlacer} sitting in the output slot would silently vanish on a chunk
 * save/reload. Fixed alongside the real block-entity sync wiring the renderer also needs ({@code
 * getUpdateTag}/{@code getUpdatePacket}), matching the same "new sync infra before new renderer" shape
 * several other tile-renderer restorations this session already needed.
 */
public class TileGolemBuilder extends BlockEntity implements MenuProvider, IEssentiaTransport {

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return stack.getItem() instanceof ItemGolemPlacer;
		}
	};

	/** The properties currently being assembled, packed the same way {@link GolemProperties} always does;
	 * {@code -1} means no build in progress. */
	public long golem = -1L;
	public int cost;
	public int maxCost;
	private boolean bufferedEssentia;
	private int ticks;

	/** Real client-only "press ram" animation angle (degrees, 0-90), matching the true original's own real
	 * field exactly - deliberately plain (not synced): the true original's own version was purely
	 * client-computed too, off the real already-synced {@link #cost}/{@link #golem} state alone. */
	public int press;

	public TileGolemBuilder(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.GOLEM_BUILDER.get(), pos, state);
	}

	public IItemHandler getItemHandler() {
		return items;
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.thaumcraft.golem_builder");
	}

	@Override
	public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
		return new GolemBuilderMenu(containerId, inventory, this);
	}

	/** The original's checkCraft: which of the golem's components the inventories beside the press can supply (the
	 * screen shows the rest as missing unless the player carries them). */
	public boolean[] checkCraft(long id) {
		ItemStack[] components = GolemProperties.fromLong(id).generateComponents();
		boolean[] ret = new boolean[components.length];
		for (int a = 0; a < components.length; a++) {
			ret[a] = level != null && countAdjacent(level, worldPosition, components[a]) >= components[a].getCount();
		}
		return ret;
	}

	/** The original's startCraft: refuses (and clears) if the output slot holds something it can't stack onto, takes the
	 * components, and sets the Machina cost - 2 per trait plus 1 per component item. */
	public boolean startCraft(long id, Player player) {
		ItemStack placer = placerFor(id);
		ItemStack current = items.getStackInSlot(0);
		if (!current.isEmpty() && (current.getCount() >= current.getMaxStackSize() || !ItemStack.isSameItemSameComponents(current, placer))) {
			cost = 0;
			golem = -1L;
			return false;
		}
		IGolemProperties props = GolemProperties.fromLong(id);
		ItemStack[] components = props.generateComponents();
		for (ItemStack stack : components) {
			if (countAdjacent(level, worldPosition, stack) < stack.getCount() && countCarried(player, stack) < stack.getCount()) {
				cost = 0;
				golem = -1L;
				return false;
			}
		}
		golem = id;
		int c = props.getTraits().size() * 2;
		for (ItemStack stack : components) {
			c += stack.getCount();
		}
		for (ItemStack stack : components) {
			if (!consumeAdjacent(level, worldPosition, stack)) {
				consumeCarried(player, stack);
			}
		}
		cost = c;
		maxCost = c;
		setChanged();
		level.playSound(null, worldPosition, thaumcraft.common.registry.ThaumcraftSounds.WAND.get(), SoundSource.BLOCKS, 0.25f, 1.0f);
		return true;
	}

	private static ItemStack placerFor(long id) {
		ItemStack placer = new ItemStack(ItemsTC.golemPlacer.get());
		placer.set(ThaumcraftDataComponents.GOLEM_PLACER_DATA.get(), new GolemPlacerData(id, 0));
		return placer;
	}

	private static boolean matches(ItemStack wanted, ItemStack candidate) {
		return !candidate.isEmpty() && ItemStack.isSameItem(wanted, candidate);
	}

	/** Every side but the top, as the original. */
	private static IItemHandler handlerAt(Level level, BlockPos pos, Direction face) {
		return level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos.relative(face), face.getOpposite());
	}

	private static int countAdjacent(Level level, BlockPos pos, ItemStack wanted) {
		int count = 0;
		for (Direction face : Direction.values()) {
			if (face == Direction.UP) continue;
			IItemHandler handler = handlerAt(level, pos, face);
			if (handler == null) continue;
			for (int slot = 0; slot < handler.getSlots(); slot++) {
				ItemStack s = handler.getStackInSlot(slot);
				if (matches(wanted, s)) count += s.getCount();
			}
			if (count >= wanted.getCount()) break;
		}
		return count;
	}

	/** The original's consumeFromAdjacentChests: takes what it can, true only if all of it came from beside the press. */
	private static boolean consumeAdjacent(Level level, BlockPos pos, ItemStack wanted) {
		int left = wanted.getCount();
		for (Direction face : Direction.values()) {
			if (face == Direction.UP || left <= 0) continue;
			IItemHandler handler = handlerAt(level, pos, face);
			if (handler == null) continue;
			for (int slot = 0; slot < handler.getSlots() && left > 0; slot++) {
				if (!matches(wanted, handler.getStackInSlot(slot))) continue;
				left -= handler.extractItem(slot, left, false).getCount();
			}
		}
		return left <= 0;
	}

	private static int countCarried(Player player, ItemStack wanted) {
		int count = 0;
		for (ItemStack s : player.getInventory().items) {
			if (matches(wanted, s)) count += s.getCount();
		}
		return count;
	}

	private static void consumeCarried(Player player, ItemStack wanted) {
		int left = wanted.getCount();
		for (ItemStack s : player.getInventory().items) {
			if (left <= 0) break;
			if (!matches(wanted, s)) continue;
			int take = Math.min(left, s.getCount());
			s.shrink(take);
			left -= take;
		}
		player.getInventory().setChanged();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileGolemBuilder tile) {
		if (level.isClientSide) {
			tile.tickPress(level, pos);
			return;
		}
		tile.ticks++;
		if (tile.ticks % 5 != 0) {
			return;
		}
		if (tile.cost > 0 && tile.golem >= 0L) {
			if (tile.bufferedEssentia || tile.drawEssentia(level, pos)) {
				tile.bufferedEssentia = false;
				tile.cost--;
				tile.setChanged();
			}
		}
		if (tile.cost <= 0 && tile.golem >= 0L) {
			tile.completeCraft(level, pos);
		}
	}

	private void completeCraft(Level level, BlockPos pos) {
		ItemStack placer = placerFor(golem);
		ItemStack current = items.getStackInSlot(0);
		if (current.isEmpty()) {
			items.setStackInSlot(0, placer);
		} else if (current.getCount() < current.getMaxStackSize() && ItemStack.isSameItemSameComponents(current, placer)) {
			current.grow(1);
		} else {
			return; // output slot full/mismatched - stay pending and retry next tick, see this class's own doc comment.
		}
		golem = -1L;
		cost = 0;
		maxCost = 0;
		setChanged();
		level.playSound(null, pos, thaumcraft.common.registry.ThaumcraftSounds.WAND.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
	}

	/** Real vent-steam colour, matching the true original's own real hardcoded {@code 11184810} (0xAAAAAA,
	 * a real light-grey steam tint) at every one of its own real call sites. */
	private static final int VENT_COLOR = 0xAAAAAA;

	/** Real modern equivalent of the true original's own real client-side {@code update()} branch - see
	 * {@link #press}'s own doc comment for the real formula, and {@link
	 * thaumcraft.client.fx.ClientFxHandlers#spawnVentBurst}'s own doc comment for why the real particle spawn
	 * is routed through that class rather than referencing {@link thaumcraft.client.fx.particles.FXVent}
	 * directly from this common-side method. */
	private void tickPress(Level level, BlockPos pos) {
		if (press < 90 && cost > 0 && golem > 0L) {
			press += 6;
			if (press >= 60) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
						SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.66f, 1.0f + level.getRandom().nextFloat() * 0.1f, false);
				thaumcraft.client.fx.ClientFxHandlers.spawnVentBurst(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 16, VENT_COLOR);
			}
		}
		if (press >= 90 && level.getRandom().nextInt(8) == 0) {
			thaumcraft.client.fx.ClientFxHandlers.spawnVentBurst(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 1, VENT_COLOR);
			level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
					SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.1f, 1.0f + level.getRandom().nextFloat() * 0.1f, false);
		}
		if (press > 0 && (cost <= 0 || golem == -1L)) {
			if (press >= 90) {
				thaumcraft.client.fx.ClientFxHandlers.spawnVentBurst(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 10, VENT_COLOR);
			}
			press -= 3;
		}
	}

	private boolean drawEssentia(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite())) continue;
			if (!neighbor.canOutputTo(dir.getOpposite())) return false; // as the original
			if (neighbor.getSuctionAmount(dir.getOpposite()) < getSuctionAmount(dir)
					&& neighbor.takeEssentia(Aspect.MECHANISM, 1, dir.getOpposite()) == 1) {
				return true;
			}
		}
		return false;
	}

	// ---- IEssentiaTransport ----

	@Override
	public boolean isConnectable(Direction face) {
		return face.getAxis().isHorizontal() || face == Direction.DOWN;
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return isConnectable(face);
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return false;
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return Aspect.MECHANISM;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return (cost > 0 && golem >= 0L) ? 128 : 0;
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
		if (!bufferedEssentia && cost > 0 && golem >= 0L && aspect == Aspect.MECHANISM) {
			bufferedEssentia = true;
			return 1;
		}
		return 0;
	}

	/** Real chunk-save persistence, added 2026-09-16 - this tile had none before, a real, previously-unnoticed
	 * bug (not just the client-sync gap) - see the class doc comment. */
	@Override
	protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putLong("Golem", golem);
		tag.putInt("Cost", cost);
		tag.putInt("MaxCost", maxCost);
		tag.put("Items", items.serializeNBT(registries));
	}

	@Override
	protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		golem = tag.contains("Golem") ? tag.getLong("Golem") : -1L;
		cost = tag.getInt("Cost");
		maxCost = tag.getInt("MaxCost");
		if (tag.contains("Items")) {
			items.deserializeNBT(registries, tag.getCompound("Items"));
		}
	}

	/** Real block-entity sync wiring, added alongside the real press-ram animation restoration - {@link
	 * #cost}/{@link #golem} (what the real animation is gated on) were never actually visible to the client
	 * before this. Same real pattern {@code TileCrucible}/{@code TileCentrifuge}/{@code TilePatternCrafter}
	 * already established. */
	@Override
	public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}
