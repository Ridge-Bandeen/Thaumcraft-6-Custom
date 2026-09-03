package thaumcraft.common.golems.seals;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;

/**
 * Replaces the old (1.12.2) {@code SealHandler} - the common-side convenience layer built on top of
 * {@link GolemHelper}'s own real registries (see that class's own doc comment for why the state itself
 * lives there, not here). {@link #addSealEntity}/{@link #removeSealEntity}/{@link #tickSealEntities} are
 * the non-API-contract operations {@link ISeal}/{@link ISealEntity} implementations and this port's own
 * {@code ItemSealPlacer}/golem AI need on top of the 5-method public API surface.
 *
 * Real, documented cut: placed seals are NOT persisted across a server restart yet - {@code GolemHelper}'s
 * seal-entity map is purely in-memory for the current server session, same as the original's own
 * {@code ConcurrentHashMap} field (that map was never itself written to a save file either - the original
 * relied on a separate, still-unported persistence path this port hasn't scoped yet). Real follow-up work,
 * not silently different from the original's own actual (also incomplete without that separate path)
 * behavior.
 */
public final class SealHandler {

	/** Registers every real seal TYPE this port ships - {@code Thaumcraft#commonSetup} must call this, not a
	 * static initializer (same registry-timing rule {@code GolemProperties#init}/{@code ThaumcraftObjectTags#init}
	 * already established, though nothing here actually needs a fired {@code RegisterEvent} yet - kept
	 * consistent with that same call site regardless). Each registered prototype is used only for its
	 * {@link ISeal#getKey()} and class (for {@link GolemHelper#createSeal} to reflectively instantiate fresh
	 * per-placement copies from) - see that method's own doc comment for why a fresh instance matters. */
	public static void init() {
		GolemHelper.registerSeal(new SealGuard());
	}

	public static ISeal getSeal(String key) {
		return GolemHelper.getSeal(key);
	}

	public static ISealEntity getSealEntity(ResourceKey<Level> dim, SealPos pos) {
		return GolemHelper.getSealEntity(dim, pos);
	}

	/** Places a fresh {@code seal} instance at {@code pos}/{@code face} if nothing is already sealed there -
	 * real translation of the original's own {@code addSealEntity(World, BlockPos, EnumFacing, ISeal, EntityPlayer)}. */
	public static boolean addSealEntity(Level level, BlockPos pos, Direction face, ISeal seal, Player player) {
		Map<SealPos, ISealEntity> map = GolemHelper.getSealEntities(level.dimension());
		SealPos sp = new SealPos(pos, face);
		if (map.containsKey(sp)) return false;
		SealEntity entity = new SealEntity(sp, seal);
		entity.setOwner(player.getUUID().toString());
		map.put(sp, entity);
		if (!level.isClientSide) {
			entity.syncToClient(level);
		}
		return true;
	}

	/** For restoring a seal already reconstructed from NBT (its {@code SealPos} is already real). */
	public static boolean addSealEntity(Level level, ISealEntity entity) {
		if (entity.getSealPos() == null) return false;
		Map<SealPos, ISealEntity> map = GolemHelper.getSealEntities(level.dimension());
		if (map.containsKey(entity.getSealPos())) return false;
		map.put(entity.getSealPos(), entity);
		if (!level.isClientSide) {
			entity.syncToClient(level);
		}
		return true;
	}

	/** Removes whatever seal sits at {@code pos}, suspending every task still tied to it and notifying the
	 * seal itself via {@link ISeal#onRemoval} - real translation of the original's own {@code removeSealEntity}. */
	public static void removeSealEntity(Level level, SealPos pos) {
		Map<SealPos, ISealEntity> map = GolemHelper.getSealEntities(level.dimension());
		ISealEntity removed = map.remove(pos);
		if (removed == null) return;
		if (!level.isClientSide && removed.getSeal() != null) {
			removed.getSeal().onRemoval(level, pos.pos, pos.face);
		}
		for (Task task : GolemHelper.getTasks(level.dimension()).values()) {
			if (task.getSealPos() != null && task.getSealPos().equals(pos)) {
				task.setSuspended(true);
			}
		}
	}

	/** Called once per level tick (see {@code Thaumcraft#tickGolemSeals}) - ticks every seal placed in this
	 * dimension, removing any that can no longer place at their own position (matches the original's own
	 * every-20th-tick {@code canPlaceAt} recheck). */
	public static void tickSealEntities(Level level) {
		int tickCounter = (int) (level.getGameTime() % 20);
		for (ISealEntity entity : GolemHelper.getSealEntities(level.dimension()).values().toArray(new ISealEntity[0])) {
			if (entity.getSeal() == null) continue;
			if (tickCounter == 0 && !entity.getSeal().canPlaceAt(level, entity.getSealPos().pos, entity.getSealPos().face)) {
				removeSealEntity(level, entity.getSealPos());
				continue;
			}
			try {
				entity.tickSealEntity(level);
			} catch (Exception e) {
				removeSealEntity(level, entity.getSealPos());
			}
		}
	}

	private SealHandler() {
	}
}
