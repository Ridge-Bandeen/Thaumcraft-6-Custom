package thaumcraft.common.blocks.devices;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.devices.TileInfernalFurnace;

/**
 * Replaces the old (1.12.2) {@code BlockInfernalFurnace} - a real fuel-free, vis-fed lava furnace: anything
 * dropped on top gets pulled in and auto-smelted, anything not smeltable is destroyed, living entities that
 * touch it take real lava-like damage and catch fire.
 *
 * As the original, it is made by a Salis Mundus ritual (ThaumcraftDustTriggers#infernalFurnaceBlueprint): a 3x3x3 of
 * nether bricks and obsidian around a lava core, iron bars in the opening at the back. The lava becomes this block and
 * the shell becomes invisible placeholders ({@link thaumcraft.common.blocks.misc.BlockPlaceholder}) - its 3x3x3 model
 * draws the whole furnace. Broken (or a shell block broken), it comes apart again: {@link #destroyFurnace}. It drops
 * nothing itself. The smelting-bonus nuggets ({@code ThaumcraftApi.SmeltBonus}) are in {@link TileInfernalFurnace}.
 */
public class BlockInfernalFurnace extends Block implements EntityBlock {

	public static final MapCodec<BlockInfernalFurnace> CODEC = simpleCodec(BlockInfernalFurnace::new);

	private static final VoxelShape SHAPE = Shapes.box(0, 0, 0, 1, 0.5, 1);

	public BlockInfernalFurnace(BlockBehaviour.Properties properties) {
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

	/** Breaking the furnace (a player or an explosion) takes it apart as the original did; it drops nothing (see its loot
	 * table). Both hooks run after the block is gone, so the lava left behind isn't overwritten by the removal. Not on a
	 * plain replacement (world edits, test cleanup), where lava would be unwelcome. */
	@Override
	public void destroy(net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockState state) {
		super.destroy(level, pos, state);
		if (level instanceof Level l) destroyFurnace(l, pos, state, pos);
	}

	@Override
	public void wasExploded(Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
		super.wasExploded(level, pos, explosion);
		destroyFurnace(level, pos, defaultBlockState(), pos);
	}

	/** The original's guard: taking the furnace apart breaks placeholders, which would start another teardown. */
	private static boolean ignore;

	/** The original's destroyFurnace: the nether-brick/obsidian placeholders around turn back into their real blocks, the
	 * iron bars go back in the opening behind the furnace if it is empty, and the furnace becomes lava again. */
	public static void destroyFurnace(Level level, BlockPos pos, BlockState state, BlockPos from) {
		if (ignore || level.isClientSide) return;
		ignore = true;
		try {
			if (level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.devices.TileInfernalFurnace tile && !tile.origins().isEmpty()) {
				// put back exactly what it was built from: parts still in place, the emptied grate, and the core
				for (var e : java.util.Map.copyOf(tile.origins().all()).entrySet()) {
					BlockPos p = e.getKey();
					if (p.equals(pos)) continue;
					if (p.equals(from)) {
						thaumcraft.common.lib.crafting.MultiblockOrigins.dropAs(level, p, e.getValue());
						continue;
					}
					BlockState now = level.getBlockState(p);
					if (now.getBlock() instanceof thaumcraft.common.blocks.misc.BlockPlaceholder || now.isAir()) {
						level.setBlock(p, e.getValue(), Block.UPDATE_ALL);
					}
				}
				BlockState core = tile.origins().at(pos);
				level.setBlock(pos, core != null ? core : net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
				return;
			}
			for (BlockPos p : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
				if (p.equals(from)) continue;
				BlockState s = level.getBlockState(p);
				if (s.is(thaumcraft.api.blocks.BlocksTC.placeholderNetherbrick.get())) {
					level.setBlock(p.immutable(), net.minecraft.world.level.block.Blocks.NETHER_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
				} else if (s.is(thaumcraft.api.blocks.BlocksTC.placeholderObsidian.get())) {
					level.setBlock(p.immutable(), net.minecraft.world.level.block.Blocks.OBSIDIAN.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
				BlockPos back = pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
				if (level.isEmptyBlock(back)) level.setBlock(back, net.minecraft.world.level.block.Blocks.IRON_BARS.defaultBlockState(), Block.UPDATE_ALL);
			}
			level.setBlock(pos, net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);
		} finally {
			ignore = false;
		}
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		// Real original semantic: faces AWAY from the placer, matching its own real getStateForPlacement
		// (placer.getHorizontalFacing().getOpposite()) - items get ejected out the front, away from whoever
		// built it.
		return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Nullable
	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new TileInfernalFurnace(pos, state);
	}

	@Nullable
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return type == ThaumcraftBlockEntities.INFERNAL_FURNACE.get()
				? (lvl, pos, st, be) -> TileInfernalFurnace.tick(lvl, pos, st, (TileInfernalFurnace) be)
				: null;
	}

	/** Real port of the original's own real {@code onEntityCollidedWithBlock} - a gentle horizontal nudge
	 * toward the furnace's center (so items/mobs settle in rather than sliding off), and every 10 real ticks:
	 * a real item resting on top gets pulled into the tile's inventory, a real non-fire-immune living entity
	 * takes real lava damage and catches fire. */
	@Override
	protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
		double dx = 0;
		double dz = 0;
		if (entity.getX() < pos.getX() + 0.3) dx = 0.0001;
		if (entity.getX() > pos.getX() + 0.7) dx = -0.0001;
		if (entity.getZ() < pos.getZ() + 0.3) dz = 0.0001;
		if (entity.getZ() > pos.getZ() + 0.7) dz = -0.0001;
		if (dx != 0 || dz != 0) {
			Vec3 motion = entity.getDeltaMovement();
			entity.setDeltaMovement(motion.x + dx, motion.y, motion.z + dz);
		}

		if (!level.isClientSide && entity.tickCount % 10 == 0) {
			if (entity instanceof ItemEntity itemEntity) {
				if (itemEntity.onGround() && level.getBlockEntity(pos) instanceof TileInfernalFurnace tile) {
					itemEntity.setItem(tile.addItemsToInventory(itemEntity.getItem()));
				}
			} else if (entity instanceof LivingEntity living && !living.fireImmune()) {
				living.hurt(level.damageSources().lava(), 3.0f);
				living.igniteForSeconds(10);
			}
		}
	}
}
