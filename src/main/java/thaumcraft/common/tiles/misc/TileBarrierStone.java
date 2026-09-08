package thaumcraft.common.tiles.misc;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileBarrierStone} - the real "keep non-player creatures out" tick logic
 * behind {@link thaumcraft.common.blocks.basic.BlockPavingStoneBarrier}. Real port of the original's own
 * two-cadence tick: every 5 ticks, while unpowered, gently shoves any real airborne non-player {@link
 * LivingEntity} within a 1x3x1 column above this tile back away from center (matches the original's own
 * real velocity formula exactly); every 100 ticks, ensures the real 2 {@link
 * thaumcraft.common.blocks.misc.BlockBarrier} copies directly above still real exist (self-healing if
 * something broke one).
 */
public class TileBarrierStone extends BlockEntity {

	private int count;

	public TileBarrierStone(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.BARRIER_STONE.get(), pos, state);
	}

	public boolean gettingPower() {
		return level != null && level.hasNeighborSignal(worldPosition);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileBarrierStone tile) {
		if (level.isClientSide) return;
		if (tile.count == 0) {
			tile.count = level.random.nextInt(100);
		}
		if (tile.count % 5 == 0 && !tile.gettingPower()) {
			tile.pushBackIntruders(level, pos);
		}
		if (++tile.count % 100 == 0) {
			tile.healBarrier(level, pos.above(1));
			tile.healBarrier(level, pos.above(2));
		}
	}

	private void pushBackIntruders(Level level, BlockPos pos) {
		AABB area = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 3, pos.getZ() + 1).inflate(0.1);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
			if (!entity.onGround() && !(entity instanceof Player)) {
				float yawRad = (entity.getYRot() + 180.0f) * Mth.DEG_TO_RAD;
				entity.push(-Mth.sin(yawRad) * 0.2, -0.1, Mth.cos(yawRad) * 0.2);
			}
		}
	}

	private void healBarrier(Level level, BlockPos above) {
		if (!level.getBlockState(above).is(BlocksTC.barrier.get()) && level.getBlockState(above).isAir()) {
			level.setBlock(above, BlocksTC.barrier.get().defaultBlockState(), 3);
		}
	}
}
