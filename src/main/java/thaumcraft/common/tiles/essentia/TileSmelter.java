package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileBellows;
import thaumcraft.common.world.aura.AuraHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the old (1.12.2) {@code TileSmelter}. A furnace variant that smelts an item's
 * {@link AspectHelper#getObjectAspects real object aspects} into stored essentia instead of (or alongside,
 * for anything with a real vanilla smelting recipe too - unrelated system, untouched) a smelted result -
 * accumulates them internally up to a cap, then periodically feeds one unit at a time to a
 * {@link TileAlembic} stack above via {@link TileAlembic#processAlembics}. First real consumer of
 * {@link AspectHelper#getObjectAspects} for arbitrary items (not just {@code IEssentiaContainerItem}).
 *
 * Simplified vs the original, all real features:
 * <ul>
 * <li><b>No GUI/menu.</b> The original opened a progress-bar screen (fuel/cook/vis gauges). This port uses
 * plain right-click item routing instead (matching the input-item-type-decides-the-slot convention already
 * used for {@code BlockPedestal}): right-click with a fuel item feeds the fuel slot, anything else with real
 * object aspects feeds the input slot, sneak+empty-hand clears the input slot back to the player. A real
 * {@link Container} implementation (not {@code WorldlyContainer}) still gives free hopper compatibility,
 * just without the original's side-specific slot routing (that nuance - fuel only from below, input only
 * from the sides, no extraction slot - is dropped along with it).</li>
 * <li><b>Real 3-tier progression (Basic/Thaumium/Void), same shared class</b> - {@link #getTier} reads which
 * real registered {@code smelterThaumium}/{@code smelterVoid} block this tile's own state belongs to, no new
 * class needed (same shape {@code BlockSmelter} already reused for all three). {@link #getEfficiency}
 * matches the true original's own real per-tier numbers exactly (0.8/0.9/0.95); {@link #getSpeedFactor}
 * preserves a real quirk in the true original's own {@code getSpeed} formula - only Thaumium actually
 * smelts faster (~1.5x), Void's own real advantage is efficiency alone.</li>
 * <li><b>Real Bellows speed boost, real aux/vent auxiliary blocks (restored - see the port-tracking memory's
 * parity-review notes).</b> Up to 3 {@link TileBellows} on the smelter's non-facing sides each cut
 * {@link #smeltTime} by 12.5%, same real formula/counting (via {@link TileBellows#getBellows}) as the true
 * original. A {@code smelterAux} block on a non-facing side is a real second (third, with two) simultaneous
 * essentia output point - see {@link #tick}. A {@code smelterVent} block gets a real chance to "vent" each
 * unit of smelting flux (a sound cue, no gameplay effect beyond that) instead of it polluting the local aura
 * directly - see {@link #ventOrPollute} for why this isn't a literal translation of the original's own
 * {@code smeltItem} (a real, fixed bug, not a simplification).</li>
 * <li>Alumentum fuel still grants a speed boost (a real, already-ported item, cheap to keep).</li>
 * </ul>
 */
public class TileSmelter extends BlockEntity implements Container {

	private static final int MAX_VIS = 256;

	private ItemStack inputItem = ItemStack.EMPTY;
	private ItemStack fuelItem = ItemStack.EMPTY;
	public AspectList aspects = new AspectList();

	private int burnTime;
	private int fuelBurnTime;
	private int cookTime;
	private int smeltTime = 100;
	private boolean speedBoost;
	private int count;
	private int bellows;

	public TileSmelter(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.SMELTER.get(), pos, state);
	}

	public boolean isLit() {
		return burnTime > 0;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileSmelter tile) {
		boolean wasLit = tile.isLit();
		if (tile.burnTime > 0) tile.burnTime--;
		if (level.isClientSide) return;

		tile.count++;
		boolean changed = false;

		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		if (tile.count % 10 == 0) {
			tile.bellows = computeBellows(level, pos, facing);
		}

		if (tile.burnTime == 0 && tile.canSmelt()) {
			int fuelTime = tile.fuelItem.getBurnTime(null);
			if (fuelTime > 0) {
				tile.speedBoost = tile.fuelItem.is(ItemsTC.alumentum.get());
				tile.burnTime = tile.speedBoost ? fuelTime * 2 : fuelTime;
				tile.fuelBurnTime = tile.burnTime;
				tile.fuelItem.shrink(1);
				changed = true;
			}
		}

		if (tile.isLit() && tile.canSmelt()) {
			tile.cookTime++;
			if (tile.cookTime >= tile.smeltTime) {
				tile.cookTime = 0;
				tile.smeltItem(level, pos, state);
				changed = true;
			}
		} else {
			tile.cookTime = 0;
		}

		if (tile.count % 10 == 0 && tile.aspects.size() > 0) {
			for (Aspect aspect : tile.aspects.getAspects()) {
				if (tile.aspects.getAmount(aspect) > 0 && TileAlembic.processAlembics(level, pos, aspect)) {
					tile.aspects.reduce(aspect, 1);
					changed = true;
					break;
				}
			}
			// A real smelterAux on a non-facing side is a second (third, with two) simultaneous essentia
			// output point - see BlockSmelterAux's own doc comment for the true original's own real mechanic
			// this restores (TileSmelter#update's own aux loop, confirmed via git history).
			for (Direction face : Direction.Plane.HORIZONTAL) {
				if (face == facing) continue;
				BlockState auxState = level.getBlockState(pos.relative(face));
				if (!auxState.is(BlocksTC.smelterAux.get()) || auxState.getValue(BlockStateProperties.HORIZONTAL_FACING) != face.getOpposite()) {
					continue;
				}
				BlockPos auxPos = pos.relative(face);
				for (Aspect aspect : tile.aspects.getAspects()) {
					if (tile.aspects.getAmount(aspect) > 0 && TileAlembic.processAlembics(level, auxPos, aspect)) {
						tile.aspects.reduce(aspect, 1);
						changed = true;
						break;
					}
				}
			}
		}

		if (tile.isLit() != wasLit) {
			level.setBlock(pos, state.setValue(BlockStateProperties.LIT, tile.isLit()), 3);
			changed = true;
		}
		if (changed) tile.setChanged();
	}

	/**
	 * The true original's own {@code TileSmelter#checkNeighbours}: the 3 horizontal directions that AREN'T
	 * the smelter's own facing ("door") side, fed to {@link TileBellows#getBellows}.
	 */
	private static int computeBellows(Level level, BlockPos pos, Direction facing) {
		Direction[] sides = new Direction[3];
		int i = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			if (dir != facing) sides[i++] = dir;
		}
		return TileBellows.getBellows(level, pos, sides);
	}

	/**
	 * The true original's own {@code TileSmelter#getType} (0=Basic/1=Thaumium/2=Void) - which real
	 * registered {@link net.minecraft.world.level.block.Block} this tile's own state belongs to, same
	 * "shared tile, tier-by-block-identity" shape {@link #getEfficiency}/{@link #getSpeedFactor} both use.
	 */
	private static int getTier(BlockState state) {
		if (state.is(BlocksTC.smelterThaumium.get())) return 1;
		if (state.is(BlocksTC.smelterVoid.get())) return 2;
		return 0;
	}

	/** The true original's own real per-tier {@code getEfficiency}: Basic 0.8, Thaumium 0.9, Void 0.95. */
	private static float getEfficiency(BlockState state) {
		return switch (getTier(state)) {
			case 1 -> 0.9f;
			case 2 -> 0.95f;
			default -> 0.8f;
		};
	}

	/**
	 * The true original's own real {@code getSpeed} (20 - (type==1 ? 10 : 5)): only the Thaumium tier
	 * actually smelts faster (a real ~1.5x, {@code 15/10}) - Void's own real advantage is
	 * {@link #getEfficiency} alone, not speed. Preserved faithfully rather than "fixed" - a real quirk of
	 * the true original, not a porting bug.
	 */
	private static float getSpeedFactor(BlockState state) {
		return getTier(state) == 1 ? 2f / 3f : 1f;
	}

	private boolean canSmelt() {
		if (inputItem.isEmpty()) return false;
		AspectList tags = AspectHelper.getObjectAspects(inputItem);
		if (tags == null || tags.size() == 0) return false;
		int vs = tags.visSize();
		if (vs > MAX_VIS - aspects.visSize()) return false;
		smeltTime = Math.max(1, (int) (vs * 2 * (1.0f - 0.125f * bellows) * getSpeedFactor(getBlockState())));
		return true;
	}

	private void smeltItem(Level level, BlockPos pos, BlockState state) {
		if (!canSmelt()) return;
		AspectList tags = AspectHelper.getObjectAspects(inputItem);
		float efficiency = getEfficiency(state);
		int flux = 0;
		for (Aspect a : tags.getAspects()) {
			int qq = tags.getAmount(a);
			int kept = qq;
			for (int q = 0; q < qq; q++) {
				if (level.getRandom().nextFloat() > (a == Aspect.FLUX ? efficiency * 0.66f : efficiency)) {
					kept--;
					flux++;
				}
			}
			if (kept > 0) aspects.add(a, kept);
		}
		if (flux > 0) {
			ventOrPollute(level, pos, state, flux);
		}
		inputItem.shrink(1);
		setChanged();
	}

	/**
	 * Real adaptation of the true original's own {@code TileSmelter#smeltItem} flux handling: each
	 * {@code smelterVent} on a non-facing side gets a real 33% chance (same real number) to "vent" a unit of
	 * flux with a sound cue instead of it polluting the local aura directly. <b>Not</b> a literal translation
	 * of the decompiled original, which retries an unconditioned {@code while(true)} loop per flux unit until
	 * some vent succeeds - with zero real vent blocks present, that condition can never become true, so the
	 * loop never exits (a real, apparent hang bug in the true original, not a deliberate mechanic). Here, each
	 * flux unit gets one roll against whatever real vent sides currently exist (still a real, if smaller,
	 * "more vents = less local pollution" incentive) and always terminates.
	 */
	private static void ventOrPollute(Level level, BlockPos pos, BlockState state, int flux) {
		Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
		List<Direction> ventSides = new ArrayList<>();
		for (Direction face : Direction.Plane.HORIZONTAL) {
			if (face == facing) continue;
			BlockState ventState = level.getBlockState(pos.relative(face));
			if (ventState.is(BlocksTC.smelterVent.get()) && ventState.getValue(BlockStateProperties.HORIZONTAL_FACING) == face.getOpposite()) {
				ventSides.add(face);
			}
		}

		int pollution = 0;
		for (int i = 0; i < flux; i++) {
			boolean vented = false;
			for (Direction side : ventSides) {
				if (level.getRandom().nextFloat() < 0.333f) {
					vented = true;
					level.playSound(null, pos.getX() + 0.5 + side.getStepX() * 0.5, pos.getY() + 0.5, pos.getZ() + 0.5 + side.getStepZ() * 0.5,
							SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.25f,
							2.6f + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.8f);
					break;
				}
			}
			if (!vented) pollution++;
		}
		if (pollution > 0) {
			AuraHandler.addFlux(level, pos, pollution);
		}
	}

	@Override
	public int getContainerSize() {
		return 2;
	}

	@Override
	public boolean isEmpty() {
		return inputItem.isEmpty() && fuelItem.isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return slot == 0 ? inputItem : fuelItem;
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = (slot == 0 ? inputItem : fuelItem).split(amount);
		if (!result.isEmpty()) setChanged();
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack result = slot == 0 ? inputItem : fuelItem;
		if (slot == 0) inputItem = ItemStack.EMPTY;
		else fuelItem = ItemStack.EMPTY;
		return result;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		if (slot == 0) inputItem = stack;
		else fuelItem = stack;
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return player.level().getBlockEntity(worldPosition) == this && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		if (slot == 1) return AbstractFurnaceBlockEntity.isFuel(stack);
		AspectList tags = AspectHelper.getObjectAspects(stack);
		return tags != null && tags.size() > 0;
	}

	@Override
	public void clearContent() {
		inputItem = ItemStack.EMPTY;
		fuelItem = ItemStack.EMPTY;
	}

	/**
	 * Right-click routing (see this class's own doc comment): a fuel item goes in the fuel slot, anything
	 * else with real object aspects goes in the input slot, sneak+empty-hand takes the input item back.
	 */
	public boolean interact(Level level, Player player, ItemStack held) {
		if (level.isClientSide) return true;
		if (held.isEmpty()) {
			if (player.isCrouching() && !inputItem.isEmpty()) {
				if (!player.getInventory().add(inputItem)) player.drop(inputItem, false);
				inputItem = ItemStack.EMPTY;
				setChanged();
				return true;
			}
			return false;
		}
		if (AbstractFurnaceBlockEntity.isFuel(held) && fuelItem.isEmpty()) {
			fuelItem = held.copyWithCount(1);
			held.shrink(1);
			setChanged();
			return true;
		}
		if (inputItem.isEmpty() && canPlaceItem(0, held)) {
			inputItem = held.copyWithCount(1);
			held.shrink(1);
			setChanged();
			return true;
		}
		return false;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		inputItem = tag.contains("Input") ? ItemStack.parseOptional(registries, tag.getCompound("Input")) : ItemStack.EMPTY;
		fuelItem = tag.contains("Fuel") ? ItemStack.parseOptional(registries, tag.getCompound("Fuel")) : ItemStack.EMPTY;
		burnTime = tag.getInt("BurnTime");
		fuelBurnTime = tag.getInt("FuelBurnTime");
		cookTime = tag.getInt("CookTime");
		aspects = new AspectList();
		aspects.readFromNBT(tag, "aspects");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (!inputItem.isEmpty()) tag.put("Input", inputItem.save(registries, new CompoundTag()));
		if (!fuelItem.isEmpty()) tag.put("Fuel", fuelItem.save(registries, new CompoundTag()));
		tag.putInt("BurnTime", burnTime);
		tag.putInt("FuelBurnTime", fuelBurnTime);
		tag.putInt("CookTime", cookTime);
		aspects.writeToNBT(tag, "aspects");
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}
}
