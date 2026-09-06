package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidBeacon} - direct translation. Triggers off a real Beacon's own real
 * drop ({@code TileResearchTable#checkSurroundingAids}'s {@code ItemStack} branch matches whatever the
 * scanned block would drop, not the block itself). */
public class AidBeacon implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return new ItemStack(Blocks.BEACON);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] {CardBeacon.class};
	}
}
