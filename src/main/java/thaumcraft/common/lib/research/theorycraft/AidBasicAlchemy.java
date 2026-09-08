package thaumcraft.common.lib.research.theorycraft;

import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidBasicAlchemy} - direct translation. Triggers off a real placed
 * Crucible. */
public class AidBasicAlchemy implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return BlocksTC.crucible.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardConcentrate.class, CardReactions.class, CardSynthesis.class };
	}
}
