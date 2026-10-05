package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidEnchantmentTable} - direct translation. */
public class AidEnchantmentTable implements ITheorycraftAid {

	@Override
	public Object getAidObject() {
		return net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "research_aids/enchanting_table"));
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] {CardEnchantment.class};
	}
}
