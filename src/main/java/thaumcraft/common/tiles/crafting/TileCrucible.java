package thaumcraft.common.tiles.crafting;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
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

	public int heat;
	public boolean hasWater;
	public AspectList aspects = new AspectList();
	private long idleTicks;

	public TileCrucible(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.CRUCIBLE.get(), pos, state);
	}

	/** The surface height the original drew the water at: 0.8 for a full cauldron (0.3 + 0.5 * fill), rising toward
	 * the rim as vis accumulates. Was a fixed 0.75 that ignored vis, so the fill looked low. */
	public float getFluidHeight() {
		float base = hasWater ? 0.8f : 0.3f;
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
		if (r.nextInt(6) == 0 && aspects.size() > 0) {
			int color = aspects.getAspects()[r.nextInt(aspects.size())].getColor();
			thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level,
					pos.getX() + (5 + r.nextInt(22)) / 32.0f + 0.015625f, pos.getY() + 0.05f + getFluidHeight(), pos.getZ() + (5 + r.nextInt(22)) / 32.0f + 0.015625f,
					((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
		}
	}

	public boolean isActive() {
		return hasWater && heat > ACTIVE_HEAT;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileCrucible tile) {
		tile.idleTicks++;
		if (level.isClientSide) {
			if (tile.hasWater) tile.drawEffects(level, pos);
			return;
		}

		if (tile.hasWater) {
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

		if (tile.aspects.visSize() > MAX_ASPECTS || tile.idleTicks >= 100) {
			tile.spillRandom(level);
			tile.idleTicks = 0;
		}
	}

	/**
	 * Feeds one item into the crucible - dissolves it into aspects, or if it completes a recipe, crafts
	 * the result. Returns the (possibly shrunk) input stack.
	 */
	public ItemStack attemptSmelt(ItemStack item, Level level) {
		if (!isActive() || item.isEmpty()) return item;

		CrucibleRecipeInput input = new CrucibleRecipeInput(item, aspects);
		Optional<RecipeHolder<CrucibleRecipe>> match = level.getRecipeManager().getRecipeFor(ThaumcraftRecipeTypes.CRUCIBLE_TYPE.get(), input, level);
		if (match.isPresent()) {
			CrucibleRecipe recipe = match.get().value();
			ItemStack out = recipe.assemble(input, level.registryAccess());
			aspects = recipe.removeMatching(aspects);
			ejectItem(level, out);
			item.shrink(1);
			idleTicks = 0;
			setChanged();
			if (level instanceof ServerLevel serverLevel) {
				serverLevel.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6f, 1.2f);
			}
			return item;
		}

		if (item.getItem() instanceof IEssentiaContainerItem container) {
			AspectList itemAspects = container.getAspects(item);
			if (itemAspects != null && itemAspects.size() > 0) {
				aspects.add(itemAspects);
				item.shrink(1);
				idleTicks = 0;
				setChanged();
				if (level instanceof ServerLevel serverLevel) {
					serverLevel.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.2f, 1.6f);
				}
			}
		}
		return item;
	}

	public void tryConsume(ItemEntity itemEntity) {
		ItemStack remaining = attemptSmelt(itemEntity.getItem(), itemEntity.level());
		if (remaining.isEmpty()) {
			itemEntity.discard();
		} else {
			itemEntity.setItem(remaining);
		}
	}

	private void ejectItem(Level level, ItemStack stack) {
		if (stack.isEmpty()) return;
		ItemEntity out = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.71, worldPosition.getZ() + 0.5, stack);
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
		if (hasWater || vis > 0) {
			hasWater = false;
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
		hasWater = tag.getBoolean("hasWater");
		aspects.readFromNBT(tag);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("heat", heat);
		tag.putBoolean("hasWater", hasWater);
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
