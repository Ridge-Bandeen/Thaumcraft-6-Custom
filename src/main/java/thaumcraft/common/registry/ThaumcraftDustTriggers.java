package thaumcraft.common.registry;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.lib.crafting.DustTriggerMultiblock;
import thaumcraft.common.lib.crafting.DustTriggerSimple;

/**
 * Registers this port's Salis Mundus dust triggers - see {@code DustTriggerSimple}'s own doc comment for the
 * simple 1:1 kind, {@link DustTriggerMultiblock}'s own doc comment for the structure-based kind restored
 * alongside it (see the port-tracking memory for the fuller "go back and build the real subsystem" context).
 * The ore-dictionary-tag variant ({@code DustTriggerOre}, matching e.g. "any modded crafting table") still
 * isn't ported - no real registered trigger in the original ever needed it for anything this port has built.
 *
 * Research gates: the original's Arcane Workbench trigger used {@code FIRSTSTEPS@1}, already in every
 * player's default knowledge (see {@code PlayerKnowledge}), so it's reachable as-is. The Crucible
 * ({@code UNLOCKALCHEMY@1}), Infernal Furnace ({@code INFERNALFURNACE}), Infusion Altar ({@code INFUSION}),
 * and Thaumatorium ({@code THAUMATORIUM}) triggers all used research keys that aren't reachable with no
 * Thaumonomicon to grant them, so all four substitute {@code FIRSTSTEPS} instead, the same "stay reachable
 * given no research-granting path yet" deviation used everywhere else this session.
 */
public final class ThaumcraftDustTriggers {

	public static void register() {
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS@1", Blocks.CRAFTING_TABLE, new ItemStack(BlocksTC.arcaneWorkbench.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS", Blocks.CAULDRON, new ItemStack(BlocksTC.crucible.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("FIRSTSTEPS", infernalFurnaceBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("FIRSTSTEPS", infusionAltarBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("FIRSTSTEPS", thaumatoriumBlueprint()));
	}

	/**
	 * Real port of the true original's own {@code infernalFurnaceBlueprint} (a 3x3x3 nether-brick/obsidian
	 * shell with a lava core) - exact same shape, exact same source/target blocks, just {@code Blocks.LAVA}
	 * standing in for the original's {@code Material.LAVA} source (see {@code DustTriggerMultiblock}'s own
	 * doc comment for why that's a clean, direct real equivalent now). The {@code "AIR"} target (real, same
	 * literal sentinel string the original used) is a real "convert to air" cell - a Java {@code null} target
	 * means something different (a validity-only cell, present but never touched, used by the blueprints this
	 * unlocks for the Infusion Altar's own central pedestal/matrix), a real bug caught while verifying this
	 * exact blueprint (the iron-bars slot silently never converted with a {@code null} target instead).
	 */
	private static Part[][][] infernalFurnaceBlueprint() {
		Part nb = new Part(Blocks.NETHER_BRICKS, new ItemStack(BlocksTC.placeholderNetherbrick.get()));
		Part ob = new Part(Blocks.OBSIDIAN, new ItemStack(BlocksTC.placeholderObsidian.get()));
		Part ib = new Part(Blocks.IRON_BARS, "AIR");
		Part la = new Part((Block) Blocks.LAVA, BlocksTC.infernalFurnace.get(), true);
		return new Part[][][] {
				{ { nb, ob, nb }, { ob, null, ob }, { nb, ob, nb } },
				{ { nb, ob, nb }, { ob, la, ob }, { nb, ib, nb } },
				{ { nb, ob, nb }, { ob, ob, ob }, { nb, ob, nb } },
		};
	}

	/**
	 * Real port of the true original's own {@code infusionAltarNormalBlueprint} (the Arcane-tier Infusion
	 * Altar) - a real {@code infusionMatrix}/real {@code pedestal} the player already placed themselves stay
	 * completely untouched (both real {@code null}-target validity cells - see {@code DustTriggerMultiblock}'s
	 * own doc comment on why that's different from a real {@code "AIR"}-target removal), a ring of
	 * {@code stoneArcane} scaffolding at the 4 corners one layer up vanishes, and the 4 corner
	 * {@code stoneArcane} blocks 2 below the matrix become real {@code pillarArcane} - each with its own real,
	 * fixed absolute facing (a {@link BlockState}-typed target, not a relative one - see that same doc comment)
	 * exactly matching the original's own per-corner facing assignment. The Ancient/Eldritch tiers aren't
	 * ported here - {@code stoneAncient}/{@code stoneEldritchTile} were never ported either (a genuine forced
	 * block, see {@code BlockPillar}'s own doc comment), so there's no real source material to build them from
	 * yet.
	 */
	private static Part[][][] infusionAltarBlueprint() {
		Part im = new Part(BlocksTC.infusionMatrix.get(), null);
		Part snt = new Part(BlocksTC.stoneArcane.get(), "AIR");
		Part snb1 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(net.minecraft.core.Direction.EAST));
		Part snb2 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(net.minecraft.core.Direction.NORTH));
		Part snb3 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(net.minecraft.core.Direction.SOUTH));
		Part snb4 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(net.minecraft.core.Direction.WEST));
		Part pn = new Part(BlocksTC.pedestal.get().defaultBlockState(), null);
		return new Part[][][] {
				{ { null, null, null }, { null, im, null }, { null, null, null } },
				{ { snt, null, snt }, { null, null, null }, { snt, null, snt } },
				{ { snb1, null, snb2 }, { null, pn, null }, { snb3, null, snb4 } },
		};
	}

	private static net.minecraft.world.level.block.state.BlockState pillarFacing(net.minecraft.core.Direction facing) {
		return BlocksTC.pillarArcane.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
	}

	/**
	 * Real port of the true original's own {@code thaumotoriumBlueprint} - a simple 1x1x3 vertical stack: a
	 * real, pre-placed {@code crucible} at the bottom (real {@code null}-target validity cell, untouched -
	 * this port's own {@link thaumcraft.common.tiles.crafting.TileThaumatorium} doesn't actually read from it
	 * at runtime, same already-documented simplification as before this restores just the real structural
	 * placement requirement, not the original's own crucible-sourced essentia mechanic), a real
	 * {@code metalAlchemical} block above it becomes the real {@code thaumatorium}, and another real
	 * {@code metalAlchemical} above that becomes the real cosmetic {@code thaumatoriumTop}. The original's own
	 * {@code applyPlayerFacing} flag on both targets is a real no-op here - neither block has a real
	 * {@code HORIZONTAL_FACING} property to begin with (both full, faceless cubes - see
	 * {@code BlockThaumatorium}'s own doc comment), so {@link DustTriggerMultiblock}'s facing computation
	 * never triggers for either.
	 */
	private static Part[][][] thaumatoriumBlueprint() {
		Part top = new Part(BlocksTC.metalAlchemical.get(), BlocksTC.thaumatoriumTop.get()).setApplyPlayerFacing(true);
		Part body = new Part(BlocksTC.metalAlchemical.get(), BlocksTC.thaumatorium.get()).setApplyPlayerFacing(true);
		Part base = new Part(BlocksTC.crucible.get(), null);
		return new Part[][][] {
				{ { top } },
				{ { body } },
				{ { base } },
		};
	}

	private ThaumcraftDustTriggers() {
	}
}
