package thaumcraft.common.blocks.misc;

import net.minecraft.world.level.block.AirBlock;

/**
 * The original's {@code effectGlimmer}: an invisible, air-like block shining at full light, left behind by the Arcane
 * Lamp and by tools with the Lamplight infusion. It counts as air, so anything can be placed into it.
 */
public class BlockEffectGlimmer extends AirBlock {

	public BlockEffectGlimmer(Properties properties) {
		super(properties);
	}
}
