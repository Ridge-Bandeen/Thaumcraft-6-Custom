package thaumcraft.common.tiles.crafting;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.CrucibleRecipeInput;
import thaumcraft.api.crafting.ThaumcraftRecipeTypes;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.registry.ThaumcraftSounds;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileCrucible}. Same core loop: heat up (lava/fire/magma below) with
 * water in it, throw items in (by hand or by them colliding as dropped item entities), and either they
 * dissolve into the crucible's aspect pool or - once the pool + catalyst match a recipe - get consumed to
 * produce the result.
 *
 * Simplified for this pass (all real features, just not built yet):
 * <ul>
 * <li>No {@code IFluidHandler} capability - "has water" is a plain boolean set by right-clicking with a
 * water bucket, not a real fluid tank other mods/pipes could interact with.</li>
 * <li>Only items implementing {@link IEssentiaContainerItem} (crystal essence, etc.) dissolve into
 * aspects. The original looked up aspects for *any* item via a global object-tag database
 * ({@code ThaumcraftCraftingManager.getObjectTags}, backed by the still-deferred
 * {@code AspectHelper}/{@code ThaumcraftApi.internalMethods} - see the port-tracking memory notes).</li>
 * <li>Boil froth and aspect bubbles are restored (client tick, see {@link #drawEffects}); the over-full froth
 * down the sides and the crucible sounds are not.</li>
 * <li>Collision shape is a plain approximation of the original's legs+walls, not an exact match.</li>
 * </ul>
 *
 * {@link #setChanged()} is overridden below - see
 * {@link thaumcraft.common.tiles.crafting.TilePedestal#setChanged()}'s own doc comment for why plain
 * {@code setChanged()} doesn't push live client updates on its own; needed here for
 * {@link thaumcraft.client.render.TileCrucibleRenderer}.
 */
public class TileCrucible extends BlockEntity implements IAspectContainer {

	public static final int MAX_ASPECTS = 500;
	private static final int ACTIVE_HEAT = 150;
	private static final int MAX_HEAT = 200;

	/** The original's tank capacity: one bucket is a full cauldron, each successful craft drains 50 of it. */
	public static final int WATER_CAPACITY = 1000;
	public static final int CRAFT_WATER_COST = 50;
	/** Persistent-data flag on items this crucible ejected - stands in for the original's {@code EntitySpecialItem}. */
	public static final String EJECTED_TAG = "thaumcraft_crucible_ejected";

	public int heat;
	/** Water in millibuckets, 0 to {@link #WATER_CAPACITY} (was a plain boolean - crafting could never use it up). */
	public int waterAmount;
	public AspectList aspects = new AspectList();
	/** The original's own {@code counter}: ticks up each tick, spills one aspect at 100 and resets. Dissolving an
	 * item sets it to -150 and crafting to -250, so freshly-added aspects sit for a while before the trickle starts
	 * (the port reset it to 0 instead, spilling them 2.5x too fast). */
	private long counter;

	public boolean hasWater() {
		return waterAmount > 0;
	}

	public TileCrucible(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.CRUCIBLE.get(), pos, state);
	}

	/** The surface height the original drew the water at: 0.8 for a full cauldron (0.3 + 0.5 * fill), rising toward
	 * the rim as vis accumulates. Was a fixed 0.75 that ignored vis, so the fill looked low. */
	public float getFluidHeight() {
		float base = 0.3f + 0.5f * (waterAmount / (float) WATER_CAPACITY);
		float out = base + aspects.visSize() / (float) MAX_ASPECTS * (1.0f - base);
		if (out > 1.0f) out = 1.001f;
		if (out == 1.0f) out = 0.9999f;
		return out;
	}

	/** Client-only boil effects, the original's {@code TileCrucible#drawEffects}: froth on the surface once hot
	 * (heat above 150), overflow froth down the sides when over-full, and a coloured bubble now and then for each
	 * dissolved aspect. */
	private void drawEffects(Level level, BlockPos pos) {
		var r = level.random;
		if (heat > ACTIVE_HEAT) {
			thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleFroth(level, pos.getX() + 0.2f + r.nextFloat() * 0.6f, pos.getY() + getFluidHeight(), pos.getZ() + 0.2f + r.nextFloat() * 0.6f);
		}
		if (heat > ACTIVE_HEAT && aspects.visSize() > MAX_ASPECTS) {
			// over-full: froth spills down all four sides
			for (int a = 0; a < 2; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleFrothDown(level, pos.getX(), pos.getY() + 1, pos.getZ() + r.nextFloat());
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleFrothDown(level, pos.getX() + 1, pos.getY() + 1, pos.getZ() + r.nextFloat());
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleFrothDown(level, pos.getX() + r.nextFloat(), pos.getY() + 1, pos.getZ());
				thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleFrothDown(level, pos.getX() + r.nextFloat(), pos.getY() + 1, pos.getZ() + 1);
			}
		}
		if (r.nextInt(6) == 0 && aspects.size() > 0) {
			int color = aspects.getAspects()[r.nextInt(aspects.size())].getColor();
			thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level,
					pos.getX() + (5 + r.nextInt(22)) / 32.0f + 0.015625f, pos.getY() + 0.05f + getFluidHeight(), pos.getZ() + (5 + r.nextInt(22)) / 32.0f + 0.015625f,
					((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
		}
	}

	public boolean isActive() {
		return hasWater() && heat > ACTIVE_HEAT;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileCrucible tile) {
		tile.counter++;
		if (level.isClientSide) {
			if (tile.hasWater()) tile.drawEffects(level, pos);
			return;
		}

		if (tile.hasWater()) {
			BlockState below = level.getBlockState(pos.below());
			boolean hot = below.is(net.minecraft.world.level.block.Blocks.LAVA) || below.is(net.minecraft.world.level.block.Blocks.FIRE)
					|| below.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || below.is(net.minecraft.world.level.block.Blocks.SOUL_FIRE)
					// The original also counted any Nitor below as a heat source (found in the first devices playtest,
					// 2026-09-25: a nitor under a crucible did nothing).
					|| below.getBlock() instanceof thaumcraft.common.blocks.misc.BlockNitor;
			if (hot && tile.heat < MAX_HEAT) {
				tile.heat++;
				tile.setChanged();
			} else if (!hot && tile.heat > 0) {
				tile.heat--;
				tile.setChanged();
			}
		} else if (tile.heat > 0) {
			tile.heat--;
		}

		if (tile.aspects.visSize() > MAX_ASPECTS || tile.counter >= 100) {
			tile.spillRandom(level);
			tile.counter = 0;
		}
	}

	/**
	 * The original's {@code attemptSmelt}: feeds a whole stack in, one item at a time. Each item first tries to
	 * complete a crucible recipe (the best-costing one the {@code player} has the research for, and only while the
	 * cauldron holds water - a craft costs {@link #CRAFT_WATER_COST} of it); otherwise it dissolves into whatever
	 * aspects the object-tag database lists for it (any item, not just crystal essences - the port had only handled
	 * {@link IEssentiaContainerItem}s). Returns what is left of the stack.
	 */
	public ItemStack attemptSmelt(ItemStack item, @Nullable Player player, Level level) {
		if (!isActive() || item.isEmpty()) return item;

		boolean bubble = false;
		boolean craftDone = false;
		int stackSize = item.getCount();
		for (int a = 0; a < item.getCount(); a++) {
			ItemStack single = item.copyWithCount(1);
			CrucibleRecipe recipe = hasWater() ? findRecipe(level, player, single) : null;
			if (recipe != null) {
				ItemStack out = recipe.assemble(new CrucibleRecipeInput(single, aspects), level.registryAccess());
				// as the original: the thrower crafted it (research asking for an alchemy product, crafting warp)
				if (player != null) net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(player, out.copy(), new net.minecraft.world.SimpleContainer(single));
				aspects = recipe.removeMatching(aspects);
				waterAmount = Math.max(0, waterAmount - CRAFT_WATER_COST);
				ejectItem(level, out);
				craftDone = true;
				stackSize--;
				counter = -250L;
			} else {
				AspectList dissolved = objectAspects(single);
				if (dissolved != null && dissolved.size() != 0) {
					for (Aspect tag : dissolved.getAspects()) {
						aspects.add(tag, dissolved.getAmount(tag));
					}
					bubble = true;
					stackSize--;
					counter = -150L;
				} else {
					break; // nothing more of this stack can do anything here
				}
			}
			if (!hasWater()) break;
		}

		if (level instanceof ServerLevel serverLevel) {
			if (bubble) {
				serverLevel.playSound(null, worldPosition, ThaumcraftSounds.BUBBLE.get(), SoundSource.BLOCKS, 0.2f, 1.0f + level.random.nextFloat() * 0.4f);
				sendBamf(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 1.25, worldPosition.getZ() + 0.5, false);
			}
			if (craftDone) {
				serverLevel.playSound(null, worldPosition, ThaumcraftSounds.SPILL.get(), SoundSource.BLOCKS, 0.2f, 1.0f);
				sendBamf(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 1.25, worldPosition.getZ() + 0.5, true);
			}
		}
		if (bubble || craftDone) setChanged();
		if (stackSize == item.getCount()) return item;
		ItemStack rest = item.copy();
		rest.setCount(Math.max(0, stackSize));
		return rest;
	}

	/** The item's own held aspects if it is an essentia container (crystal essence, phial...), else the global
	 * object-tag database - matching the original's {@code getObjectTags}, which did both. */
	@Nullable
	private static AspectList objectAspects(ItemStack stack) {
		if (stack.getItem() instanceof IEssentiaContainerItem container) {
			AspectList held = container.getAspects(stack);
			if (held != null && held.size() > 0) return held;
		}
		return thaumcraft.api.aspects.AspectHelper.getObjectAspects(stack);
	}

	/** The original's {@code findMatchingCrucibleRecipe}: among recipes the player has the research for and whose
	 * aspects and catalyst match, the one with the highest total aspect cost. No player (nobody threw it, a hopper)
	 * means no recipe, as before. */
	@Nullable
	private CrucibleRecipe findRecipe(Level level, @Nullable Player player, ItemStack catalyst) {
		if (player == null) return null;
		CrucibleRecipe best = null;
		int highest = 0;
		for (RecipeHolder<CrucibleRecipe> holder : level.getRecipeManager().getAllRecipesFor(ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get())) {
			CrucibleRecipe recipe = holder.value();
			String research = recipe.getResearch();
			if (research != null && !research.isEmpty()
					&& !thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearchStrict(player, research)) continue;
			if (!recipe.matches(aspects, catalyst)) continue;
			int cost = recipe.getAspects().visSize();
			if (cost <= highest) continue;
			highest = cost;
			best = recipe;
		}
		return best;
	}

	private static void sendBamf(ServerLevel level, double x, double y, double z, boolean sound) {
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, x, y, z, 32.0,
					new thaumcraft.common.lib.network.fx.PacketFXBlockBamf(x, y, z, 0, sound, true));
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Crucible bamf FX packet failed to send (no real client connection?)", e);
		}
	}

	public void tryConsume(ItemEntity itemEntity) {
		Player thrower = itemEntity.getOwner() instanceof Player p ? p : null;
		ItemStack remaining = attemptSmelt(itemEntity.getItem(), thrower, itemEntity.level());
		if (remaining.isEmpty()) {
			itemEntity.discard();
		} else {
			itemEntity.setItem(remaining);
		}
	}

	private void ejectItem(Level level, ItemStack stack) {
		if (stack.isEmpty()) return;
		ItemEntity out = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.71, worldPosition.getZ() + 0.5, stack);
		out.getPersistentData().putBoolean(EJECTED_TAG, true);
		out.setDeltaMovement(0, 0.075, 0);
		level.addFreshEntity(out);
	}

	public void spillRandom(Level level) {
		if (aspects.size() > 0) {
			Aspect[] all = aspects.getAspects();
			Aspect tag = all[level.getRandom().nextInt(all.length)];
			aspects.remove(tag, 1);
			AuraHandler.addFlux(level, worldPosition, tag == Aspect.FLUX ? 1.0f : 0.25f);
			setChanged();
		}
	}

	public void spillRemnants(Level level) {
		int vis = aspects.visSize();
		if (hasWater() || vis > 0) {
			waterAmount = 0;
			heat = 0;
			if (vis > 0) {
				AuraHandler.addFlux(level, worldPosition, vis * 0.25f);
				int flux = aspects.getAmount(Aspect.FLUX);
				if (flux > 0) AuraHandler.addFlux(level, worldPosition, flux * 0.75f);
			}
			aspects = new AspectList();
			level.playSound(null, worldPosition, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.5f, 1.0f);
			setChanged();
		}
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

	public int getComparatorSignal() {
		float ratio = aspects.visSize() / (float) MAX_ASPECTS;
		return net.minecraft.util.Mth.floor(ratio * 14.0f) + (aspects.visSize() > 0 ? 1 : 0);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		heat = tag.getInt("heat");
		// "hasWater" (boolean) is the pre-2026-09-25 save format: a full cauldron.
		waterAmount = tag.contains("water") ? tag.getInt("water") : (tag.getBoolean("hasWater") ? WATER_CAPACITY : 0);
		aspects.readFromNBT(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("heat", heat);
		tag.putInt("water", waterAmount);
		aspects.writeToNBT(tag);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
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
