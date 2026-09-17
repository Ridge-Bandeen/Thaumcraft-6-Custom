package thaumcraft.common.blocks.basic;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.tiles.misc.TileBarrierStone;

/**
 * Replaces the {@code paving_stone_barrier} half of the old (1.12.2) {@code BlockPavingStone} - the warding-
 * network floor tile, sibling to the already-real {@link BlockPavingStoneTravel} (see that class's own doc
 * comment for why the original's shared class is split in two here instead). Real shape/thin-slab look
 * reused as-is from that sibling. The real mechanic lives in {@link TileBarrierStone} - this block just
 * hosts the tile.
 *
 * <p>Real client-only ambient warding-state rune cue restored 2026-09-17 (was dropped under this port's old
 * blanket "no client FX" rule) - a real faithful port of the true original's own real {@code
 * randomDisplayTick} 3-way state cue (powered/dormant, barrier-damaged, or a real living non-player intruder
 * nearby), routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnBlockRunes} for the same real
 * dist-crash-avoidance reason every other client-only trigger in this port uses.
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

	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		if (!(level.getBlockEntity(pos) instanceof TileBarrierStone tile)) return;

		if (tile.gettingPower()) {
			for (int a = 0; a < 4; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, pos.getX(), pos.getY() + 0.7f, pos.getZ(),
						0.2f + random.nextFloat() * 0.4f, random.nextFloat() * 0.3f, 0.8f + random.nextFloat() * 0.2f, 20, -0.02f);
			}
		} else if (!level.getBlockState(pos.above(1)).is(BlocksTC.barrier.get()) || !level.getBlockState(pos.above(2)).is(BlocksTC.barrier.get())) {
			for (int a = 0; a < 6; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, pos.getX(), pos.getY() + 0.7f, pos.getZ(),
						0.9f + random.nextFloat() * 0.1f, random.nextFloat() * 0.3f, random.nextFloat() * 0.3f, 24, -0.02f);
			}
		} else {
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(1.0))) {
				if (!(entity instanceof Player)) {
					thaumcraft.client.fx.ClientFxHandlers.spawnBlockRunes(level, pos.getX(),
							pos.getY() + 0.6f + random.nextFloat() * Math.max(0.8f, entity.getEyeHeight()), pos.getZ(),
							0.6f + random.nextFloat() * 0.4f, 0.0f, 0.3f + random.nextFloat() * 0.7f, 20, 0.0f);
					break;
				}
			}
		}
	}
}
