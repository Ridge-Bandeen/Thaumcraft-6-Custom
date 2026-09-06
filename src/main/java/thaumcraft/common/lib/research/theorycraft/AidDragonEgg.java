package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidDragonEgg} - direct translation. */
public class AidDragonEgg implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return new ItemStack(Blocks.DRAGON_EGG);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] {CardDragonEgg.class};
	}
}
