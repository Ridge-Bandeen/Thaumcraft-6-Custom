package thaumcraft.common.blocks.crafting;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;

/**
 * Replaces the old (1.12.2) {@code BlockInfusionMatrix} - the "Runic Matrix". See {@link TileInfusionMatrix}'s
 * own doc comment for the full list of what's simplified in this device this pass.
 *
 * The original rendered as {@code EnumBlockRenderType.INVISIBLE} and drew itself entirely through a custom
 * {@code TileEntitySpecialRenderer} (a rotating variant of {@code models/block/infusion_matrix.json}, which
 * still exists as a real static model in this port's assets). No custom {@code BlockEntityRenderer} has been
 * ported this whole session (see the port-tracking memory notes' recurring disqualifier list) - this block
 * just renders that existing static model directly through the normal blockstate path instead, losing only
 * the rotation animation.
 */
public class BlockInfusionMatrix extends Block implements EntityBlock {

	public static final MapCodec<BlockInfusionMatrix> CODEC = simpleCodec(BlockInfusionMatrix::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.5, 1);

	public BlockInfusionMatrix(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		// as the original: the renderer draws the cubes (they spin and jitter while active); the model is for the item
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileInfusionMatrix(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.INFUSION_MATRIX.get()
				? (lvl, pos, st, be) -> TileInfusionMatrix.tick(lvl, pos, st, (TileInfusionMatrix) be)
				: null;
	}

	// No right-click of its own: as the original, only a caster starts it (ItemCaster -> TileInfusionMatrix#onCasterRightClick).
	// The port's empty-hand activation, a stand-in from before casters existed, was removed 2026-09-30.
}
