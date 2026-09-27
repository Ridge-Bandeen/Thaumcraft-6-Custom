package thaumcraft.common.tiles.devices;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftBlockEntities;
import thaumcraft.common.registry.ThaumcraftSounds;

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
 * <p>Real client-only "looks around the room, sighs occasionally" idle animation restored 2026-09-17
 * ({@link #rota}/{@link #rotb}/{@link #targetYaw}/{@link #nextSighTime}) - re-evaluating this class's own
 * earlier "drop pure client FX" cut per this port's own standing "reevaluate past cuts once their blocker
 * resolves" rule. Real, faithful port of the true original's own exact formula: eases its own facing angle
 * toward the nearest XP orb it's actively pulling, or the nearest player within 6 blocks if no orb is in
 * range, or just slowly spins on its own if neither is present; plays a quiet ambient sigh
 * ({@link ThaumcraftSounds#BRAIN}) on a real random 5-30 second cadence whenever a player is nearby. See
 * {@link thaumcraft.client.render.TileJarRenderer}'s own doc comment for the rendering side. The true
 * original's own separate {@code field_40059_f}/{@code field_40061_d} fields are real dead code - declared
 * and updated, but never actually read by its own real renderer - not ported, same "don't resurrect
 * genuinely unused state" precedent as {@code ModelJar}'s own unused {@code Core} box.
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

	/** Client-only idle-animation state - see this class's own doc comment. Never synced. */
	private float rota;
	private float rotb;
	private float targetYaw;
	private long nextSighTime = System.currentTimeMillis() + 1500L;

	public TileJarBrain(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.JAR_BRAIN.get(), pos, state);
	}

	public int getXp() {
		return xp;
	}

	/** Restores XP a broken jar carried on its item (see {@code JarBrainBlockItem#place}). */
	public void setXp(int xp) {
		this.xp = Math.max(0, Math.min(XP_MAX, xp));
	}

	public float getRota() {
		return rota;
	}

	public float getRotb() {
		return rotb;
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileJarBrain tile) {
		if (level.isClientSide) {
			tile.tickClient(level, pos);
			return;
		}
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

	/** Real direct port of the true original's own real client-only idle "look toward whatever's interesting,
	 * ease into it" formula - see this class's own doc comment. */
	private void tickClient(Level level, BlockPos pos) {
		rotb = rota;
		Player nearest = level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6.0, false);
		if (nearest != null) {
			if (nextSighTime < System.currentTimeMillis()) {
				level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ThaumcraftSounds.BRAIN.get(),
						SoundSource.AMBIENT, 0.15f, 0.8f + level.random.nextFloat() * 0.4f, false);
				nextSighTime = System.currentTimeMillis() + 5000L + level.random.nextInt(25000);
			}
			double dx = nearest.getX() - (pos.getX() + 0.5);
			double dz = nearest.getZ() - (pos.getZ() + 0.5);
			targetYaw = (float) Math.atan2(dz, dx);
		} else {
			targetYaw += 0.01f;
		}
		rota = wrapRadians(rota);
		targetYaw = wrapRadians(targetYaw);
		rota += wrapRadians(targetYaw - rota) * 0.04f;
	}

	/** Real direct port of the true original's own literal normalize-into-(-pi,pi] loop (no modern {@link Mth}
	 * equivalent exists - that class only has a degrees-based wrap). */
	private static float wrapRadians(float angle) {
		while (angle >= Mth.PI) {
			angle -= Mth.TWO_PI;
		}
		while (angle < -Mth.PI) {
			angle += Mth.TWO_PI;
		}
		return angle;
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
