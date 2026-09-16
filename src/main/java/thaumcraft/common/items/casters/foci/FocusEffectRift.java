package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.tiles.misc.TileHole;

/**
 * Replaces the old (1.12.2) {@code FocusEffectRift} - "punches" a real temporary, walkable tunnel through
 * solid terrain along the cast direction (real depth = {@code depth setting * power}), reverting each
 * punched block back to its real original state after a real {@code duration setting} (in seconds). Real,
 * as-is core mechanic: {@link #createHole} is also called recursively by {@link TileHole#tick} to propagate
 * the tunnel forward one block per real tick after the first, and to open a real 3x3 burst of single-tick
 * holes in the plane perpendicular to the tunnel at each step (the original's own real "the tunnel opens
 * outward, not just forward" visual/functional detail).
 *
 * Real, deliberate cuts: no per-block-type blacklist (the original's {@code
 * BlockUtils#isPortableHoleBlackListed} was config-driven and no modern config system exists yet - the real
 * tile-entity/bedrock/hardness checks below already prevent tunneling through anything that would actually
 * break; matches this session's established "drop config-driven complexity, keep the real core mechanic"
 * pattern); no "Outer Lands" dimension block (that custom dimension was never ported); no client-only
 * particle trail (matches every other focus effect ported so far).
 */
public class FocusEffectRift extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSRIFT";
	}

	@Override
	public String getKey() {
		return "thaumcraft.RIFT";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.ELDRITCH;
	}

	@Override
	public int getComplexity() {
		return 3 + getSettingValue("duration") / 2 + getSettingValue("depth") / 4;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		if (!(target instanceof BlockHitResult blockHit)) return false;

		Level level = getPackage().world;
		float maxDistance = getSettingValue("depth") * finalPower;
		int duration = 20 * getSettingValue("duration");

		BlockPos pos = blockHit.getBlockPos();
		int distance;
		for (distance = 0; distance < maxDistance; distance++) {
			BlockState state = level.getBlockState(pos);
			if (state.isAir() || state.is(Blocks.BEDROCK) || state.is(BlocksTC.hole.get())) break;
			if (state.getDestroySpeed(level, pos) < 0) break;
			pos = pos.relative(blockHit.getDirection().getOpposite());
		}

		createHole(level, blockHit.getBlockPos(), blockHit.getDirection(), (byte) Math.round(distance + 1), duration);
		return true;
	}

	/** @return whether a real hole was actually created at {@code pos}. */
	public static boolean createHole(Level level, BlockPos pos, Direction side, byte count, int max) {
		BlockState state = level.getBlockState(pos);
		if (level.isClientSide || level.getBlockEntity(pos) != null || state.is(Blocks.BEDROCK)
				|| state.is(BlocksTC.hole.get()) || state.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		if (level.setBlock(pos, BlocksTC.hole.get().defaultBlockState(), 3)
				&& level.getBlockEntity(pos) instanceof TileHole tile) {
			tile.oldBlock = state;
			tile.countdownMax = (short) max;
			tile.count = count;
			tile.direction = side;
			tile.setChanged();
			return true;
		}
		return false;
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] depth = { 8, 16, 24, 32 };
		String[] depthDesc = { "8", "16", "24", "32" };
		return new NodeSetting[] {
				new NodeSetting("depth", "focus.rift.depth", new NodeSetting.NodeSettingIntList(depth, depthDesc)),
				new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(2, 10))
		};
	}

	/** See {@link thaumcraft.client.fx.ClientFxHandlers#spawnGeneric}'s own doc comment for why this builds a
	 * plain {@link FocusGenericFx} rather than an {@code FXGeneric} directly. */
	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		FocusGenericFx cfg = new FocusGenericFx();
		cfg.age = 16 + world.random.nextInt(16);
		cfg.partStart = 384 + world.random.nextInt(16);
		cfg.slowDown = 0.75;
		cfg.alpha = new float[] { 1.0f, 0.0f };
		cfg.scale = new float[] { (float) (0.7 + world.random.nextGaussian() * 0.3) };
		cfg.redStart = cfg.redEnd = 0.25f;
		cfg.greenStart = cfg.greenEnd = 0.25f;
		cfg.blueStart = cfg.blueEnd = 1.0f;
		cfg.randomX = cfg.randomY = cfg.randomZ = 0.01f;
		thaumcraft.client.fx.ClientFxHandlers.spawnGeneric(world, posX, posY, posZ, motionX, motionY, motionZ, cfg);
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.2f, 0.7f);
	}
}
