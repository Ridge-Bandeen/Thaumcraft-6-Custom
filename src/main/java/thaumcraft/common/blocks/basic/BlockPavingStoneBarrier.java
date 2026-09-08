package thaumcraft.common.blocks.basic;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.misc.TileBarrierStone;

/**
 * Replaces the {@code paving_stone_barrier} half of the old (1.12.2) {@code BlockPavingStone} - the warding-
 * network floor tile, sibling to the already-real {@link BlockPavingStoneTravel} (see that class's own doc
 * comment for why the original's shared class is split in two here instead). Real shape/thin-slab look
 * reused as-is from that sibling. The real mechanic lives in {@link TileBarrierStone} - this block just
 * hosts the tile.
 */
public class BlockPavingStoneBarrier extends Block implements EntityBlock {

	public static final MapCodec<BlockPavingStoneBarrier> CODEC = simpleCodec(BlockPavingStoneBarrier::new);

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9375, 1.0);

	public BlockPavingStoneBarrier(BlockBehaviour.Properties properties) {
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
		return new TileBarrierStone(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.BARRIER_STONE.get()
				? (lvl, p, st, be) -> TileBarrierStone.tick(lvl, p, st, (TileBarrierStone) be)
				: null;
	}
}
