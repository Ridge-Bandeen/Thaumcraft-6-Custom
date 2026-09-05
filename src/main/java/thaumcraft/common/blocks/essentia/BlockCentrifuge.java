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
 * real aspect-decomposition mechanic. The original rendered {@code EnumBlockRenderType.INVISIBLE} and drew
 * its ENTIRE visual (frame and all) via a custom TileEntitySpecialRenderer with an animated spinning axle -
 * this port uses the real, already-present static baked model instead (real, complete machine geometry,
 * confirmed by reading the model file directly - it's not a placeholder), a real, deliberate simplification
 * that trades the axle's spin animation for actually being visible without a brand new TESR/BlockEntityRenderer
 * subsystem this port has no other precedent for.
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
}
