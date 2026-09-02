package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileMirror;

/**
 * Replaces the old (1.12.2) {@code BlockMirror} (the plain item-transport variant only - see
 * {@link TileMirror}'s own doc comment for what's deferred, {@code TileMirrorEssentia}/{@code ItemHandMirror}
 * chief among them). Full 6-way {@link BlockStateProperties#FACING}, same shape
 * {@link BlockArcaneEar}/{@link thaumcraft.common.blocks.devices.BlockBellows} already established for a
 * device that can be placed on any face, not just horizontally.
 *
 * The original was fully invisible in-world ({@code EnumBlockRenderType.INVISIBLE}, no block model asset
 * ever existed for it - only an item icon), relying entirely on a custom TESR (a "mirror frame + reflective
 * pane" visual) for anything to actually be visible - dropped, same "no custom renderer" boundary this whole
 * port has kept, replaced with a single static thin-panel model instead (real geometry now, not invisible) -
 * see {@code data/.../models/block/mirror.json}.
 *
 * Also drops the original's "preserve the link in the dropped item's NBT when you break a linked mirror"
 * mechanic - same precedent the Void Jar's own port already set (breaking a filled jar drops a plain empty
 * one instead of preserving contents); breaking a linked mirror here just drops a plain unlinked one, and
 * the other side's link is left dangling (harmless - {@link TileMirror#isLinkValid()} simply reports false
 * once the tile is gone).
 */
public class BlockMirror extends Block implements EntityBlock {

	public static final MapCodec<BlockMirror> CODEC = simpleCodec(BlockMirror::new);

	public BlockMirror(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(getStateDefinition().any().setValue(BlockStateProperties.FACING, Direction.UP));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(BlockStateProperties.FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(BlockStateProperties.FACING, context.getClickedFace());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return switch (state.getValue(BlockStateProperties.FACING)) {
			case DOWN -> Shapes.box(0, 0.875, 0, 1, 1, 1);
			case UP -> Shapes.box(0, 0, 0, 1, 0.125, 1);
			case NORTH -> Shapes.box(0, 0, 0.875, 1, 1, 1);
			case SOUTH -> Shapes.box(0, 0, 0, 1, 1, 0.125);
			case WEST -> Shapes.box(0.875, 0, 0, 1, 1, 1);
			case EAST -> Shapes.box(0, 0, 0, 0.125, 1, 1);
		};
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockPos support = pos.relative(facing.getOpposite());
		if (!level.getBlockState(support).isFaceSturdy(level, support, facing)) {
			level.destroyBlock(pos, true);
		}
		super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
	}

	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		if (!level.isClientSide && entity instanceof ItemEntity itemEntity && !itemEntity.isRemoved() && !itemEntity.isOnPortalCooldown()
				&& level.getBlockEntity(pos) instanceof TileMirror tile) {
			tile.transport(itemEntity);
		}
		super.entityInside(state, level, pos, entity);
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileMirror(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.MIRROR.get()
				? (lvl, p, st, be) -> TileMirror.tick(lvl, p, st, (TileMirror) be)
				: null;
	}
}
