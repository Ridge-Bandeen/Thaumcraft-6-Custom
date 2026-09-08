package thaumcraft.common.lib.research.theorycraft;

import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidBasicArtifice} - direct translation. Triggers off a real placed
 * Arcane Workbench. */
public class AidBasicArtifice implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return BlocksTC.arcaneWorkbench.get();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardCalibrate.class, CardTinker.class, CardMindOverMatter.class };
	}
}
