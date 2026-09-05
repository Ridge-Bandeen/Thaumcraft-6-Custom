package thaumcraft.common.blocks.misc;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.misc.TileHole;

/**
 * Replaces the old (1.12.2) {@code BlockHole} - the internal, never-obtainable block {@link
 * FocusEffectRift} temporarily swaps a real solid block into. Real, as-is: indestructible, no collision (a
 * real player just walks straight through), glowing (matches the original's own real light level 0.7/15 -
 * scaled to the modern 0-15 range), no drops, no item form worth having (the original hid it from every
 * creative tab and made it undroppable/unpickable for the same reason - it should never end up in an
 * inventory).
 */
public class BlockHole extends Block implements EntityBlock {

	public static final MapCodec<BlockHole> CODEC = simpleCodec(BlockHole::new);

	public BlockHole(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected RenderShape getRenderShape(BlockState state) {
		// Fully invisible, matching the original's own real "thaumcraft:empty" model - the modern
		// blockstate JSON still points at a real (never-actually-drawn) model purely so asset loading
		// doesn't warn about a missing one.
		return RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return Shapes.empty();
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileHole(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.HOLE.get()
				? (lvl, pos, st, be) -> TileHole.tick(lvl, pos, st, (TileHole) be)
				: null;
	}
}
