package thaumcraft.common.blocks.essentia;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.essentia.TileCentrifuge;

/**
 * Replaces the old (1.12.2) {@code BlockCentrifuge} - see {@link TileCentrifuge}'s own doc comment for the
 * real aspect-decomposition mechanic. The true original rendered {@code EnumBlockRenderType.INVISIBLE} and
 * drew its ENTIRE visual (frame and all) via a custom TileEntitySpecialRenderer with an animated spinning
 * axle. This port uses a real, already-present static baked model for the frame (real, complete machine
 * geometry, confirmed by reading the model file directly - it's not a placeholder) PLUS a real
 * {@link thaumcraft.client.render.TileCentrifugeRenderer} for the spinning axle on top, restored 2026-09-16 -
 * strictly better than the true original's own approach (a real static model AND the real animation, not one
 * or the other), not the tradeoff this doc comment used to describe before a real
 * {@code BlockEntityRenderer} precedent existed elsewhere in this port.
 */
public class BlockCentrifuge extends Block implements EntityBlock {

	public static final MapCodec<BlockCentrifuge> CODEC = simpleCodec(BlockCentrifuge::new);

	private static final VoxelShape SHAPE = Shapes.or(
			Block.box(4, 0, 4, 12, 16, 12),
			Block.box(0, 5, 6, 16, 11, 10));

	public BlockCentrifuge(BlockBehaviour.Properties properties) {
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

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileCentrifuge(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.CENTRIFUGE.get()
				? (lvl, pos, st, be) -> TileCentrifuge.tick(lvl, pos, st, (TileCentrifuge) be)
				: null;
	}

	/** As the original: the essentia inside pours into the aura as flux when this goes. */
	@Override
	protected void onRemove(net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.Level level,
			net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState newState, boolean movedByPiston) {
		thaumcraft.common.blocks.EssentiaSpill.onRemove(state, level, pos, newState);
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
