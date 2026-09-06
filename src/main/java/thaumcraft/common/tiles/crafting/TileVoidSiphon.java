package thaumcraft.common.tiles.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileVoidSiphon} - drains nearby {@link EntityFluxRift}s (within 8
 * blocks, real line of sight required, rift size >= 2) to charge up progress toward producing Void Seeds,
 * the same {@code sqrt(riftSize)}-per-tick drain/shrink-chance formula the original used. {@code ENABLED}
 * is the same redstone-inverted convention every other toggled device this session uses.
 *
 * Simplified vs the original: no GUI - right-click empties the output slot into the player's hand/inventory
 * directly, same no-menu convention {@link thaumcraft.common.blocks.devices.BlockRechargePedestal} already
 * established (the original's GUI was read-only progress display plus item collection anyway - nothing a
 * player could configure). No client-side void-streak particle FX ({@code FXDispatcher}) - visual-only,
 * dropped like every other device this session. {@code EntityUtils#canEntityBeSeen}/{@code getEntitiesInRange}
 * (the original's own helpers - stale, Baubles-coupled) are replaced with real vanilla equivalents directly:
 * {@link Level#getEntitiesOfClass} and a direct {@link Level#clip} raycast for the line-of-sight check.
 */
public class TileVoidSiphon extends BlockEntity {

	private static final int PROGRESS_REQUIRED = 2000;

	private final ItemStackHandler items = new ItemStackHandler(1) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return false;
		}

		@Override
		protected void onContentsChanged(int slot) {
			setChanged();
		}
	};

	private int counter;
	private int progress;

	public TileVoidSiphon(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.VOID_SIPHON.get(), pos, state);
	}

	public ItemStack getItem() {
		return items.getStackInSlot(0);
	}

	public IItemHandler getItemHandler() {
		return items;
	}

	public int getProgress() {
		return progress;
	}

	private boolean canAcceptOutput() {
		ItemStack stack = items.getStackInSlot(0);
		return stack.isEmpty() || (stack.is(ItemsTC.voidSeed.get()) && stack.getCount() < stack.getMaxStackSize());
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileVoidSiphon tile) {
		if (level.isClientSide || !state.getValue(BlockStateProperties.ENABLED)) return;
		tile.counter++;
		if (tile.counter % 20 != 0 || tile.progress >= PROGRESS_REQUIRED || !tile.canAcceptOutput()) return;

		List<EntityFluxRift> rifts = tile.getValidRifts(level, pos);
		for (EntityFluxRift rift : rifts) {
			double drain = Math.sqrt(rift.getRiftSize());
			tile.progress += (int) drain;
			rift.setRiftStability((float) (rift.getRiftStability() - drain / 15.0));
			if (level.random.nextInt(33) == 0) {
				rift.setRiftSize(rift.getRiftSize() - 1);
			}
		}

		boolean produced = false;
		while (tile.progress >= PROGRESS_REQUIRED && tile.canAcceptOutput()) {
			tile.progress -= PROGRESS_REQUIRED;
			ItemStack stack = tile.items.getStackInSlot(0);
			if (stack.isEmpty()) {
				tile.items.setStackInSlot(0, new ItemStack(ItemsTC.voidSeed.get()));
			} else {
				stack.grow(1);
			}
			produced = true;
		}
		if (produced || !rifts.isEmpty()) {
			tile.setChanged();
		}
	}

	private List<EntityFluxRift> getValidRifts(Level level, BlockPos pos) {
		List<EntityFluxRift> found = new ArrayList<>();
		double cx = pos.getX() + 0.5;
		double cy = pos.getY() + 1;
		double cz = pos.getZ() + 0.5;
		for (EntityFluxRift rift : level.getEntitiesOfClass(EntityFluxRift.class, new AABB(pos).inflate(8.0))) {
			if (!rift.isAlive() || rift.getRiftSize() < 2) continue;
			// A real block-collision raycast from the rift to the siphon's collection point, matching the
			// original's own EntityUtils#canEntityBeSeen check (the rift is the looker, the siphon the
			// target) - vanilla's own Entity#hasLineOfSight only checks sight *to* another entity, so this
			// uses Level#clip directly instead.
			net.minecraft.world.phys.Vec3 from = new net.minecraft.world.phys.Vec3(rift.getX(), rift.getY(), rift.getZ());
			net.minecraft.world.phys.Vec3 to = new net.minecraft.world.phys.Vec3(cx, cy, cz);
			net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(from, to,
					net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, rift);
			if (level.clip(ctx).getType() != net.minecraft.world.phys.HitResult.Type.MISS) continue;
			found.add(rift);
		}
		return found;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("Items", items.serializeNBT(registries));
		tag.putShort("progress", (short) progress);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items.deserializeNBT(registries, tag.getCompound("Items"));
		progress = tag.getShort("progress");
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
