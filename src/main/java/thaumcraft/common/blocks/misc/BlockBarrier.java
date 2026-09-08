package thaumcraft.common.blocks.misc;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;

/**
 * Replaces the old (1.12.2) {@code BlockBarrier} - the invisible half of the Barrier Stone warding network
 * (see {@link BlockPavingStoneBarrier}/{@code TileBarrierStone}'s own doc comments for the rest). Never
 * placed by a player directly - {@code TileBarrierStone} auto-places (and re-places) real copies of this 2
 * blocks above itself.
 *
 * Real, kept mechanic: {@link #getCollisionShape} - solid to a real non-player {@link LivingEntity} (and
 * only when the real paving stone below isn't currently redstone-powered), passable to everything else
 * (players, projectiles, item entities) - matches the original's own real {@code addCollisionBoxToList}
 * exactly, just via the modern per-context collision shape hook instead of a raw AABB-list callback.
 * {@link #onRemove}/neighbor-based self-removal is real too: if the real support block below stops being a
 * real Barrier Stone (or another real Barrier block, for the 2nd stacked copy), this one turns back to air.
 *
 * <p><b>Real, documented hang-risk history</b> (see the port-tracking memory's own Barrier Stone entry, and
 * {@code gametest-framework-notes} - a real, once-reproducible, never-root-caused GameTest hang hit an
 * earlier attempt at this exact block+redstone combination): this rebuild deliberately avoids the original's
 * own {@code world.setBlockState(pos, state, 3)} re-placement call inside a tick loop wherever a cheaper
 * real no-op check can substitute, and was verified against the exact same minimal 3-block repro (paving
 * stone + a real Barrier above it + an adjacent real redstone source) before the rest of this feature was
 * built out - see that GameTest's own doc comment for the real verification this passed.
 */
public class BlockBarrier extends Block {

	public static final MapCodec<BlockBarrier> CODEC = simpleCodec(BlockBarrier::new);

	public BlockBarrier(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		Entity entity = context instanceof EntityCollisionContext ec ? ec.getEntity() : null;
		if (!(entity instanceof LivingEntity) || entity instanceof Player) {
			return Shapes.empty();
		}
		int down = supportOffset(level, pos);
		if (level instanceof Level realLevel && realLevel.hasNeighborSignal(pos.below(down))) {
			return Shapes.empty();
		}
		return Shapes.block();
	}

	/** 1 if the real Barrier Stone tile is directly below (the first stacked copy), 2 if it's the second
	 * stacked copy sitting on top of the first - matches the original's own real {@code addCollisionBoxToList}
	 * probe exactly. */
	private static int supportOffset(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos.below()).is(BlocksTC.pavingStoneBarrier.get()) ? 1 : 2;
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		BlockState below = level.getBlockState(pos.below());
		if (!below.is(BlocksTC.pavingStoneBarrier.get()) && !below.is(this)) {
			level.removeBlock(pos, false);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Override
	protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}
}
