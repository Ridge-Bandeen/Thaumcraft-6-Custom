package thaumcraft.common.registry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.common.lib.crafting.DustTriggerSimple;

/**
 * Registers this port's Salis Mundus dust triggers - see {@code DustTriggerSimple}'s own doc comment.
 * Both convert a plain vanilla block into a real Thaumcraft device, closing loops flagged much earlier this
 * session: the Arcane Workbench was originally built as a directly-registered block "with the dust-
 * conversion + research-gate explicitly flagged as a TODO to restore once research exists" - it does now.
 *
 * Only the two simple 1:1 block-swap triggers from the original are ported (crafting table -> Arcane
 * Workbench, cauldron -> Crucible); the ore-dictionary-tag variant ({@code DustTriggerOre}, matching e.g.
 * "any modded crafting table") and the multiblock-structure variant ({@code DustTriggerMultiblock}, behind
 * Infernal Furnace/Infusion Altar/etc. - see the port-tracking memory notes on why those are still deferred)
 * aren't ported.
 *
 * Research gates: the original's Arcane Workbench trigger used {@code FIRSTSTEPS@1}, already in every
 * player's default knowledge (see {@code PlayerKnowledge}), so it's reachable as-is. The original's
 * Crucible trigger used {@code UNLOCKALCHEMY@1} - not reachable with no Thaumonomicon to grant it, so this
 * substitutes {@code FIRSTSTEPS} instead, the same "stay reachable given no research-granting path yet"
 * deviation used everywhere else this session.
 */
public final class ThaumcraftDustTriggers {

	public static void register() {
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS@1", Blocks.CRAFTING_TABLE, new ItemStack(BlocksTC.arcaneWorkbench.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS", Blocks.CAULDRON, new ItemStack(BlocksTC.crucible.get())));
	}

	private ThaumcraftDustTriggers() {
	}
}
