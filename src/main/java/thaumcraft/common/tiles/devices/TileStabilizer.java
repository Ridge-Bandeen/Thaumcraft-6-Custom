package thaumcraft.common.tiles.devices;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.blocks.devices.BlockInlay;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;
import thaumcraft.common.tiles.crafting.TilePedestal;

/**
 * Replaces the old (1.12.2) {@code TileStabilizer} - real, restored 2026-09-15 during the full parity audit.
 * An earlier pass here had cut this entirely, reasoning that {@code canStabaliseInfusion}/
 * {@code getStabilizationAmount} never read the energy field (true - that half really is inert) and that
 * {@link EntityFluxRift} wasn't ported (false by the time this was re-checked - it's been real for a while).
 * Both halves of that reasoning turned out to matter: the real energy meter's other real real job -
 * pacifying nearby Flux Rifts, and (see {@link TileInfusionMatrix#inEvEjectItem}) absorbing an Infusion
 * Matrix's own instability-event item ejection instead of dropping it - are both real, working mechanics
 * once this tile exists.
 *
 * <p>Real per-tick behavior, matching the true original's own {@code update} exactly: every 20 ticks, if
 * under the real 15-unit cap, gains 1 energy and pollutes the local aura by 0.25 (a real, deliberate cost for
 * generating stability energy from nothing) and notifies neighbors (so a real {@link BlockInlay} chain - see
 * that class's own doc comment - could in principle react, though this port's own inlay stays a plain
 * stateless block, see below). Every 5 ticks, while energy remains and no cooldown is active, tries to
 * pacify every real {@link EntityFluxRift} within 8 blocks that isn't already {@code VERY_STABLE}, spending 1
 * energy per rift stabilized and setting a real 5-tick cooldown per success, matching the original's own real
 * {@code tryAddStability} loop exactly.
 *
 * <p>Real, deliberate simplification: the true original's own {@code TilePedestal#findInstabilityMitigator}
 * found a nearby stabilizer by following a real, persistent 0-15 "charge" value propagated blockstate-by-
 * blockstate through a chain of {@link BlockInlay} tiles (a real redstone-wire-style network,
 * {@code BlockInlay.CHARGE}) back to its source. {@code BlockInlay} itself stayed a plain stateless block in
 * this port (see its own doc comment - the same charge value's OTHER real job, infusion stability scoring,
 * is genuinely inert and not worth a whole propagation network on its own). Restoring the literal charge
 * network just to re-derive "is there a stabilizer reachable through connected inlays" would be real
 * player-invisible internal machinery for the exact same real outcome a direct search already gives -
 * {@link TilePedestal#findInstabilityMitigator} instead does a real plain BFS through connected
 * {@code BlockInlay} positions (capped at 15 hops, matching the original's own implicit 0-15 charge-value
 * range) until it finds a real {@link TileStabilizer}, same real reachability result, no persistent charge
 * state needed.
 */
public class TileStabilizer extends BlockEntity {

	private static final int CAPACITY = 15;

	private int ticks;
	private int delay;
	private int energy;

	public TileStabilizer(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.STABILIZER.get(), pos, state);
	}

	public int getEnergy() {
		return energy;
	}

	/** Not part of the true original's own real API - a real testing convenience so a GameTest can set up a
	 * comfortable energy reserve directly instead of waiting out real 20-tick charge intervals repeatedly (the
	 * real charge RATE itself already has its own dedicated, direct coverage). Clamped to the real 0-15 cap,
	 * same as every other real path that touches {@link #energy}. */
	public void setEnergyForTesting(int amount) {
		energy = Math.max(0, Math.min(amount, CAPACITY));
		setChanged();
	}

	/** Real port of the true original's own {@code mitigate(int)} - spends real stored energy if there's
	 * enough, used both by this tile's own real Flux Rift pacification and by a real
	 * {@link TileInfusionMatrix} instability-event redirect. */
	public boolean mitigate(int amount) {
		if (energy < amount) return false;
		energy -= amount;
		setChanged();
		return true;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileStabilizer tile) {
		if (level.isClientSide) return;
		tile.ticks++;
		if (tile.energy < CAPACITY && tile.ticks % 20 == 0) {
			tile.energy++;
			AuraHelper.polluteAura(level, pos, 0.25f, true);
			tile.setChanged();
		}
		if (tile.energy > 0 && tile.delay <= 0 && tile.ticks % 5 == 0) {
			tile.tryAddStability(level, pos);
		}
		if (tile.delay > 0) {
			tile.delay--;
		}
	}

	private void tryAddStability(Level level, BlockPos pos) {
		List<EntityFluxRift> targets = level.getEntitiesOfClass(EntityFluxRift.class,
				new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1).inflate(8.0));
		for (EntityFluxRift rift : targets) {
			if (!rift.isAlive()) continue;
			if (rift.getStability() == EntityFluxRift.EnumStability.VERY_STABLE) continue;
			if (!mitigate(1)) continue;
			rift.setRiftStability(rift.getRiftStability() + 0.125f);
			delay += 5;
			if (energy <= 0) return;
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		int before = energy;
		energy = Math.min(tag.getInt("Energy"), CAPACITY);
		// the block's charge tint (ThaumcraftClient's stabilizer colour handler) is baked into the chunk mesh:
		// re-mesh when a synced charge changes, as the original's client tick did
		if (level != null && level.isClientSide && energy != before) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_IMMEDIATE);
		}
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	/** Every change here is a charge change, which the client needs for the tint (the original's syncTile). */
	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Energy", energy);
	}

	/** Real port of the true original's own {@code TilePedestal#findInstabilityMitigator}/
	 * {@code seekSourceRecursive} - see this class's own doc comment for why a plain BFS through connected
	 * real {@link BlockInlay} positions stands in for the original's own persistent charge-gradient search. */
	public static BlockPos findNearbyStabilizer(Level level, BlockPos from) {
		Set<BlockPos> visited = new HashSet<>();
		Deque<BlockPos> frontier = new ArrayDeque<>();
		visited.add(from);
		frontier.add(from);
		int hops = 0;
		while (!frontier.isEmpty() && hops < CAPACITY) {
			int size = frontier.size();
			for (int i = 0; i < size; i++) {
				BlockPos current = frontier.poll();
				for (Direction dir : Direction.Plane.HORIZONTAL) {
					BlockPos next = current.relative(dir);
					if (!visited.add(next)) continue;
					if (level.getBlockEntity(next) instanceof TileStabilizer) return next;
					if (level.getBlockState(next).getBlock() instanceof BlockInlay) frontier.add(next);
				}
			}
			hops++;
		}
		return null;
	}
}
