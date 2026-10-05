package thaumcraft.client.fx.particles;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Real modern equivalent of the true original's own {@code FXBoreParticles} - real chunks of the block being
 * mined, homing toward {@link thaumcraft.common.entities.construct.EntityArcaneBore}, restored 2026-09-15
 * (see {@code ClientFxHandlers#handleBoreDig}). Real 3-in-4-chance sibling of {@link FXBoreSparkle} (matches
 * the true original's own real {@code boreDigFx} weighting exactly).
 *
 * <p>Extends vanilla's own real {@link TerrainParticle} rather than hand-rolling the true original's own
 * real "look up this block's particle icon, tint it, jitter the UV within it" logic from scratch - vanilla
 * already does exactly that (down to the same real 4x4 UV sub-cell jitter and block-colour tinting), so only
 * the real homing motion (identical formula to {@link FXBoreSparkle} - the true original itself duplicates
 * this exact code between the two classes, not shared there either) needs a real override.
 *
 * <p>The true original's own second constructor (spawning from a dropped item's own icon rather than a
 * block's) is restored too, 2026-09-17 - see {@link FXBoreParticlesItem}'s own doc comment for why it's a
 * real separate class here rather than a second constructor on this one.
 */
public class FXBoreParticles extends TerrainParticle {

	@Nullable
	private Entity target;
	private double targetX, targetY, targetZ;

	public FXBoreParticles(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, BlockState state) {
		super(level, x, y, z, 0.0, 0.0, 0.0, state);
		this.rCol = 0.6f;
		this.gCol = 0.6f;
		this.bCol = 0.6f;
		this.quadSize = 0.1f * (random.nextFloat() * 0.3f + 0.4f); // the original's half-width, 0.1 * its 0.4-0.7 scale
		this.hasPhysics = false;

		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		double dx = tx - x, dy = ty - y, dz = tz - z;
		int base = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0f));
		setLifetime(base / 2 + random.nextInt(base));

		float jitter = 0.01f;
		this.xd = random.nextGaussian() * jitter;
		this.yd = random.nextGaussian() * jitter;
		this.zd = random.nextGaussian() * jitter;
	}

	public void setTarget(Entity target) {
		this.target = target;
	}

	/** Public wrapper for {@link #setAlpha} - {@code protected} on the base {@link
	 * net.minecraft.client.particle.Particle}, unreachable from {@code ClientFxHandlers}' own different
	 * package otherwise. */
	public FXBoreParticles setAlphaF(float value) {
		setAlpha(value);
		return this;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (target != null) {
			targetX = target.getX();
			targetY = target.getY() + target.getEyeHeight();
			targetZ = target.getZ();
		}
		if (age++ >= lifetime
				|| (Mth.floor(x) == Mth.floor(targetX) && Mth.floor(y) == Mth.floor(targetY) && Mth.floor(z) == Mth.floor(targetZ))) {
			remove();
			return;
		}
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.95;
		zd *= 0.985;

		double dx = targetX - x, dy = targetY - y, dz = targetZ - z;
		double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double clamp = Math.min(0.25, dist / 15.0);
		if (dist < 2.0) {
			quadSize *= 0.9f;
		}
		dx /= dist;
		dy /= dist;
		dz /= dist;
		xd += dx * clamp;
		yd += dy * clamp;
		zd += dz * clamp;
		xd = Mth.clamp(xd, -clamp, clamp);
		yd = Mth.clamp(yd, -clamp, clamp);
		zd = Mth.clamp(zd, -clamp, clamp);
		xd += random.nextGaussian() * 0.005;
		yd += random.nextGaussian() * 0.005;
		zd += random.nextGaussian() * 0.005;
	}
}
