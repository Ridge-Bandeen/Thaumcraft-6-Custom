package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileLampArcane}, as the original: every 5 ticks while lit it picks a random spot up
 * to 15 blocks away (no more than 4 above the ground there, and not below the fifth block from the bottom of the
 * world), and if that spot is air, darker than block light 11 and in sight of the lamp, leaves a glimmer there
 * ({@code BlocksTC#effectGlimmer}, invisible full light). Turning it off with redstone or removing it clears every
 * glimmer within 15 blocks.
 */
public class TileLampArcane extends BlockEntity {

	public TileLampArcane(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.LAMP_ARCANE.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileLampArcane tile) {
		if (level.isClientSide) return;
		if (!state.getValue(BlockStateProperties.ENABLED)) return;
		if (level.getGameTime() % 5L != 0L) return;
		tile.scatterAttempt(level, pos);
	}

	/** The actual scatter-one-light attempt, split out from {@link #tick} so tests can call it directly
	 * many times in a row without needing to wait out the real 5-tick throttle (a rate limit for normal
	 * play, not something a test needs to respect). */
	public void scatterAttempt(Level level, BlockPos pos) {
		int dx = level.random.nextInt(16) - level.random.nextInt(16);
		int dy = level.random.nextInt(16) - level.random.nextInt(16);
		int dz = level.random.nextInt(16) - level.random.nextInt(16);
		BlockPos target = pos.offset(dx, dy, dz);
		int top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, target).getY();
		if (target.getY() > top + 4) {
			target = new BlockPos(target.getX(), top + 4, target.getZ());
		}
		if (target.getY() < level.getMinBuildHeight() + 5) {
			target = new BlockPos(target.getX(), level.getMinBuildHeight() + 5, target.getZ());
		}

		BlockState there = level.getBlockState(target);
		if (there.isAir() && !there.is(thaumcraft.api.blocks.BlocksTC.effectGlimmer.get()) && level.getBrightness(LightLayer.BLOCK, target) < 11
				&& hasLineOfSight(level, pos, target)) {
			level.setBlock(target, thaumcraft.api.blocks.BlocksTC.effectGlimmer.get().defaultBlockState(), 3);
		}
	}

	private static boolean hasLineOfSight(Level level, BlockPos source, BlockPos target) {
		Vec3 from = Vec3.atCenterOf(source);
		Vec3 to = Vec3.atCenterOf(target);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1.0;
	}

	/** The original's removeLights: every glimmer within 15 blocks goes out. */
	public void removeLights(Level level) {
		BlockPos pos = getBlockPos();
		for (BlockPos p : BlockPos.betweenClosed(pos.offset(-15, -15, -15), pos.offset(15, 15, 15))) {
			if (level.getBlockState(p).is(thaumcraft.api.blocks.BlocksTC.effectGlimmer.get())) {
				level.removeBlock(p, false);
			}
		}
	}
}
