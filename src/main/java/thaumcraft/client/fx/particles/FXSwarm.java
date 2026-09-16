package thaumcraft.client.fx.particles;

import java.util.ArrayDeque;
import java.util.Deque;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import thaumcraft.Thaumcraft;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Real modern equivalent of the true original's own {@code FXSwarm} - a small, real, steering "insect" that
 * orbits {@link thaumcraft.common.entities.monster.tainted.EntityTaintSwarm}, restored 2026-09-16 (see that
 * entity's own doc comment/{@code aiStep} for the real client-only maintenance loop - up to 30 real particles,
 * no packet needed at all since everything here is derived purely from the entity's own already-synced
 * position/state, matching the true original's own real client-only field shape).
 *
 * <p>Single billboarded quad, same real "extend {@link SingleQuadParticle}" approach {@link FXBoreSparkle}
 * already established, on the same shared row of {@code particles.png} {@code FXBoreSparkle} uses (an 8-frame
 * flicker across columns 7-14, matching the true original's own real hardcoded UV math exactly).
 *
 * <p>Real distinctive behaviour, genuinely different from {@link FXBoreSparkle}'s own simple homing-vector
 * acceleration: this one steers via real clamped yaw/pitch rotation (classic "flying boid" turn-rate
 * smoothing, matching the true original's own real {@code faceEntity}/{@code updateRotation} exactly), orbits
 * just outside the target's own hitbox rather than converging on it, and keeps flying/falling for a real
 * 50-tick fade-out once the target dies rather than despawning instantly. A real, shared 3-buzz sound cooldown
 * (matching the true original's own static {@code buzzcount} list) keeps overlapping swarms from all buzzing
 * at once.
 */
public class FXSwarm extends SingleQuadParticle {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final float CELL = 1.0f / 64.0f;
	private static final float ROW_V = 4.0f * CELL;

	/** Real, shared cooldown matching the true original's own static {@code buzzcount} - caps how many
	 * overlapping swarms can buzz their fly sound at once (at most 3 real pending buzzes tracked). */
	private static final Deque<Long> BUZZ_COUNT = new ArrayDeque<>();

	public static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
		@Nullable
		@Override
		public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
			RenderSystem.setShader(GameRenderer::getParticleShader);
			RenderSystem.setShaderTexture(0, TEXTURE);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			RenderSystem.depthMask(false);
			return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
		}

		@Override
		public String toString() {
			return "thaumcraft:swarm";
		}
	};

	private final Entity target;
	private final float turnSpeed;
	private final float speed;
	private int deathTimer;
	private float rotYaw, rotPitch;
	private final float spawnR, spawnG, spawnB;

	public FXSwarm(ClientLevel level, double x, double y, double z, Entity target, float r, float g, float b, float speed, float turnSpeed, float gravity) {
		super(level, x, y, z);
		this.rCol = r;
		this.gCol = g;
		this.bCol = b;
		this.spawnR = r;
		this.spawnG = g;
		this.spawnB = b;
		this.alpha = 1.0f;
		this.quadSize = 0.1f * (random.nextFloat() * 0.5f + 1.0f);
		this.hasPhysics = false;
		this.target = target;
		this.speed = speed;
		this.turnSpeed = turnSpeed;
		this.gravity = gravity;

		float jitter = 0.2f;
		this.xd = (random.nextFloat() - random.nextFloat()) * jitter;
		this.yd = (random.nextFloat() - random.nextFloat()) * jitter;
		this.zd = (random.nextFloat() - random.nextFloat()) * jitter;
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		age++;

		boolean targetGone = target == null || !target.isAlive() || (target instanceof LivingEntity le && le.deathTime > 0);
		if (targetGone) {
			deathTimer++;
			xd *= 0.9;
			zd *= 0.9;
			yd -= gravity / 2.0f;
			if (deathTimer > 50) {
				remove();
				return;
			}
		} else {
			yd += gravity;
		}

		move(xd, yd, zd);
		xd *= 0.985;
		yd *= 0.985;
		zd *= 0.985;

		this.alpha = Mth.clamp((50.0f - deathTimer) / 50.0f, 0.0f, 1.0f);
		boolean hurt = !targetGone && target instanceof LivingEntity le && le.hurtTime > 0;
		float dd = hurt ? 2.0f : 1.0f;
		this.rCol = spawnR;
		this.gCol = spawnG / dd;
		this.bCol = spawnB / dd;

		if (!targetGone) {
			double distSq = (x - target.getX()) * (x - target.getX()) + (y - target.getY()) * (y - target.getY()) + (z - target.getZ()) * (z - target.getZ());
			float halfTurn = turnSpeed / 2.0f;
			int turnRange = Math.max(1, (int) halfTurn);
			if (distSq > target.getBbWidth() && !hurt) {
				faceEntity(target, halfTurn + random.nextInt(turnRange), halfTurn + random.nextInt(turnRange));
			} else {
				faceEntity(target, -(halfTurn + random.nextInt(turnRange)), -(halfTurn + random.nextInt(turnRange)));
			}
			double dx = -Mth.sin(rotYaw / 180.0f * (float) Math.PI) * Mth.cos(rotPitch / 180.0f * (float) Math.PI);
			double dz = Mth.cos(rotYaw / 180.0f * (float) Math.PI) * Mth.cos(rotPitch / 180.0f * (float) Math.PI);
			double dy = -Mth.sin(rotPitch / 180.0f * (float) Math.PI);
			setHeading(dx, dy, dz, speed, 15.0f);
		}

		if (!targetGone && BUZZ_COUNT.size() < 3 && random.nextInt(50) == 0) {
			level.playLocalSound(x, y, z, ThaumcraftSounds.FLY.get(), SoundSource.HOSTILE, 0.03f, 0.5f + random.nextFloat() * 0.4f, false);
			BUZZ_COUNT.addLast(System.nanoTime() + 1_500_000L);
		}
		if (BUZZ_COUNT.size() >= 3 && BUZZ_COUNT.peekFirst() < System.nanoTime()) {
			BUZZ_COUNT.pollFirst();
		}
	}

	private void faceEntity(Entity other, float yawRate, float pitchRate) {
		double dx = other.getX() - x;
		double dz = other.getZ() - z;
		double dy = (other.getBoundingBox().minY + other.getBoundingBox().maxY) / 2.0 - y;
		double horiz = Math.sqrt(dx * dx + dz * dz);
		float wantYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		float wantPitch = (float) -(Math.atan2(dy, horiz) * 180.0 / Math.PI);
		rotPitch = updateRotation(rotPitch, wantPitch, pitchRate);
		rotYaw = updateRotation(rotYaw, wantYaw, yawRate);
	}

	private static float updateRotation(float from, float to, float maxDelta) {
		float delta = Mth.wrapDegrees(to - from);
		if (delta > maxDelta) delta = maxDelta;
		if (delta < -maxDelta) delta = -maxDelta;
		return from + delta;
	}

	private void setHeading(double dx, double dy, double dz, float moveSpeed, float randomness) {
		double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
		dx /= len;
		dy /= len;
		dz /= len;
		dx += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * randomness;
		dy += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * randomness;
		dz += random.nextGaussian() * (random.nextBoolean() ? -1 : 1) * 0.0075 * randomness;
		xd = dx * moveSpeed;
		yd = dy * moveSpeed;
		zd = dz * moveSpeed;
	}

	@Override
	public float getQuadSize(float scaleFactor) {
		float bob = Mth.sin(age / 3.0f) * 0.25f + 1.0f;
		return quadSize * bob;
	}

	@Override
	protected float getU0() {
		int frame = 7 + age % 8;
		return frame * CELL;
	}

	@Override
	protected float getU1() {
		return getU0() + CELL;
	}

	@Override
	protected float getV0() {
		return ROW_V;
	}

	@Override
	protected float getV1() {
		return ROW_V + CELL;
	}

	@Override
	public ParticleRenderType getRenderType() {
		return RENDER_TYPE;
	}
}
