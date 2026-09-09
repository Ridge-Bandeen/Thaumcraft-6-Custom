package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** {@link BlocksTC#jarBrain} - see {@link thaumcraft.common.blocks.essentia.BlockJarBrain}'s own doc
 * comment for the real "Brain in a Jar" mechanic, built the same session this aid was finally wired in. */
public class AidBrainInAJar implements ITheorycraftAid {
	@Override
	public Object getAidObject() {
		return new ItemStack(BlocksTC.jarBrain.get());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardDarkWhispers.class };
	}
}
