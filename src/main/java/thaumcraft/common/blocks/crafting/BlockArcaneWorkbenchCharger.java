package thaumcraft.common.blocks.crafting;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;

/**
 * Replaces the old (1.12.2) {@code BlockArcaneWorkbenchCharger} - a no-tile topper block that must sit
 * directly on an Arcane Workbench, widening the workbench's vis draw from its own chunk to the full 3x3
 * chunk block around it (see {@link thaumcraft.common.container.ArcaneWorkbenchMenu}'s own doc comment and
 * {@code AuraHandler#getWideVis}/{@code #drainWideVis} for the actual wider-area logic this unlocks - both
 * shipped alongside this block). Right-clicking it opens the same workbench menu as clicking the workbench
 * itself, matching the original's own "either block opens the same GUI" design.
 *
 * Simplified vs the original: only sits on the real Arcane Workbench, not the original's second target
 * ({@code wandWorkbench}, an unported device with its own separate crafting GUI).
 */
public class BlockArcaneWorkbenchCharger extends Block {

	public static final MapCodec<BlockArcaneWorkbenchCharger> CODEC = simpleCodec(BlockArcaneWorkbenchCharger::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.375, 1.0);

	public BlockArcaneWorkbenchCharger(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlocksTC.arcaneWorkbench.get());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
		if (level.isClientSide) return InteractionResult.SUCCESS;
		if (level.getBlockEntity(pos.below()) instanceof TileArcaneWorkbench workbench) {
			player.openMenu(workbench);
		}
		return InteractionResult.CONSUME;
	}
}
