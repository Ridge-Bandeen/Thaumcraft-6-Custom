package thaumcraft.common.lib.crafting;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.IDustTrigger;
import thaumcraft.api.crafting.Part;
import thaumcraft.common.lib.events.ToolEvents;
import thaumcraft.common.lib.scheduling.BlockScheduler;

/**
 * Replaces the old (1.12.2) {@code DustTriggerMultiblock} - the wand-conversion ritual behind the Infernal
 * Furnace, the Infusion Altar's pillared corners, and the Thaumatorium: build a real 3x3x3 (or smaller)
 * arrangement of plain vanilla-ish blocks matching a {@link Part} blueprint, then right-click any part of it
 * with Salis Mundus to convert the whole thing at once. Restored while going back to build the real multiblock
 * subsystem properly (see the port-tracking memory) instead of continuing the "pick the right vanilla-shaped
 * block directly" simplification several devices adopted while this was still unported.
 *
 * Real port of the true original's own {@code getValidFace}/{@code fitMultiblock}/{@code execute}, translated
 * onto this port's own already-real equivalents rather than anything reinvented:
 * <ul>
 * <li>{@link Matrix#Rotate90DegRight} is untouched (no old-API dependencies at all, ported unchanged).</li>
 * <li>Each matched cell's delayed conversion goes through {@link BlockScheduler#addRunnableServer}/
 * {@link BlockScheduler#addSwapper} - the same real scheduler {@link DustTriggerSimple} already established -
 * using the new explicit-{@link BlockState} overload (see that method's own doc comment) so a target with a
 * real {@code HORIZONTAL_FACING} property (pillars, the pedestal, etc.) places with the real, geometrically
 * correct rotation instead of losing it to a bare {@code defaultBlockState()}.</li>
 * <li>{@link ToolEvents#addBlockedBlock}/{@link ToolEvents#clearBlockedBlock} (restored alongside this class)
 * protect each cell from being harvested out from under its own pending delayed swap, same real mechanic as
 * the original.</li>
 * <li>A {@code Material}-typed source (the original's only real use: the Infernal Furnace's lava core) has no
 * direct modern equivalent - lava is a real, ordinary {@link Block} now ({@code Blocks.LAVA}), so blueprints
 * needing it just use that directly; no separate {@code Material} matching branch is needed.</li>
 * <li>An {@code ItemStack}-typed {@code Part} source (matching by item+damage rather than block) has no real
 * registered trigger using it anywhere in the original's own shipped blueprints - not ported, since there's
 * nothing real to verify it against.</li>
 * <li>The original's own metadata-encoded {@code ItemStack} targets (e.g. the Infusion Altar's 4 corner
 * pillars, each {@code new ItemStack(pillarArcane, 1, BlockPillar.calcMeta(EnumFacing.EAST))}, baking in a
 * real fixed, absolute facing per corner) have no modern equivalent - this port's {@code ItemStack} no longer
 * carries block-state metadata. A {@code BlockState}-typed {@code Part} target is the real modern replacement
 * for exactly that case: used verbatim, with no placement-relative facing computation, since the whole point
 * is a fixed absolute orientation regardless of which of the 4 rotations the structure matched.</li>
 * <li>A {@code null} {@code Part} target is a real validity-only cell (present, checked, never converted -
 * the Infusion Altar's own central pedestal/matrix cells) - not the same thing as the original's own literal
 * {@code "AIR"} target sentinel (a real "convert to air" cell), which this class still honors as a distinct,
 * genuinely scheduled conversion. Conflating the two was a real bug caught while verifying the Infernal
 * Furnace's own iron-bars-to-air cell (see {@code ThaumcraftDustTriggers}'s own doc comment).</li>
 * </ul>
 */
public class DustTriggerMultiblock implements IDustTrigger {

	private final Part[][][] blueprint;
	private final String research;
	private final int ySize;
	private final int xSize;
	private final int zSize;

	public DustTriggerMultiblock(String research, Part[][][] blueprint) {
		this.blueprint = blueprint;
		this.research = research;
		this.ySize = blueprint.length;
		this.xSize = blueprint[0].length;
		this.zSize = blueprint[0][0].length;
	}

	@Override
	public Placement getValidFace(Level level, Player player, BlockPos pos, Direction face) {
		if (research != null && !research.isEmpty() && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(research)) {
			return null;
		}
		for (int yy = -ySize; yy <= 0; yy++) {
			for (int xx = -xSize; xx <= 0; xx++) {
				for (int zz = -zSize; zz <= 0; zz++) {
					BlockPos p2 = pos.offset(xx, yy, zz);
					Direction fit = fitMultiblock(level, p2);
					if (fit != null) {
						Placement placement = new Placement(xx, yy, zz, fit);
						// as for a single block: nothing that would be replaced may be holding items or fluid
						for (BlockPos converted : convertedCells(pos, placement)) {
							if (DustTriggerSimple.refuseFull(level, player, converted)) return null;
						}
						return placement;
					}
				}
			}
		}
		return null;
	}

	/** Tries all 4 horizontal rotations at {@code anchor} (the blueprint's own bottom-northwest corner before
	 * rotation) - returns the real facing that fits, or {@code null} if none does. */
	private Direction fitMultiblock(Level level, BlockPos anchor) {
		rotation:
		for (Direction rotation : Direction.Plane.HORIZONTAL) {
			for (int y = 0; y < ySize; y++) {
				Matrix matrix = new Matrix(blueprint[y]);
				matrix.Rotate90DegRight(3 - rotation.get2DDataValue());
				Part[][] cells = matrix.getMatrix();
				for (int x = 0; x < cells.length; x++) {
					for (int z = 0; z < cells[x].length; z++) {
						Part cell = cells[x][z];
						if (cell == null) continue;
						BlockState real = level.getBlockState(anchor.offset(x, -y + (ySize - 1), z));
						if (!sourceMatches(cell.getSource(), real)) {
							continue rotation;
						}
					}
				}
			}
			return rotation;
		}
		return null;
	}

	/** The positions this placement would replace (cells with a target; check-only cells are left alone). */
	private List<BlockPos> convertedCells(BlockPos pos, Placement placement) {
		BlockPos anchor = pos.offset(placement.xOffset, placement.yOffset, placement.zOffset);
		List<BlockPos> list = new ArrayList<>();
		for (int y = 0; y < ySize; y++) {
			Matrix matrix = new Matrix(blueprint[y]);
			matrix.Rotate90DegRight(3 - placement.facing.get2DDataValue());
			Part[][] cells = matrix.getMatrix();
			for (int x = 0; x < cells.length; x++) {
				for (int z = 0; z < cells[x].length; z++) {
					Part cell = cells[x][z];
					if (cell != null && cell.getTarget() != null) list.add(anchor.offset(x, -y + (ySize - 1), z));
				}
			}
		}
		return list;
	}

	private static boolean sourceMatches(Object source, BlockState real) {
		if (source instanceof Block block) return real.is(block);
		if (source instanceof BlockState state) return real.equals(state);
		return false;
	}

	/** Same real "does at least one neighbor let light/particles through" check
	 * {@link BlockScheduler}'s own private {@code isExposed} already uses - just the cosmetic sparkle-FX
	 * position filter here, not gameplay-relevant, so a small local copy is simpler than exposing that one. */
	private static boolean isExposed(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!level.getBlockState(pos.relative(dir)).isCollisionShapeFullBlock(level, pos.relative(dir))) return true;
		}
		return false;
	}

	@Override
	public List<BlockPos> sparkle(Level level, Player player, BlockPos pos, Placement placement) {
		BlockPos anchor = pos.offset(placement.xOffset, placement.yOffset, placement.zOffset);
		List<BlockPos> list = new ArrayList<>();
		for (int y = 0; y < ySize; y++) {
			Matrix matrix = new Matrix(blueprint[y]);
			matrix.Rotate90DegRight(3 - placement.facing.get2DDataValue());
			Part[][] cells = matrix.getMatrix();
			for (int x = 0; x < cells.length; x++) {
				for (int z = 0; z < cells[x].length; z++) {
					Part cell = cells[x][z];
					if (cell == null || cell.getSource() == null) continue;
					BlockPos p3 = anchor.offset(x, -y + (ySize - 1), z);
					if (isExposed(level, p3)) {
						list.add(p3);
					}
				}
			}
		}
		return list;
	}

	@Override
	public void execute(Level level, Player player, BlockPos pos, Placement placement, Direction side) {
		if (level.isClientSide) return;
		// As the original: every multiblock conversion counts as crafting an Infernal Furnace - its own hardcoded item,
		// whichever structure was actually built (kept as it was).
		if (player != null) {
			net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(player, new ItemStack(thaumcraft.api.blocks.BlocksTC.infernalFurnace.get()),
					new net.minecraft.world.SimpleContainer(1));
		}
		BlockPos anchor = pos.offset(placement.xOffset, placement.yOffset, placement.zOffset);
		for (int y = 0; y < ySize; y++) {
			Matrix matrix = new Matrix(blueprint[y]);
			matrix.Rotate90DegRight(3 - placement.facing.get2DDataValue());
			Part[][] cells = matrix.getMatrix();
			for (int x = 0; x < cells.length; x++) {
				for (int z = 0; z < cells[x].length; z++) {
					Part cell = cells[x][z];
					if (cell == null || cell.getTarget() == null) continue;
					BlockPos p3 = anchor.offset(x, -y + (ySize - 1), z);
					convertOneCell(level, player, placement, side, cell, p3);
				}
			}
		}
	}

	private void convertOneCell(Level level, Player player, Placement placement, Direction side, Part cell, BlockPos p3) {
		ItemStack targetItem = ItemStack.EMPTY;
		BlockState targetState = null;

		if (cell.getTarget() instanceof Block targetBlock) {
			BlockState defaultState = targetBlock.defaultBlockState();
			if (defaultState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				Direction facingSide = side;
				if (facingSide.getAxis() == Direction.Axis.Y) {
					facingSide = player.getDirection().getOpposite();
				}
				Direction chosen = cell.getApplyPlayerFacing() ? facingSide : (cell.isOpp() ? placement.facing.getOpposite() : placement.facing);
				defaultState = defaultState.setValue(BlockStateProperties.HORIZONTAL_FACING, chosen);
			}
			targetState = defaultState;
			targetItem = new ItemStack(targetBlock);
		} else if (cell.getTarget() instanceof BlockState explicitState) {
			// A pre-built state with its own fixed, absolute facing baked in (e.g. each of the Infusion
			// Altar's 4 corner pillars) - real modern equivalent of the original's own metadata-encoded
			// ItemStack targets (`new ItemStack(pillarArcane, 1, BlockPillar.calcMeta(EnumFacing.EAST))`),
			// which this port's Ingredient/ItemStack no longer carries block-state metadata on. Used verbatim,
			// no placement-relative facing computation - the whole point is it does NOT rotate with placement.
			targetState = explicitState;
			targetItem = new ItemStack(explicitState.getBlock());
		} else if (cell.getTarget() instanceof ItemStack itemTarget) {
			targetItem = itemTarget.copy();
			if (targetItem.getItem() instanceof BlockItem blockItem) {
				targetState = blockItem.getBlock().defaultBlockState();
			}
		}

		// Match by the cell's own real source (a Block matches by type only, forgiving of e.g. iron bars' own
		// neighbor-sensitive connection state changing while the delayed swap is pending; a BlockState source
		// still matches exactly, for a real pre-existing-structure requirement like the Infusion Altar's own
		// pedestal). NOT a freshly captured level.getBlockState(p3) snapshot - a real bug caught while
		// verifying this exact blueprint: a neighboring cell's own conversion earlier in the same delayed
		// batch could reshape iron bars' connection state before its own swap ran, silently failing an
		// exact-state match against a now-stale snapshot.
		Object source = cell.getSource();
		ToolEvents.addBlockedBlock(level, p3);
		ItemStack finalTargetItem = targetItem;
		BlockState finalTargetState = targetState;
		BlockScheduler.addRunnableServer(level, () -> {
			BlockScheduler.addSwapper(level, p3, source, finalTargetItem, finalTargetState, false, 0, player,
					true, false, -9999, false, false, 0, BlockScheduler.DEFAULT_PREDICATE, 0.0f);
			ToolEvents.clearBlockedBlock(level, p3);
		}, cell.getPriority());
	}
}
