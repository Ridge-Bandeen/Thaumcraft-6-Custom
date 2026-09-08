package thaumcraft.common.lib.research.theorycraft;

import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidBasicGolemancy} - direct translation. Triggers off a real placed
 * Golem Builder. */
public class AidBasicGolemancy implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return BlocksTC.golemBuilder.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardSculpting.class, CardScripting.class, CardSynergy.class };
	}
}
