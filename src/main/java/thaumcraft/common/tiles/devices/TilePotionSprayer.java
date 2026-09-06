package thaumcraft.common.tiles.devices;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TilePotionSprayer}. Holds one potion item; every 5 ticks it draws
 * essentia from an {@link IEssentiaTransport} neighbor (same suction-and-fill shape as
 * {@link TileCondenser#fill}, reused here for a second consumer) into a small internal recipe cost until
 * it's paid off, banking one "charge" (max {@link #MAX_CHARGES}). On a redstone pulse (edge-triggered, one
 * spray per press - {@link BlockStateProperties#ENABLED} is the same redstone-inverted convention every
 * other toggled device this session uses) it spends one charge to apply the held potion's effects to every
 * living entity in a small volume 2 blocks out along the block's facing, without consuming the potion
 * itself.
 *
 * Real, documented simplification: the original derived a real per-potion aspect cost by tracing the
 * potion's brewing reagents back through the whole item-aspect ("object tag") database
 * ({@code ConfigAspects#getPotionAspects} -> {@code ThaumcraftCraftingManager#getPotionReagentsRecursive}/
 * {@code #getObjectTags}) - thousands of {@code registerObjectTag} calls across {@code ConfigAspects} and
 * friends, none of which are ported yet (see the port-tracking memory's "config system" gap; this is a
 * separately-scoped future subsystem, not something to improvise piecemeal here). Standing in for it: a
 * flat alchemical cost, the same regardless of which potion is loaded - forced by the missing dependency,
 * not a deviation from the original's actual intent (charge the sprayer up with essentia before each
 * burst).
 *
 * Also simplified: no GUI - right-click inserts/removes the potion directly, the same no-menu convention
 * {@link thaumcraft.common.blocks.devices.BlockRechargePedestal} already established (no ported single-slot
 * container/menu base to reuse yet, so building one bespoke for a single device isn't worth it). No client
 * particle/sound venting FX ({@code FXDispatcher}) - visual-only, dropped like every other device this
 * session.
 */
public class TilePotionSprayer extends BlockEntity implements IEssentiaTransport {

	private static final int MAX_CHARGES = 8;

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return isValidPotion(stack);
		}

		@Override
		protected void onContentsChanged(int slot) {
			recalcRecipe();
		}
	};

	private AspectList recipe = new AspectList();
	private AspectList recipeProgress = new AspectList();
	private int charges;
	private int counter;
	private boolean activated;
	private Aspect currentSuction;

	public TilePotionSprayer(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.POTION_SPRAYER.get(), pos, state);
	}

	public static boolean isValidPotion(ItemStack stack) {
		if (!stack.is(Items.POTION) && !stack.is(Items.SPLASH_POTION) && !stack.is(Items.LINGERING_POTION)) return false;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		return contents != null && contents.hasEffects();
	}

	public ItemStack getItem() {
		return items.getStackInSlot(0);
	}

	public void setItem(ItemStack stack) {
		items.setStackInSlot(0, stack);
	}

	public boolean accepts(ItemStack stack) {
		return items.isItemValid(0, stack);
	}

	public IItemHandler getItemHandler() {
		return items;
	}

	public int getCharges() {
		return charges;
	}

	private void recalcRecipe() {
		ItemStack stack = items.getStackInSlot(0);
		recipe = stack.isEmpty() ? new AspectList() : new AspectList().add(Aspect.ALCHEMY, 10);
		recipeProgress = new AspectList();
		charges = 0;
		currentSuction = null;
		setChanged();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TilePotionSprayer tile) {
		if (level.isClientSide) return;
		tile.counter++;
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);

		if (tile.counter % 5 == 0 && !tile.items.getStackInSlot(0).isEmpty() && tile.charges < MAX_CHARGES) {
			tile.currentSuction = null;
			boolean done = true;
			for (Aspect aspect : tile.recipe.getAspectsSortedByName()) {
				if (tile.recipeProgress.getAmount(aspect) < tile.recipe.getAmount(aspect)) {
					tile.currentSuction = aspect;
					done = false;
					break;
				}
			}
			if (done) {
				tile.recipeProgress = new AspectList();
				tile.charges++;
				tile.setChanged();
			} else if (tile.currentSuction != null) {
				tile.fill(level, pos, facing);
			}
		}

		boolean enabled = state.getValue(BlockStateProperties.ENABLED);
		if (!enabled) {
			if (!tile.activated && tile.charges > 0) {
				tile.charges--;
				tile.spray(level, pos, facing);
				tile.setChanged();
			}
			tile.activated = true;
		} else {
			tile.activated = false;
		}
	}

	private void spray(Level level, BlockPos pos, Direction facing) {
		ItemStack stack = items.getStackInSlot(0);
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents != null) {
			BlockPos center = pos.relative(facing, 2);
			AABB area = new AABB(center.getX() - 1, center.getY() - 1, center.getZ() - 1,
					center.getX() + 2, center.getY() + 2, center.getZ() + 2);
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive);
			for (LivingEntity target : targets) {
				for (MobEffectInstance effect : contents.getAllEffects()) {
					if (effect.getEffect().value().isInstantenous()) {
						effect.getEffect().value().applyInstantenousEffect(null, null, target, effect.getAmplifier(), 1.0);
					} else {
						target.addEffect(new MobEffectInstance(effect));
					}
				}
			}
		}
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25f,
				2.6f + (level.random.nextFloat() - level.random.nextFloat()) * 0.8f);
	}

	private void fill(Level level, BlockPos pos, Direction facing) {
		for (int y = 0; y <= 1; y++) {
			for (Direction dir : Direction.values()) {
				if (dir == facing) continue;
				if (!(level.getBlockEntity(pos.above(y).relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
				if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;
				if (neighbor.getEssentiaAmount(dir.getOpposite()) <= 0) continue;
				if (neighbor.getSuctionAmount(dir.getOpposite()) >= getSuctionAmount(dir)) continue;
				if (getSuctionAmount(dir) < neighbor.getMinimumSuction()) continue;
				int taken = neighbor.takeEssentia(currentSuction, 1, dir.getOpposite());
				if (taken > 0) {
					addToContainer(currentSuction, taken);
					return;
				}
			}
		}
	}

	private int addToContainer(Aspect tag, int amount) {
		int need = recipe.getAmount(tag) - recipeProgress.getAmount(tag);
		if (need <= 0) return amount;
		int add = Math.min(need, amount);
		recipeProgress.add(tag, add);
		setChanged();
		return amount - add;
	}

	private Direction getFacing() {
		return getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	public boolean isConnectable(Direction face) {
		return face != getFacing();
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return face != getFacing();
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
		return canInputFrom(face) ? amount - addToContainer(aspect, amount) : 0;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		recipe.writeToNBT(tag, "recipe");
		recipeProgress.writeToNBT(tag, "progress");
		tag.putInt("charges", charges);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		(recipe = new AspectList()).readFromNBT(tag, "recipe");
		(recipeProgress = new AspectList()).readFromNBT(tag, "progress");
		charges = tag.getInt("charges");
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
