package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileJarBrain} - the "Brain in a Jar", a portable XP battery. Real
 * mechanic, faithfully ported: passively pulls nearby {@link ExperienceOrb}s within 8 blocks toward itself
 * (a real, gentle, distance-scaled magnetic force, matching the original's own real formula exactly) and
 * consumes any orb that actually touches it, capping at {@link #XP_MAX}. Right-clicking (see {@link
 * thaumcraft.common.blocks.essentia.BlockJarBrain#useWithoutItem}) drains a real random amount (up to
 * {@code min(xp, 64)}) back out as real vanilla XP orbs - same "curated, portable store of levels" real
 * gameplay the original had, just without needing a container GUI. A short {@link #eatDelay} after draining
 * (matching the original's own real 40-tick value) stops the jar from immediately re-swallowing the orbs it
 * just spawned.
 *
 * <p>Real, documented cut: the original's own client-side "the brain slowly looks around the room, sighs
 * occasionally" idle animation ({@code TileJarRenderer#renderBrain}, driven by a handful of rotation/timer
 * fields with zero real gameplay effect) isn't ported - same "drop pure client FX, keep the real mechanic"
 * precedent this session already applied to {@code GolemLegLevitator}/{@code GolemLegWheels}. The block
 * always renders as the plain static jar model.
 *
 * <p>Real, documented cut: the original's own {@code getEnchantPowerBonus() -> 5.0f} (a stronger-than-a-
 * bookshelf enchanting boost) has no direct modern equivalent - modern Minecraft replaced the old per-block
 * float bonus with a flat, tag-driven {@code minecraft:enchantment_power_provider} block tag (a fixed +1 per
 * qualifying block in range, not a variable magnitude). This block is tagged into it (see
 * {@code data/minecraft/tags/block/enchantment_power_provider.json}) for a real, if weaker, modern
 * equivalent rather than dropping the perk outright.
 *
 * <p>Real, minor simplification: a merged/stacked vanilla {@code ExperienceOrb} entity (several orbs that
 * despawn-merged into one, tracked via a private {@code count} field with no public accessor) is only
 * credited for its own {@link ExperienceOrb#getValue()} once, not {@code value * count} - a genuine, rare
 * edge case (many kills in the same spot), not reachable without reflection into a private vanilla field.
 */
public class TileJarBrain extends BlockEntity {

	public static final int XP_MAX = 2000;
	private static final double PULL_RADIUS = 8.0;

	private int xp;
	private int eatDelay;

	public TileJarBrain(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.JAR_BRAIN.get(), pos, state);
	}

	public int getXp() {
		return xp;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileJarBrain tile) {
		if (level.isClientSide) return;
		if (tile.eatDelay > 0) {
			tile.eatDelay--;
			return;
		}
		if (tile.xp >= XP_MAX) return;

		AABB touchBox = new AABB(pos).inflate(0.1);
		java.util.List<ExperienceOrb> touching = level.getEntitiesOfClass(ExperienceOrb.class, touchBox);
		if (!touching.isEmpty()) {
			boolean changed = false;
			for (ExperienceOrb orb : touching) {
				if (tile.xp >= XP_MAX) break;
				tile.xp = Math.min(XP_MAX, tile.xp + orb.getValue());
				level.playSound(null, pos, SoundEvents.GENERIC_EAT, SoundSource.BLOCKS,
						0.1f, (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.2f + 1.0f);
				orb.discard();
				changed = true;
			}
			if (changed) tile.setChanged();
			return;
		}

		// Gentle magnetic pull on orbs within range but not yet touching - real, matches the original's own
		// real formula (an inverse-distance pull that strengthens as the orb gets closer, capped at 1 block).
		Vec3 center = Vec3.atCenterOf(pos);
		for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, new AABB(pos).inflate(PULL_RADIUS))) {
			double dx = (center.x - orb.getX()) / 25.0;
			double dy = (center.y - orb.getY()) / 25.0;
			double dz = (center.z - orb.getZ()) / 25.0;
			double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (dist <= 1.0E-4) continue;
			double pull = 1.0 - dist;
			if (pull > 0.0) {
				pull *= pull;
				orb.setDeltaMovement(orb.getDeltaMovement().add(dx / dist * pull * 0.3, dy / dist * pull * 0.5, dz / dist * pull * 0.3));
			}
		}
	}

	/** Real right-click drain - see this class's own doc comment. Always resets {@link #eatDelay}, even when
	 * nothing was actually drained (matches the original's own unconditional {@code eatDelay = 40} on every
	 * click). @return true if any real XP was actually paid out. */
	public boolean drainRandom(ServerLevel level, BlockPos pos, RandomSource random) {
		eatDelay = 40;
		if (xp <= 0) return false;
		int drained = random.nextInt(Math.min(xp + 1, 64));
		if (drained <= 0) return false;
		xp -= drained;
		ExperienceOrb.award(level, Vec3.atCenterOf(pos), drained);
		setChanged();
		return true;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("XP", xp);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		xp = tag.getInt("XP");
	}
}
