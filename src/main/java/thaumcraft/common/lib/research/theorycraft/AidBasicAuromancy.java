package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/**
 * The true original's own {@code BlocksTC.wandWorkbench} field is this port's own {@link
 * thaumcraft.common.blocks.crafting.BlockFocalManipulator}, already real and shipped under the modern
 * {@link BlocksTC#focalManipulator} name (see that field's own doc comment for the full Focal Manipulator
 * arc) - a genuine naming difference, not a missing block. This aid was left unregistered simply because
 * nobody had connected the two yet, the same real "blocker already resolved, just needed wiring" story as
 * {@link AidGlyphedStone}.
 */
public class AidBasicAuromancy implements ITheorycraftAid {
	@Override
	public Object getAidObject() {
		return new ItemStack(BlocksTC.focalManipulator.get());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardFocus.class, CardAwareness.class, CardSpellbinding.class };
	}
}
