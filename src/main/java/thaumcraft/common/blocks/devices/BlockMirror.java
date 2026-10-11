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
 * Breaking a linked mirror drops a linked mirror item ({@link #getDrops}), as the original.
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

	/** As the original: drawn only by TileMirrorRenderer (the frame and glass); the cube model is for particles. */
	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.INVISIBLE;
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

	/** Block event 1: wispy motes drifting out of the glass (an item went in or came out), as the original. */
	@Override
	protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
		if (id == 1) {
			if (level.isClientSide) {
				thaumcraft.client.fx.ClientFxHandlers.mirrorMotes(level, pos, state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING));
			}
			return true;
		}
		return super.triggerEvent(state, level, pos, id, param);
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

	/** As the original ({@code dropMirror}): a linked mirror drops as a linked mirror item, still pointing at its partner,
	 * so it can be placed somewhere else and pair up again without relinking. */
	@Override
	protected java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(this);
		if (params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof TileMirror tile
				&& tile.getLink() != null) {
			stack.set(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get(), tile.getLink());
		}
		return java.util.List.of(stack);
	}

	/** As the original (BlockTCTile#breakBlock): items still waiting to come out drop when the mirror goes. */
	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TileMirror tile) {
			for (net.minecraft.world.item.ItemStack stack : tile.getOutputStacks()) {
				net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
			}
			tile.getOutputStacks().clear();
		}
		super.onRemove(state, level, pos, newState, movedByPiston);
	}
}
