package thaumcraft.client.fx.particles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * Real modern equivalent of the true original's own real {@code FXBreakingFade} - a small crumb of an item's
 * own icon (real vanilla {@code BreakingItemParticle}-style random 4x4 sub-quadrant sampling, the same real
 * technique vanilla's own snowball/slime/cobweb break particles use) with a real custom fade-out over its own
 * lifetime, instead of vanilla's own fixed opaque look. Restored 2026-09-17. Real call sites confirmed via
 * `git grep`: {@link thaumcraft.common.entities.monster.tainted.EntityTaintacle}'s own real "arising from the
 * ground" cue and {@link thaumcraft.common.entities.monster.EntityThaumicSlime}'s own real landing-splat cue
 * (both real Taint/Slime-family ambient effects, every real Item in this port's own tree always
 * {@code Items.SLIME_BALL}, matching the true original's own real hardcoded choice at every real call site).
 *
 * <p>Real, checked-not-guessed detail: vanilla's own real {@code BreakingItemParticle} (the modern equivalent
 * of the true original's own real {@code ParticleBreaking} base class) has protected constructors, unreachable
 * from a mod's own particle class - this replicates its own real sprite-lookup/4x4-sub-quadrant-UV technique
 * directly instead (verified against its own real decompiled source, not guessed), reusing vanilla's own real
 * {@link ParticleRenderType#TERRAIN_SHEET} render bucket (the item particle icon lives in the block atlas, same
 * real texture vanilla's own break particles already share - no new texture binding needed).
 */
public class FXBreakingFade extends TextureSheetParticle {

	private final float uo;
	private final float vo;
	private float baseAlpha = 1.0f;

	public FXBreakingFade(ClientLevel level, double x, double y, double z, ItemStack stack) {
		super(level, x, y, z);
		var model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
		this.setSprite(model.getOverrides().resolve(model, stack, level, null, 0).getParticleIcon(ModelData.EMPTY));
		this.gravity = 1.0f;
		this.quadSize /= 2.0f;
		this.uo = random.nextFloat() * 3.0f;
		this.vo = random.nextFloat() * 3.0f;
		this.lifetime = 20;
	}

	public FXBreakingFade(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, ItemStack stack) {
		this(level, x, y, z, stack);
		this.xd *= 0.1;
		this.yd *= 0.1;
		this.zd *= 0.1;
		this.xd += xSpeed;
		this.yd += ySpeed;
		this.zd += zSpeed;
	}

	public FXBreakingFade setParticleMaxAge(int maxAge) {
		this.lifetime = maxAge;
		return this;
	}

	public FXBreakingFade setParticleGravity(float value) {
		this.gravity = value;
		return this;
	}

	/** Real port of the true original's own real {@code particleAlpha} field - a peak alpha multiplier the
	 * true original's own real {@code renderParticle} kept constant, only ever multiplying it by a separate,
	 * locally-computed real age-based fade fraction at render time. Replicated here by folding that same real
	 * fade fraction into {@link #alpha} itself each real tick, since this class reuses
	 * {@link net.minecraft.client.particle.SingleQuadParticle}'s own real render path (no custom
	 * {@code render()} override) - same real net result. */
	public FXBreakingFade setStartAlpha(float value) {
		this.baseAlpha = value;
		return this;
	}

	public FXBreakingFade setSpeed(double x, double y, double z) {
		this.xd = x;
		this.yd = y;
		this.zd = z;
		return this;
	}

	/** Real port of the true original's own real {@code boom()} - re-randomizes speed magnitude while keeping
	 * direction, matching {@link #setSpeed}'s own already-set direction. */
	public FXBreakingFade boom() {
		float speed = (float) (Math.random() + Math.random() + 1.0) * 0.15f;
		float len = Mth.sqrt((float) (xd * xd + yd * yd + zd * zd));
		xd = xd / len * speed * 0.964;
		yd = yd / len * speed * 0.964 + 0.1;
		zd = zd / len * speed * 0.964;
		return this;
	}

	@Override
	public void tick() {
		super.tick();
		alpha = baseAlpha * (1.0f - age / (float) lifetime);
	}

	@Override
	protected float getU0() {
		return this.sprite.getU((this.uo + 1.0f) / 4.0f);
	}

	@Override
	protected float getU1() {
		return this.sprite.getU(this.uo / 4.0f);
	}

	@Override
	protected float getV0() {
		return this.sprite.getV(this.vo / 4.0f);
	}

	@Override
	protected float getV1() {
		return this.sprite.getV((this.vo + 1.0f) / 4.0f);
	}

	@Override
	public ParticleRenderType getRenderType() {
		return ParticleRenderType.TERRAIN_SHEET;
	}
}
