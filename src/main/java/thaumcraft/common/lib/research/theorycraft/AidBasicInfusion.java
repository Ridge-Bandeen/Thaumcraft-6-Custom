package thaumcraft.common.lib.research.theorycraft;

import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidBasicInfusion} - direct translation. Triggers off a real placed
 * Infusion Matrix. */
public class AidBasicInfusion implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return BlocksTC.infusionMatrix.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardMeasure.class, CardChannel.class, CardInfuse.class };
	}
}
