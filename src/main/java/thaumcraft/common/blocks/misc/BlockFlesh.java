package thaumcraft.common.blocks.misc;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Replaces the old (1.12.2) {@code BlockFlesh} - a plain decorative block, mainly useful as the Crucible
 * catalyst for Sanity Soap. Uses vanilla's own {@code SLIME_BLOCK} sound type as a substitute for the
 * original's custom "gore" squelch sound - porting the real sound would mean porting {@code SoundsTC}, a
 * ~70-entry mod-wide sound registry, just for this one block; a vanilla sound close in spirit is a much
 * smaller, well-flagged simplification.
 */
public class BlockFlesh extends Block {

	public BlockFlesh(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
