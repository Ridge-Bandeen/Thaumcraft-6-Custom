package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code TileJarFillableVoid}. A jar that never stops demanding essentia, even
 * once "full" - past its 250-unit capacity it silently discards the overflow instead (with a small 1-in-250
 * chance per overfill to vent a point of flux into the local aura, same as the original) - a real overflow
 * valve/sink for an essentia network with excess production, rather than a normal jar's behavior of
 * stopping suction once full.
 *
 * A small, purely additive extension of {@link TileJarFillable} via composition, not field access - three
 * overrides ({@link #isFull} always false so {@link #tick} never skips it; {@link #addToContainer}
 * delegates to the real capacity-respecting superclass method and just discards whatever it reports as
 * leftover instead of returning it to the caller; {@link #getSuctionAmount}/{@link #getMinimumSuction} drop
 * the "0 once full" case). Everything else (label locking, the two fill paths, NBT persistence) is inherited
 * unchanged.
 */
public class TileJarFillableVoid extends TileJarFillable {

	public TileJarFillableVoid(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.JAR_VOID.get(), pos, state);
	}

	@Override
	protected boolean isFull() {
		return false;
	}

	@Override
	public int addToContainer(Aspect tag, int toAdd) {
		if (toAdd <= 0) return 0;
		// A real rejection (already holding a different aspect) has to stay rejected - only overflow past
		// capacity gets silently discarded below. super.addToContainer's own leftover can't tell these two
		// apart on its own (both return the full toAdd when the jar's already at capacity), so the mismatch
		// case is ruled out here first using the same public accessors any other caller would use.
		Aspect current = getEssentiaType(Direction.UP);
		if (current != null && tag != current) return toAdd;

		int leftover = super.addToContainer(tag, toAdd);
		if (leftover > 0) {
			if (getLevel() != null && getLevel().getRandom().nextInt(250) == 0) {
				AuraHandler.addFlux(getLevel(), getBlockPos(), 1.0f);
			}
			leftover = 0;
		}
		return leftover;
	}

	@Override
	public int getSuctionAmount(Direction face) {
		return getAspectFilter() != null ? 48 : 32;
	}

	@Override
	public int getMinimumSuction() {
		return getAspectFilter() != null ? 48 : 32;
	}
}
