package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileMirror} - the Node Transducer's item-transport half. Two mirrors
 * become mutually linked ({@link #link}) via {@link thaumcraft.common.blocks.devices.BlockMirrorItem}'s own
 * "charge a copy of the item with this mirror's coordinates, then place it elsewhere" workflow (see that
 * class's own doc comment) - an item entity that touches a linked mirror ({@link #transport}, called from
 * {@link thaumcraft.common.blocks.devices.BlockMirror#entityInside}) is queued on the OTHER mirror
 * ({@link #outputStacks}) and trickles back out there one unit at a time.
 *
 * As the original, a hopper (or pipe) feeding a mirror with a partner sends the items through too
 * ({@link #getItemHandler}: a slot that always reads empty), a Hand Mirror delivers straight out of the mirror it is
 * linked to ({@link #transportDirect}), and items going in or coming out raise wispy motes (block event 1). A
 * freshly loaded mirror waits a second before ejecting. {@link #link} is one nullable {@link MirrorLink} record
 * instead of a {@code linked} boolean plus 4 raw ints.
 */
public class TileMirror extends BlockEntity {

	private static final int BASE_CYCLE = 40;
	private static final int MAX_CYCLE = 600;

	@Nullable
	private MirrorLink link;
	private final List<ItemStack> outputStacks = new ArrayList<>();
	private int instability;
	private int count;
	private int cycle = BASE_CYCLE;

	public TileMirror(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.MIRROR.get(), pos, state);
	}

	protected TileMirror(net.minecraft.world.level.block.entity.BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	/** Called by {@code BlockMirrorItem#place} once a pre-charged mirror item is placed - immediately tries
	 * to confirm the mutual pairing rather than waiting for the next tick cycle. */
	public void applyLink(MirrorLink newLink) {
		this.link = newLink;
		setChanged();
		tryRestoreLink();
	}

	@Nullable
	private TileMirror targetTile() {
		if (link == null || !(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		ServerLevel targetLevel = serverLevel.getServer().getLevel(link.dimension());
		if (targetLevel == null || !targetLevel.isLoaded(link.pos())) {
			return null;
		}
		return targetLevel.getBlockEntity(link.pos()) instanceof TileMirror tile ? tile : null;
	}

	/** Mutual pairing confirmed - our link points at a real mirror, and that mirror's own link points right
	 * back at us. */
	public boolean isLinkValid() {
		TileMirror target = targetTile();
		return target != null && target.link != null
				&& target.link.dimension().equals(level.dimension()) && target.link.pos().equals(worldPosition);
	}

	private void tryRestoreLink() {
		TileMirror target = targetTile();
		if (target != null && !target.isLinkValid()) {
			target.link = new MirrorLink(level.dimension(), worldPosition.immutable());
			target.setChanged();
			setChanged();
		}
	}

	/** @return whether the item was accepted and consumed. */
	public boolean transport(ItemEntity itemEntity) {
		if (!isLinkValid()) {
			return false;
		}
		TileMirror target = targetTile();
		if (target == null) {
			return false;
		}
		target.outputStacks.add(itemEntity.getItem().copy());
		target.setChanged();
		instability += itemEntity.getItem().getCount();
		setChanged();
		itemEntity.discard();
		level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
		return true;
	}

	/** The original's {@code transportDirect}, the Hand Mirror's delivery: the items come out of THIS mirror (the one
	 * the Hand Mirror is linked to), whether or not it has a partner. @return whether there was anything to send. */
	public boolean transportDirect(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		outputStacks.add(stack.copy());
		setChanged();
		return true;
	}

	/** The original's IInventory face: one slot that always reads empty; what's put in goes to the partner (or, with
	 * no partner tile, drops out of this mirror). Only accepts while a partner mirror exists. */
	private final net.neoforged.neoforge.items.IItemHandler itemHandler = new net.neoforged.neoforge.items.IItemHandler() {
		@Override
		public int getSlots() {
			return 1;
		}

		@Override
		public ItemStack getStackInSlot(int slot) {
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
			if (simulate) return ItemStack.EMPTY;
			TileMirror target = targetTile();
			if (target != null) {
				target.outputStacks.add(stack.copy());
				target.setChanged();
				instability += stack.getCount();
				setChanged();
				level.blockEvent(worldPosition, getBlockState().getBlock(), 1, 0);
			}
			return ItemStack.EMPTY;
		}

		@Override
		public ItemStack extractItem(int slot, int amount, boolean simulate) {
			return ItemStack.EMPTY;
		}

		@Override
		public int getSlotLimit(int slot) {
			return 64;
		}

		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return targetTile() != null;
		}
	};

	public net.neoforged.neoforge.items.IItemHandler getItemHandler() {
		return itemHandler;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileMirror tile) {
		if (level.isClientSide) {
			return;
		}
		tile.eject(level, pos, state);
		tile.checkInstability(level, pos);
		tile.count++;
		if (tile.count % tile.cycle == 0) {
			if (!tile.isLinkValid()) {
				tile.cycle = Math.min(MAX_CYCLE, tile.cycle + 20);
				tile.tryRestoreLink();
			} else {
				tile.cycle = BASE_CYCLE;
			}
		}
	}

	private void eject(Level level, BlockPos pos, BlockState state) {
		if (outputStacks.isEmpty() || count <= 20) {
			return;
		}
		int i = level.random.nextInt(outputStacks.size());
		ItemStack queued = outputStacks.get(i);
		if (queued.isEmpty()) {
			outputStacks.remove(i);
			return;
		}
		Direction facing = state.getValue(BlockStateProperties.FACING);
		ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.25, pos.getZ() + 0.5, queued.copyWithCount(1));
		itemEntity.setDeltaMovement(facing.getStepX() * 0.15, facing.getStepY() * 0.15, facing.getStepZ() * 0.15);
		itemEntity.setPortalCooldown();
		level.addFreshEntity(itemEntity);
		queued.shrink(1);
		instability++;
		level.blockEvent(pos, state.getBlock(), 1, 0);
		if (queued.isEmpty()) {
			outputStacks.remove(i);
		}
		setChanged();
	}

	private void checkInstability(Level level, BlockPos pos) {
		if (instability > 128) {
			AuraHelper.polluteAura(level, pos, 1.0f, true);
			instability -= 128;
			setChanged();
		}
		if (instability > 0 && count % 100 == 0) {
			instability--;
			setChanged();
		}
	}

	@Nullable
	public MirrorLink getLink() {
		return link;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (link != null) {
			MirrorLink.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), link)
					.result().ifPresent(encoded -> tag.put("link", encoded));
		}
		tag.putInt("instability", instability);
		ListTag items = new ListTag();
		for (ItemStack stack : outputStacks) {
			if (!stack.isEmpty()) {
				items.add(stack.save(registries, new CompoundTag()));
			}
		}
		tag.put("items", items);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		link = tag.contains("link")
				? MirrorLink.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("link")).result().orElse(null)
				: null;
		instability = tag.getInt("instability");
		outputStacks.clear();
		ListTag items = tag.getList("items", Tag.TAG_COMPOUND);
		for (int i = 0; i < items.size(); i++) {
			ItemStack stack = ItemStack.parseOptional(registries, items.getCompound(i));
			if (!stack.isEmpty()) {
				outputStacks.add(stack);
			}
		}
	}
}
