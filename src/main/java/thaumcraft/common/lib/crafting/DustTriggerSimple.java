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
	/** The original's {@code DustTriggerOre}: any block in this tag instead of one block. */
	private final net.minecraft.tags.TagKey<Block> targetTag;
	private final ItemStack result;
	private final String research;

	public DustTriggerSimple(String research, Block target, ItemStack result) {
		this.target = target;
		this.targetTag = null;
		this.result = result;
		this.research = research;
	}

	public DustTriggerSimple(String research, net.minecraft.tags.TagKey<Block> targetTag, ItemStack result) {
		this.target = null;
		this.targetTag = targetTag;
		this.result = result;
		this.research = research;
	}

	@Override
	public Placement getValidFace(Level world, Player player, BlockPos pos, Direction face) {
		BlockState here = world.getBlockState(pos);
		if (target != null ? here.getBlock() != target : !here.is(targetTag)) return null;
		if (research != null && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research)) return null;
		if (refuseFull(world, player, pos)) return null;
		return new Placement(0, 0, 0, null);
	}

	/** Whether the block at {@code pos} holds items or fluid (through the usual item/fluid capabilities) - Salis Mundus
	 * won't convert a block with contents, so nothing in it is lost. Tells the player why. Nothing Thaumcraft
	 * converts by default holds anything; this guards modded containers tagged in as conversion targets. */
	public static boolean refuseFull(Level world, Player player, BlockPos pos) {
		if (!holdsContents(world, pos)) return false;
		if (!world.isClientSide && player != null) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable("tc.dust.notempty"), true);
		}
		return true;
	}

	private static boolean holdsContents(Level world, BlockPos pos) {
		var items = world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, null);
		if (items != null) {
			for (int i = 0; i < items.getSlots(); i++) {
				if (!items.getStackInSlot(i).isEmpty()) return true;
			}
		}
		var fluids = world.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, null);
		if (fluids != null) {
			for (int i = 0; i < fluids.getTanks(); i++) {
				if (!fluids.getFluidInTank(i).isEmpty()) return true;
			}
		}
		return false;
	}

	@Override
	public void execute(Level world, Player player, BlockPos pos, Placement placement, Direction side) {
		if (world.isClientSide) return;
		// As the original: the conversion counts as crafting the result - which is what completes research stages that
		// ask you to craft it (First Steps' Arcane Workbench). Missing until 2026-09-29, so that stage never advanced.
		if (player != null) {
			net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(player, result.copy(), new net.minecraft.world.SimpleContainer(1));
		}
		BlockState state = world.getBlockState(pos);
		BlockScheduler.addRunnableServer(world, () ->
				BlockScheduler.addSwapper(world, pos, state, result, false, 0, player, true, true, -9999, false, false, 0, BlockScheduler.DEFAULT_PREDICATE, 0.0f),
				50);
	}
}
