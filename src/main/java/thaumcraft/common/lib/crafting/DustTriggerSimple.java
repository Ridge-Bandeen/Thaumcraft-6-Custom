package thaumcraft.common.lib.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IDustTrigger;

/**
 * Replaces the old (1.12.2) {@code DustTriggerSimple} - the dust-trigger behind converting a plain vanilla
 * block into its Thaumcraft counterpart (crafting table -> Arcane Workbench, cauldron -> Crucible) by
 * right-clicking it with Salis Mundus ({@code ItemMagicDust}). Simplified from the original: the block swap
 * is instant, not the original's ~2.5-second delayed animated swap ({@code ServerEvents.addSwapper}, a
 * scheduler/particle-animation subsystem not ported) - "drop the animation, keep the function", the same
 * precedent used throughout this session. Doesn't fire {@code firePlayerCraftingEvent} (a 1.12.2-specific
 * achievement/stat hook with no direct modern equivalent needed here).
 */
public class DustTriggerSimple implements IDustTrigger {

	private final Block target;
	private final ItemStack result;
	private final String research;

	public DustTriggerSimple(String research, Block target, ItemStack result) {
		this.target = target;
		this.result = result;
		this.research = research;
	}

	@Override
	public Placement getValidFace(Level world, Player player, BlockPos pos, Direction face) {
		if (world.getBlockState(pos).getBlock() != target) return null;
		if (research != null && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research)) return null;
		return new Placement(0, 0, 0, null);
	}

	@Override
	public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
		if (!world.isClientSide && result.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
			world.setBlockAndUpdate(pos, blockItem.getBlock().defaultBlockState());
		}
	}
}
