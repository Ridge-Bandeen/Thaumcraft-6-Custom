package thaumcraft.common.blocks.basic;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockPillar} - a real, structurally-required corner block for
 * {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix}'s own real pillar/tier system, restored while
 * reviewing this port's own "dead/blocked" candidates (see that class's own doc comment and the port-tracking
 * memory for the fuller review): the true original's own {@code validLocation()} required 4 real
 * {@code BlockPillar}-typed blocks at the altar's corners, and a uniform set of all-Ancient or all-Eldritch
 * pillars gave a real cycle-time/cost/stability bonus - none of that was a deliberate cut, just never built.
 *
 * <b>Deliberately NOT built via the original's own {@code DustTriggerMultiblock} wand-conversion ritual</b>
 * (the true original's real path: build a whole 3x3x3 blueprint of plain {@code stoneArcane}/etc. blocks, then
 * a wand ability converts the corners into pillars all at once) - that whole subsystem was never ported, and
 * this class instead follows the SAME precedent already set for the Arcane Workbench and Crucible (both
 * originally wand-converted too, both ported as plain directly-placeable blocks instead, with the
 * dust-trigger conversion flagged as a deferred TODO in their own doc comments). {@code pillarArcane} gets a
 * real, if simplified, direct crafting recipe from {@code stoneArcane} instead.
 *
 * The other two tiers ({@code pillarAncient}/{@code pillarEldritch}) are still registered here as real blocks
 * with real tier-bonus behavior (see {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix#getSurroundings}) -
 * they just have no real crafting recipe yet, since their own true source materials ({@code stoneAncient}/
 * {@code stoneEldritchTile}) were never ported either. Ships real but currently unreachable, same honest
 * pattern as {@code nuggetRareearth}/{@code morphicResonator} from this same review pass, not a substitute.
 *
 * Breaking a pillar drops 2 of its own tier's stone material (never the pillar item itself) - a real, faithful
 * mechanic from the true original's own {@code breakBlock}, restored here via each tier's own loot table
 * instead of a manual drop-suppression hack.
 */
public class BlockPillar extends Block {

	public static final MapCodec<BlockPillar> CODEC = simpleCodec(BlockPillar::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);
	// The true original's own real getCollisionBoundingBox - taller than the render/outline shape, blocking
	// movement through the full 2-block altar-corner column even though only the bottom half renders.
	private static final VoxelShape COLLISION_SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 2.0, 1.0);

	public BlockPillar(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.HORIZONTAL_FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return COLLISION_SHAPE;
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.BLOCK;
	}
}
