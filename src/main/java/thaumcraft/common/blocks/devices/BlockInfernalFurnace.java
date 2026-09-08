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
 * Real, documented simplification, same "pick the right vanilla-shaped block directly, drop the multi-block
 * illusion" pattern {@code BlockGolemBuilder}'s own doc comment already established for exactly this
 * situation: the original wasn't a real block at all - an invisible core built by a whole separate "structure
 * recognition" ritual ({@code IDustTrigger}/{@code DustTriggerMultiblock}) that swapped a real 3x3x3
 * arrangement of decorative nether-brick/obsidian/iron-bars "placeholder" blocks into their real vanilla
 * equivalents on completion/destruction. This currently ships as one real, solid, directly placed/broken
 * block instead, with a real {@link thaumcraft.api.crafting} recipe using the same real ingredients the
 * original's ritual consumed (nether bricks/obsidian/iron bars/lava bucket) rather than the original's
 * structure-based acquisition - <b>a real multiblock subsystem is now being built (see the port-tracking
 * memory), and this device's acquisition path is a real migration target once it exists.</b> The
 * smelting-bonus-nugget system ({@code ThaumcraftApi.SmeltBonus}) - previously deferred here with the claim
 * that it needed nugget items this port hadn't built - is real now; see {@link TileInfernalFurnace}'s own doc
 * comment for the real smelting mechanic and the bonus table's own real, carefully-checked scope.
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
