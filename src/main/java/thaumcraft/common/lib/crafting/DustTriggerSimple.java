package thaumcraft.common.lib.crafting;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.common.lib.scheduling.BlockScheduler;

/**
 * Replaces the old (1.12.2) {@code DustTriggerSimple} - the dust-trigger behind converting a plain vanilla
 * block into its Thaumcraft counterpart (crafting table -> Arcane Workbench, cauldron -> Crucible) by
 * right-clicking it with Salis Mundus ({@code ItemMagicDust}). Real port of the original's own real
 * ~2.5-second (50-tick) delayed, animated swap - {@link BlockScheduler#addRunnableServer}/
 * {@link BlockScheduler#addSwapper}, confirmed against that class's own real pre-port source (exact same
 * real delay, {@code fx}/{@code fancy} flags, and {@link BlockScheduler#DEFAULT_PREDICATE}). This class's
 * own doc comment previously called the swap instant a deliberate "drop the animation, keep the function"
 * simplification - a real, felt gameplay difference from the original that went out without a check-in; see
 * the port-tracking memory for why it's being corrected now. Doesn't fire {@code firePlayerCraftingEvent} (a
 * 1.12.2-specific achievement/stat hook with no direct modern equivalent needed here).
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
		if (world.isClientSide) return;
		BlockState state = world.getBlockState(pos);
		BlockScheduler.addRunnableServer(world, () ->
				BlockScheduler.addSwapper(world, pos, state, result, false, 0, player, true, true, -9999, false, false, 0, BlockScheduler.DEFAULT_PREDICATE, 0.0f),
				50);
	}
}
