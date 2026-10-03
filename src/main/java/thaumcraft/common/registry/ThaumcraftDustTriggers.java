package thaumcraft.common.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * Research gates are the original's own (restored 2026-09-28; they had all been swapped for {@code FIRSTSTEPS} while no
 * research could be earned): the dream ({@code !gotdream}) for bookshelf -> Thaumonomicon, {@code FIRSTSTEPS@1} for
 * the Arcane Workbench, {@code UNLOCKALCHEMY@1} for the Crucible, and each multiblock's own research. The original's
 * ore-dictionary variants ({@code DustTriggerOre}: any "bookshelf", any "workbench") are block tags here.
 */
public final class ThaumcraftDustTriggers {

	/** One multiblock as the Thaumonomicon shows it - the original's {@code ThaumcraftApi.BluePrint} catalog entries: the
	 * research it needs, the structure, the item shown on its bookmark ({@code null}: a small render of the finished
	 * structure) and the materials listed under the picture. */
	public record Blueprint(String research, Part[][][] parts, ItemStack display, List<ItemStack> ingredients) {
	}

	/** The original's multiblock catalog, by its (lower-cased) research recipe key. */
	public static final Map<String, Blueprint> BLUEPRINTS = new LinkedHashMap<>();

	public static void register() {
		BLUEPRINTS.clear();
		BLUEPRINTS.put("thaumcraft:infernalfurnace", new Blueprint("INFERNALFURNACE", infernalFurnaceBlueprint(), null,
				List.of(new ItemStack(Blocks.NETHER_BRICKS, 12), new ItemStack(Blocks.OBSIDIAN, 12), new ItemStack(Blocks.IRON_BARS), new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET))));
		BLUEPRINTS.put("thaumcraft:infusionaltar", new Blueprint("INFUSION", infusionAltarBlueprint(), null,
				List.of(new ItemStack(BlocksTC.stoneArcane.get(), 8), new ItemStack(BlocksTC.pedestal.get()), new ItemStack(BlocksTC.infusionMatrix.get()))));
		BLUEPRINTS.put("thaumcraft:infusionaltarancient", new Blueprint("INFUSIONANCIENT", infusionAltarAncientBlueprint(), null,
				List.of(new ItemStack(BlocksTC.stoneAncient.get(), 8), new ItemStack(BlocksTC.pedestalAncient.get()), new ItemStack(BlocksTC.infusionMatrix.get()))));
		BLUEPRINTS.put("thaumcraft:infusionaltareldritch", new Blueprint("INFUSIONELDRITCH", infusionAltarEldritchBlueprint(), null,
				List.of(new ItemStack(BlocksTC.stoneEldritchTile.get(), 8), new ItemStack(BlocksTC.pedestalEldritch.get()), new ItemStack(BlocksTC.infusionMatrix.get()))));
		BLUEPRINTS.put("thaumcraft:thaumatorium", new Blueprint("THAUMATORIUM", thaumatoriumBlueprint(), null,
				List.of(new ItemStack(BlocksTC.metalAlchemical.get(), 2), new ItemStack(BlocksTC.crucible.get()))));
		BLUEPRINTS.put("thaumcraft:golempress", new Blueprint("MINDCLOCKWORK", golemPressBlueprint(), new ItemStack(BlocksTC.golemBuilder.get()),
				List.of(new ItemStack(Blocks.IRON_BARS), new ItemStack(Blocks.CAULDRON), new ItemStack(Blocks.PISTON), new ItemStack(Blocks.ANVIL), new ItemStack(BlocksTC.tableStone.get()))));

		IDustTrigger.registerDustTrigger(new DustTriggerSimple("!gotdream", Blocks.BOOKSHELF, new ItemStack(thaumcraft.api.items.ItemsTC.thaumonomicon.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("!gotdream", net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "bookshelves")),
				new ItemStack(thaumcraft.api.items.ItemsTC.thaumonomicon.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS@1", Blocks.CRAFTING_TABLE, new ItemStack(BlocksTC.arcaneWorkbench.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("FIRSTSTEPS@1", net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "player_workstations/crafting_tables")),
				new ItemStack(BlocksTC.arcaneWorkbench.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerSimple("UNLOCKALCHEMY@1", net.minecraft.tags.BlockTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "crucible_bases")),
				new ItemStack(BlocksTC.crucible.get())));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("INFERNALFURNACE", infernalFurnaceBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("INFUSION", infusionAltarBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("INFUSIONANCIENT", infusionAltarAncientBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("INFUSIONELDRITCH", infusionAltarEldritchBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("THAUMATORIUM", thaumatoriumBlueprint()));
		IDustTrigger.registerDustTrigger(new DustTriggerMultiblock("MINDCLOCKWORK", golemPressBlueprint()));
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
	 * exactly matching the original's own per-corner facing assignment.
	 */
	private static Part[][][] infusionAltarBlueprint() {
		Part im = new Part(BlocksTC.infusionMatrix.get(), null);
		Part snt = new Part(BlocksTC.stoneArcane.get(), "AIR");
		Part snb1 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(BlocksTC.pillarArcane.get(), net.minecraft.core.Direction.EAST));
		Part snb2 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(BlocksTC.pillarArcane.get(), net.minecraft.core.Direction.NORTH));
		Part snb3 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(BlocksTC.pillarArcane.get(), net.minecraft.core.Direction.SOUTH));
		Part snb4 = new Part(BlocksTC.stoneArcane.get(), pillarFacing(BlocksTC.pillarArcane.get(), net.minecraft.core.Direction.WEST));
		Part pn = new Part(BlocksTC.pedestal.get().defaultBlockState(), null);
		return new Part[][][] {
				{ { null, null, null }, { null, im, null }, { null, null, null } },
				{ { snt, null, snt }, { null, null, null }, { snt, null, snt } },
				{ { snb1, null, snb2 }, { null, pn, null }, { snb3, null, snb4 } },
		};
	}

	/**
	 * Real port of the true original's own {@code infusionAltarAncientBlueprint} - same real shape as
	 * {@link #infusionAltarBlueprint} above, just the Ancient tier's own real materials
	 * ({@code stoneAncient}/{@code pillarAncient}/{@code pedestalAncient}). <b>Genuinely unreachable through
	 * normal survival progression, matching the true original exactly</b> - see {@code BlocksTC.stoneAncient}'s
	 * own doc comment for why this is real, correct, functioning code rather than a porting gap: the true
	 * original itself never had a real acquisition path for {@code stoneAncient} either. Ported anyway for
	 * real fidelity, same as {@code matrixSpeed}/the Causality Collapser earlier this session shipped real
	 * code before their own blockers were found and fixed - this one's blocker (a whole abandoned/incomplete
	 * upstream content gap, not a portable subsystem) has no real fix to chase.
	 */
	private static Part[][][] infusionAltarAncientBlueprint() {
		Part sat = new Part(BlocksTC.stoneAncient.get(), "AIR");
		Part sab1 = new Part(BlocksTC.stoneAncient.get(), pillarFacing(BlocksTC.pillarAncient.get(), net.minecraft.core.Direction.EAST));
		Part sab2 = new Part(BlocksTC.stoneAncient.get(), pillarFacing(BlocksTC.pillarAncient.get(), net.minecraft.core.Direction.NORTH));
		Part sab3 = new Part(BlocksTC.stoneAncient.get(), pillarFacing(BlocksTC.pillarAncient.get(), net.minecraft.core.Direction.SOUTH));
		Part sab4 = new Part(BlocksTC.stoneAncient.get(), pillarFacing(BlocksTC.pillarAncient.get(), net.minecraft.core.Direction.WEST));
		Part im = new Part(BlocksTC.infusionMatrix.get(), null);
		Part pa = new Part(BlocksTC.pedestalAncient.get().defaultBlockState(), null);
		return new Part[][][] {
				{ { null, null, null }, { null, im, null }, { null, null, null } },
				{ { sat, null, sat }, { null, null, null }, { sat, null, sat } },
				{ { sab1, null, sab2 }, { null, pa, null }, { sab3, null, sab4 } },
		};
	}

	/**
	 * Real port of the true original's own {@code infusionAltarEldritchBlueprint} - see
	 * {@link #infusionAltarAncientBlueprint}'s own doc comment for why this tier is real but genuinely
	 * unreachable too, matching the true original exactly.
	 */
	private static Part[][][] infusionAltarEldritchBlueprint() {
		Part set = new Part(BlocksTC.stoneEldritchTile.get(), "AIR");
		Part seb1 = new Part(BlocksTC.stoneEldritchTile.get(), pillarFacing(BlocksTC.pillarEldritch.get(), net.minecraft.core.Direction.EAST));
		Part seb2 = new Part(BlocksTC.stoneEldritchTile.get(), pillarFacing(BlocksTC.pillarEldritch.get(), net.minecraft.core.Direction.NORTH));
		Part seb3 = new Part(BlocksTC.stoneEldritchTile.get(), pillarFacing(BlocksTC.pillarEldritch.get(), net.minecraft.core.Direction.SOUTH));
		Part seb4 = new Part(BlocksTC.stoneEldritchTile.get(), pillarFacing(BlocksTC.pillarEldritch.get(), net.minecraft.core.Direction.WEST));
		Part im = new Part(BlocksTC.infusionMatrix.get(), null);
		Part pe = new Part(BlocksTC.pedestalEldritch.get().defaultBlockState(), null);
		return new Part[][][] {
				{ { null, null, null }, { null, im, null }, { null, null, null } },
				{ { set, null, set }, { null, null, null }, { set, null, set } },
				{ { seb1, null, seb2 }, { null, pe, null }, { seb3, null, seb4 } },
		};
	}

	private static net.minecraft.world.level.block.state.BlockState pillarFacing(Block pillarBlock, net.minecraft.core.Direction facing) {
		return pillarBlock.defaultBlockState()
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

	/**
	 * Real port of the true original's own {@code golempressBlueprint} - {@link thaumcraft.common.blocks.crafting.BlockGolemBuilder}'s
	 * real acquisition ritual, missing entirely until now despite the block itself (and its menu/tile) being
	 * real and working since an earlier chunk - that class's own doc comment describing a "ships as one real,
	 * solid, directly-interactable block" simplification was written before this port's real
	 * {@code DustTriggerMultiblock} subsystem existed, and (unlike {@link #infernalFurnaceBlueprint} and
	 * {@link #thaumatoriumBlueprint}, both already migrated to their own real rituals) was never revisited -
	 * this chunk is that migration, closing the block's total lack of any acquisition path at all.
	 *
	 * Same real compact 2x2x2 shape as the original: a bottom-layer iron-bars corner, and a top layer of
	 * cauldron/anvil/stone-table corners around a piston (facing up) that becomes the real
	 * {@code golemBuilder} block itself - a {@link Block}-typed target, same as {@link #infernalFurnaceBlueprint}'s
	 * own lava→furnace conversion, not an {@link net.minecraft.world.level.block.state.BlockState}-typed one,
	 * since (unlike the Infusion Altar's corner pillars) this device has no facing property to fix in place.
	 * The other 4 corners convert to real, purely decorative {@link BlocksTC#placeholderBars}/
	 * {@link BlocksTC#placeholderCauldron}/{@link BlocksTC#placeholderAnvil}/{@link BlocksTC#placeholderTable} -
	 * same real "visually near-identical, different block id so a finished structure's own shell no longer
	 * matches the blueprint and can't be accidentally re-converted" pattern {@link BlocksTC#placeholderNetherbrick}/
	 * {@link BlocksTC#placeholderObsidian} already established, not the true original's own much heavier
	 * {@code BlockPlaceholder} (GUI right-click forwarding + break-triggers-destroy) - this port's
	 * {@code golemBuilder} is already a real, directly-interactable block in its own right, so none of that
	 * forwarding machinery is needed.
	 */
	private static Part[][][] golemPressBlueprint() {
		Part bars = new Part(Blocks.IRON_BARS, new ItemStack(BlocksTC.placeholderBars.get()));
		Part cauldron = new Part(Blocks.CAULDRON, new ItemStack(BlocksTC.placeholderCauldron.get()));
		Part piston = new Part(
				Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP),
				BlocksTC.golemBuilder.get());
		Part anvil = new Part(Blocks.ANVIL, new ItemStack(BlocksTC.placeholderAnvil.get()));
		Part table = new Part(BlocksTC.tableStone.get(), new ItemStack(BlocksTC.placeholderTable.get()));
		return new Part[][][] {
				{ { null, null }, { bars, null } },
				{ { cauldron, anvil }, { piston, table } },
		};
	}

	private ThaumcraftDustTriggers() {
	}
}
