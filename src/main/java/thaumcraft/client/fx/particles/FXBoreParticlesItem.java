package thaumcraft.client.fx.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Real modern equivalent of the true original's own real {@code FXBoreParticles}' second, item-icon
 * constructor - restored 2026-09-17 alongside {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix}'s
 * own real "a pedestal's own item is being consumed toward the current craft" cue (see
 * {@link thaumcraft.client.fx.ClientFxHandlers#handleInfusionSource}). {@link FXBoreParticles}'s own doc
 * comment already flagged this exact gap by name ("add it if/when that lands").
 *
 * <p>A real separate class rather than a second constructor on {@link FXBoreParticles} itself - that class
 * extends vanilla's own real {@link net.minecraft.client.particle.TerrainParticle} specifically to get its
 * real block-particle-icon lookup for free, which a real item-icon-sourced particle has no use for and can't
 * share a constructor chain with (Java's {@code super()} rules). The true original's own real class did have
 * both on one class, but its own real {@code onUpdate} was ALSO independently duplicated between the two
 * (see that class's own doc comment) rather than shared - so keeping the real homing-physics duplicated here
 * too, instead of restructuring the already-real, already-verified {@link FXBoreParticles}, matches the true
 * original's own real shape rather than fighting it.
 *
 * <p>Real sprite lookup reuses the exact same real technique {@link FXBreakingFade} already established this
 * session (vanilla's own real item-model particle-icon resolution chain, verified against
 * {@code BreakingItemParticle}'s own real decompiled source) rather than the true original's own now-removed
 * {@code ItemModelMesher#getParticleIcon} call.
 */
public class FXBoreParticlesItem extends TextureSheetParticle {

	private double targetX;
	private double targetY;
	private double targetZ;

	public FXBoreParticlesItem(ClientLevel level, double x, double y, double z, double tx, double ty, double tz, ItemStack stack) {
		super(level, x, y, z);
		var model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
		this.setSprite(model.getOverrides().resolve(model, stack, level, null, 0).getParticleIcon(ModelData.EMPTY));

		this.rCol = 0.6f;
		this.gCol = 0.6f;
		this.bCol = 0.6f;
		this.quadSize = 0.1f * (random.nextFloat() * 0.3f + 0.4f); // the original's half-width, 0.1 * its 0.4-0.7 scale
		this.hasPhysics = false;

		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		double dx = tx - x;
		double dy = ty - y;
		double dz = tz - z;
		int base = Math.max(1, (int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 10.0f));
		setLifetime(base / 2 + random.nextInt(base));

		float jitter = 0.01f;
		this.xd = random.nextGaussian() * jitter;
		this.yd = random.nextGaussian() * jitter;
		this.zd = random.nextGaussian() * jitter;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime
				|| (Mth.floor(x) == Mth.floor(targetX) && Mth.floor(y) == Mth.floor(targetY) && Mth.floor(z) == Mth.floor(targetZ))) {
			remove();
			return;
		}
		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.95;
		zd *= 0.985;

		double dx = targetX - x;
		double dy = targetY - y;
		double dz = targetZ - z;
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

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}
}
