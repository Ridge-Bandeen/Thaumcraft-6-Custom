package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.registry.ThaumcraftSounds;

import javax.annotation.Nullable;

/**
 * Replaces the old (1.12.2) {@code TileTubeValve} - a {@link TileTube} that redstone-gates its own flow: an
 * incoming redstone signal (indirect, same real {@code isBlockIndirectlyGettingPowered} check the original
 * used) shuts it, losing the signal reopens it, each transition playing the real custom {@code squeek} cue
 * restored 2026-09-15 (was a vanilla trapdoor open/close stand-in) - see {@link ThaumcraftSounds}'s own doc
 * comment; the true original uses the same {@code squeek} sound for both transitions, at 0.7f volume.
 *
 * <b>Real, disclosed simplification, not a new one</b> - same "no per-face open/close toggling" gap
 * {@link TileTube}'s own doc comment already flagged for the whole tube family (blocked on the never-ported
 * sub-hit precise-face raytracing, {@code IndexedCuboid6}/{@code RayTracer}): the original's own
 * {@code onCasterRightClick} let a wand toggle individual faces open/closed on this specific tube and cycle
 * its own "facing" for a one-way mode. Neither is built here, consistent with every other tube in this port -
 * this restores the real, independent, non-per-face-dependent part of the device (the redstone-gated relay
 * itself), not a new deviation from the tube family's own established shape.
 *
 * Real spinning-valve-ring animation restored 2026-09-16 - see {@link #rotation} and {@link
 * thaumcraft.client.render.TileTubeValveRenderer}'s own doc comment. {@code TileTube}'s own real
 * block-entity sync wiring (added in the same chunk) is what makes {@link #isAllowingFlow()} - the state this
 * animation is gated on - actually visible to the client in the first place.
 */
public class TileTubeValve extends TileTube {

	private boolean allowFlow = true;
	private boolean wasPoweredLastTick;
	private int count;

	/** Real client-only valve-ring rotation angle (degrees), matching the true original's own real field
	 * exactly - deliberately plain (not synced): the true original's own version was purely client-computed
	 * too, off the real already-synced {@link #allowFlow} state alone. */
	public float rotation;

	public TileTubeValve(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_VALVE.get(), pos, state);
	}

	public boolean isAllowingFlow() {
		return allowFlow;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileTubeValve tile) {
		if (level.isClientSide) {
			if (!tile.allowFlow && tile.rotation < 360.0f) {
				tile.rotation += 20.0f;
			} else if (tile.allowFlow && tile.rotation > 0.0f) {
				tile.rotation -= 20.0f;
			}
		}
		if (!level.isClientSide) {
			tile.count++;
			if (tile.count % 5 == 0) {
				boolean poweredNow = level.hasNeighborSignal(pos);
				if (tile.wasPoweredLastTick && !poweredNow && !tile.allowFlow) {
					tile.allowFlow = true;
					level.playSound(null, pos, ThaumcraftSounds.SQUEEK.get(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
					tile.setChanged();
				}
				if (!tile.wasPoweredLastTick && poweredNow && tile.allowFlow) {
					tile.allowFlow = false;
					level.playSound(null, pos, ThaumcraftSounds.SQUEEK.get(), SoundSource.BLOCKS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
					tile.setChanged();
				}
				tile.wasPoweredLastTick = poweredNow;
			}
		}
		TileTube.tick(level, pos, state, tile);
	}

	/** Real gate on the original's own real mechanic: a shut valve reports/accepts no demand at all, so
	 * nothing pulls essentia through it while closed. {@link #calculateSuction} already resets
	 * {@code suction}/{@code suctionType} to zero/null before this hook ever runs, so rejecting every
	 * neighbor here (the same hook {@link TileTubeFilter} uses for its own aspect check) is enough - no
	 * neighbor's demand is ever picked up while shut, and {@link #equalizeWithNeighbours} rejects the same
	 * way besides. */
	@Override
	protected boolean acceptsNeighborSuctionType(@Nullable Aspect neighborType) {
		return allowFlow;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putBoolean("AllowFlow", allowFlow);
		tag.putBoolean("WasPowered", wasPoweredLastTick);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		allowFlow = !tag.contains("AllowFlow") || tag.getBoolean("AllowFlow");
		wasPoweredLastTick = tag.getBoolean("WasPowered");
	}
}
