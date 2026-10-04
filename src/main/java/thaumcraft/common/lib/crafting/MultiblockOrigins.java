package thaumcraft.common.lib.crafting;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks a Salis Mundus multiblock (Infernal Furnace, Golem Press) was built from, so taking it apart puts back
 * exactly those - which matters once a blueprint accepts any block in a tag. The conversion records them
 * ({@link #record}), the device's block entity claims them on its first tick ({@link #claim}) and keeps them
 * ({@link Store}); a part broken out of the structure drops its own original block ({@link #takeDrop}).
 */
public final class MultiblockOrigins {

	/** Conversions waiting for their device's block entity, by every position they convert. */
	private static final Map<GlobalPos, Map<BlockPos, BlockState>> PENDING = new ConcurrentHashMap<>();
	/** Original blocks of parts just broken out of a structure, waiting for that part's drops. */
	private static final Map<GlobalPos, BlockState> DROPS = new ConcurrentHashMap<>();

	private MultiblockOrigins() {
	}

	public static void record(Level level, Map<BlockPos, BlockState> originals) {
		Map<BlockPos, BlockState> copy = Map.copyOf(originals);
		for (BlockPos p : copy.keySet()) PENDING.put(GlobalPos.of(level.dimension(), p), copy);
	}

	@Nullable
	public static Map<BlockPos, BlockState> claim(Level level, BlockPos pos) {
		Map<BlockPos, BlockState> found = PENDING.remove(GlobalPos.of(level.dimension(), pos));
		if (found != null) {
			for (BlockPos p : found.keySet()) PENDING.remove(GlobalPos.of(level.dimension(), p));
		}
		return found;
	}

	/** The part at {@code pos} is being broken out: its drops should be {@code original}'s. */
	public static void dropAs(Level level, BlockPos pos, BlockState original) {
		if (DROPS.size() > 64) DROPS.clear(); // drops that never happened (creative breaking)
		DROPS.put(GlobalPos.of(level.dimension(), pos.immutable()), original);
	}

	@Nullable
	public static BlockState takeDrop(Level level, BlockPos pos) {
		return DROPS.remove(GlobalPos.of(level.dimension(), pos));
	}

	/** What a device's block entity keeps: absolute positions to the blocks that were there. */
	public static final class Store {
		private final Map<BlockPos, BlockState> originals = new HashMap<>();

		public boolean isEmpty() {
			return originals.isEmpty();
		}

		public Map<BlockPos, BlockState> all() {
			return originals;
		}

		@Nullable
		public BlockState at(BlockPos pos) {
			return originals.get(pos);
		}

		public void claim(Level level, BlockPos self) {
			Map<BlockPos, BlockState> found = MultiblockOrigins.claim(level, self);
			if (found != null) {
				originals.clear();
				originals.putAll(found);
			}
		}

		/** Saved relative to the device, so a moved structure (a schematic paste) still makes sense. */
		public void save(CompoundTag tag, BlockPos self) {
			if (originals.isEmpty()) return;
			ListTag list = new ListTag();
			originals.forEach((p, s) -> {
				CompoundTag e = new CompoundTag();
				e.put("Pos", NbtUtils.writeBlockPos(p.subtract(self)));
				e.put("State", NbtUtils.writeBlockState(s));
				list.add(e);
			});
			tag.put("Originals", list);
		}

		public void load(CompoundTag tag, BlockPos self, HolderLookup.Provider registries) {
			originals.clear();
			if (!tag.contains("Originals", Tag.TAG_LIST)) return;
			var blocks = registries.lookupOrThrow(Registries.BLOCK);
			for (Tag t : tag.getList("Originals", Tag.TAG_COMPOUND)) {
				CompoundTag e = (CompoundTag) t;
				NbtUtils.readBlockPos(e, "Pos").ifPresent(rel -> originals.put(self.offset(rel), NbtUtils.readBlockState(blocks, e.getCompound("State"))));
			}
		}
	}

	/** A device that keeps its originals. */
	public interface Holder {
		Store origins();
	}
}
