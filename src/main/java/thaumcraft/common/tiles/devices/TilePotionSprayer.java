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
 * Each charge costs the potion's own essentia, as the original: the aspects of its brewing ingredients, cut by 66%
 * and culled to 10 kinds ({@link thaumcraft.common.lib.crafting.PotionAspects}). Its screen holds the potion.
 *
 * <p>Real client-side spray-vent particle burst restored 2026-09-16 - see {@link #venting}'s own doc comment
 * (this class's own doc comment previously called it dropped "visual-only... like every other device this
 * session," which was itself only ever true because this specific FX class - {@code FXVent2} - had no real
 * proven client-particle precedent to reuse yet).
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

	/** Real client-only spray-vent countdown, matching the true original's own real field exactly -
	 * deliberately plain (not synced): set to {@code 15} by a real vanilla entity-event broadcast ({@link
	 * #triggerEvent}, id {@code 0}, fired by {@link #spray} on every real successful spray), then ticked down
	 * independently on the client. */
	private int venting;
	private int ventColor = 0xAAAAAA;

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

	/** The loaded potion's colour, for the screen's charge column (the original's {@code color}). */
	private int color = 0x333333;

	public int getColor() {
		return color;
	}

	public AspectList getRecipe() {
		return recipe;
	}

	public AspectList getRecipeProgress() {
		return recipeProgress;
	}

	/** The essentia one charge costs is the loaded potion's real cost (its brewing reagents' aspects, see
	 * {@link thaumcraft.common.lib.crafting.PotionAspects}), culled to 10 kinds - it was a flat 10 Alchemy for every
	 * potion before 2026-09-25. */
	private void recalcRecipe() {
		ItemStack stack = items.getStackInSlot(0);
		if (level == null) {
			recipe = stack.isEmpty() ? new AspectList() : new AspectList().add(Aspect.ALCHEMY, 10);
		} else if (stack.isEmpty()) {
			recipe = new AspectList();
			color = 0x333333;
		} else {
			recipe = thaumcraft.api.aspects.AspectHelper.cullTags(thaumcraft.common.lib.crafting.PotionAspects.of(level, stack), 10);
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			color = contents != null ? contents.getColor() : 0x333333;
		}
		recipeProgress = new AspectList();
		charges = 0;
		currentSuction = null;
		setChanged();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TilePotionSprayer tile) {
		if (level.isClientSide) {
			tile.tickVenting(level, pos, state.getValue(BlockStateProperties.HORIZONTAL_FACING));
			return;
		}
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
		level.blockEvent(pos, getBlockState().getBlock(), 0, contents != null ? contents.getColor() : 0xAAAAAA);
	}

	/** Real modern equivalent of the true original's own real client-side {@code onUpdate} venting branch -
	 * see {@link #venting}'s own doc comment for the real trigger. Real, checked-not-assumed exact formula
	 * (half as many particles as {@link #venting} itself, matching the true original's own real {@code
	 * venting / 2} loop bound exactly), each real particle offset/aimed along the sprayer's own real facing. */
	private void tickVenting(Level level, BlockPos pos, Direction facing) {
		if (venting <= 0) {
			return;
		}
		venting--;
		var random = level.getRandom();
		for (int i = 0; i < venting / 2; i++) {
			float fx = 0.1f - random.nextFloat() * 0.2f;
			float fy = 0.1f - random.nextFloat() * 0.2f;
			float fz = 0.1f - random.nextFloat() * 0.2f;
			float mx = (float) (random.nextGaussian() * 0.06) + facing.getStepX() * 0.25f;
			float my = (float) (random.nextGaussian() * 0.06) + facing.getStepY() * 0.25f;
			float mz = (float) (random.nextGaussian() * 0.06) + facing.getStepZ() * 0.25f;
			thaumcraft.client.fx.ClientFxHandlers.spawnVentBurst2(level,
					pos.getX() + 0.5 + fx + facing.getStepX() / 2.0, pos.getY() + 0.5 + fy + facing.getStepY() / 2.0, pos.getZ() + 0.5 + fz + facing.getStepZ() / 2.0,
					mx, my, mz, ventColor, 4.0f);
		}
	}

	/** Real modern equivalent of the true original's own real {@code receiveClientEvent} - {@code id == 0}
	 * matches that exact real event id; {@code type} (the event's own generic second int parameter) carries
	 * the real sprayed potion's own colour across to the client, since {@link #venting}'s own real particle
	 * burst needs it and this tile has no other real per-particle colour state to read at render time. */
	@Override
	public boolean triggerEvent(int id, int type) {
		if (id == 0) {
			venting = 15;
			ventColor = type;
			return true;
		}
		return super.triggerEvent(id, type);
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
		tag.putInt("color", color);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		(recipe = new AspectList()).readFromNBT(tag, "recipe");
		(recipeProgress = new AspectList()).readFromNBT(tag, "progress");
		charges = tag.getInt("charges");
		color = tag.contains("color") ? tag.getInt("color") : 0x333333;
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	/** Pushes recipe progress and charges to clients for the screen - same fix as {@code TilePedestal#setChanged}. */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		}
	}
}
