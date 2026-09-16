package thaumcraft.common.blocks.misc;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Replaces the old (1.12.2) {@code BlockFlesh} - a plain decorative block, mainly useful as the Crucible
 * catalyst for Sanity Soap. Uses the real custom {@code GORE} sound type as of 2026-09-15 (see {@link
 * thaumcraft.common.registry.ThaumcraftSounds}'s own doc comment), matching the true original's own
 * {@code SoundsTC.GORE} exactly.
 */
public class BlockFlesh extends Block {

	public BlockFlesh(BlockBehaviour.Properties properties) {
		super(properties);
	}
}
