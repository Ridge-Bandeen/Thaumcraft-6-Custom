package thaumcraft.api.research;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * Replaces the old (1.12.2) {@code ScanBlock}. Also registers a companion {@link ScanItem} for the block's
 * own item form, same as the original - scanning either the placed block or its inventory item grants the
 * same research.
 */
public class ScanBlock implements IScanThing {

	private final String research;
	private final Block[] blocks;

	public ScanBlock(Block block) {
		this(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block) != null
				? "!" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block)
				: "!" + block, block);
	}

	public ScanBlock(String research, Block... blocks) {
		this.research = research;
		this.blocks = blocks;
		for (Block block : blocks) {
			ScanningManager.addScannableThing(new ScanItem(research, new ItemStack(block)));
		}
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (obj instanceof BlockPos pos) {
			for (Block block : blocks) {
				if (player.level().getBlockState(pos).is(block)) return true;
			}
		}
		return false;
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
