package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidGlyphedStone} - direct translation. Real, buildable now:
 * {@code BlocksTC.stoneAncientGlyphed} (part of the Ancient stone family shipped earlier this session)
 * closed the one real gap that kept this aid out of {@link thaumcraft.Thaumcraft#commonSetup}'s own
 * registration list - see that method's own doc comment, written before that block existed. */
public class AidGlyphedStone implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return new ItemStack(BlocksTC.stoneAncientGlyphed.get());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { thaumcraft.common.lib.research.theorycraft.CardGlyphs.class };
	}
}
