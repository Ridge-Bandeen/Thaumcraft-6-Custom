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
 * Replaces the old (1.12.2) {@code TileLampArcane}. Scatters real light sources around itself, using
 * vanilla's own {@link net.minecraft.world.level.block.LightBlock} (added in 1.17, a placeable invisible
 * light source with no equivalent needed in the original - which had to hand-roll {@code effectGlimmer}, a
 * custom marker block, for exactly this purpose). Every 5 ticks, while enabled, picks a random nearby air
 * position with low current light and a clear line of sight (via {@code Level#clip}, the same raytrace
 * mechanism {@code FocusMediumTouch}/{@code ItemCaster} already use) and places a light block there.
 *
 * Improvement over the original: placed positions are tracked in a persisted list rather than being
 * rediscovered by re-scanning a 31x31x31 volume on removal (the original's {@code removeLights}) - cheaper
 * and exact, no risk of clearing a light block placed by a second nearby lamp.
 *
 * Simplified vs the original: the block itself doesn't attach to an arbitrary face (torch/lantern-style) -
 * see {@code BlockLampArcane}'s own doc comment. No client FX.
 */
public class TileLampArcane extends BlockEntity {

	private static final int MAX_TRACKED = 64;

	private final List<BlockPos> placedLights = new ArrayList<>();

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
		if (target.getY() < level.getMinBuildHeight() + 5) {
			target = new BlockPos(target.getX(), level.getMinBuildHeight() + 5, target.getZ());
		}

		if (level.isEmptyBlock(target) && level.getBrightness(LightLayer.BLOCK, target) < 11 && hasLineOfSight(level, pos, target)) {
			level.setBlock(target, Blocks.LIGHT.defaultBlockState().setValue(BlockStateProperties.LEVEL, 14), 3);
			placedLights.add(target.immutable());
			if (placedLights.size() > MAX_TRACKED) {
				placedLights.remove(0);
			}
			setChanged();
		}
	}

	private static boolean hasLineOfSight(Level level, BlockPos source, BlockPos target) {
		Vec3 from = Vec3.atCenterOf(source);
		Vec3 to = Vec3.atCenterOf(target);
		HitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 1.0;
	}

	/** Called when the lamp block is removed - clears every light block this specific lamp placed, still
	 * present, without touching anything else nearby. */
	public void removeLights(Level level) {
		for (BlockPos light : placedLights) {
			if (level.getBlockState(light).is(Blocks.LIGHT)) {
				level.removeBlock(light, false);
			}
		}
		placedLights.clear();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (BlockPos p : placedLights) {
			CompoundTag posTag = new CompoundTag();
			posTag.putInt("X", p.getX());
			posTag.putInt("Y", p.getY());
			posTag.putInt("Z", p.getZ());
			list.add(posTag);
		}
		tag.put("PlacedLights", list);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		placedLights.clear();
		if (tag.contains("PlacedLights")) {
			for (Tag t : tag.getList("PlacedLights", 10)) {
				CompoundTag posTag = (CompoundTag) t;
				placedLights.add(new BlockPos(posTag.getInt("X"), posTag.getInt("Y"), posTag.getInt("Z")));
			}
		}
	}
}
