package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** The true original's own {@code BlocksTC.eldritch} field is real and shipped in this port under the
 * modern name {@link BlocksTC#cultistRitualAltar} (see that field's own doc comment - it also fixed a real
 * bug in the true original, which declared {@code eldritch} but never once assigned it, and had to dodge a
 * separate naming collision with this port's own, correctly-registered {@code pedestalEldritch}). This aid
 * was simply left unregistered, the same "blocker already resolved, just needed wiring" story as
 * {@link AidGlyphedStone}. */
public class AidBasicEldritch implements ITheorycraftAid {
	@Override
	public Object getAidObject() {
		return new ItemStack(BlocksTC.cultistRitualAltar.get());
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] { CardRealization.class, CardRevelation.class, CardTruth.class };
	}
}
