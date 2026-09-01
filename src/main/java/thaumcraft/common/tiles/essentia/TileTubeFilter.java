package thaumcraft.common.tiles.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeFilter} - a {@link TileTube} locked to one aspect, set by
 * right-clicking it with a filled essentia-container item (a Phial or Crystal Essence) and cleared by
 * sneak+empty-hand-clicking it - the same interaction shape as the Warded Jar Label, applied here to a tube
 * instead of a jar (see {@code BlockTubeFilter}). Rejects any neighbor demanding a conflicting aspect and
 * forces its own declared demand to the filter aspect even when idle.
 */
public class TileTubeFilter extends TileTube {

	private Aspect aspectFilter;

	public TileTubeFilter(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_FILTER.get(), pos, state);
	}

	public Aspect getAspectFilter() {
		return aspectFilter;
	}

	public void setAspectFilter(Aspect aspect) {
		aspectFilter = aspect;
		setChanged();
	}

	@Override
	protected boolean acceptsNeighborSuctionType(Aspect neighborType) {
		return aspectFilter == null || neighborType == null || neighborType == aspectFilter;
	}

	@Override
	protected Aspect forcedSuctionType() {
		return aspectFilter;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (aspectFilter != null) {
			tag.putString("AspectFilter", aspectFilter.getTag());
		}
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspectFilter = tag.contains("AspectFilter") ? Aspect.getAspect(tag.getString("AspectFilter")) : null;
	}
}
