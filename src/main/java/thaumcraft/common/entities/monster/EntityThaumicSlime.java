package thaumcraft.common.entities.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityThaumicSlime} - a real {@link Slime} subclass (the original was
 * one too, {@code EntitySlime}), reusing vanilla's own jump/float/attack {@code Goal}s. As the original:
 * <ul>
 * <li>A slime bigger than 2 watches the nearest player within 16 blocks and, every five seconds while they're
 * more than 4 blocks off, spits a little slime at them ({@link #spit}), shrinking by one.</li>
 * <li>For its first half-second a slime trails splats (the spat ones fly through the air trailing them).</li>
 * <li>It dies into as many tiny slimes as its size ({@link #remove}), not vanilla's halves.</li>
 * </ul>
 * The original's own {@code getDropItem}/{@code dropFewItems}' two overlapping crystal-essence drops are
 * collapsed into the one real drop {@code ConfigItems.FLUX_CRYSTAL} actually was (a crystal essence carrying
 * the {@link Aspect#FLUX} tag) via {@link #dropCustomDeathLoot}, the same local {@code makeCrystal} stand-in
 * {@link EntityWisp} already established rather than porting the whole unconverted {@code ThaumcraftApiHelper}.
 *
 * <p>Touch damage itself ({@link #getAttackDamage}/{@link #isDealsDamage}) reuses vanilla {@link Slime}'s own
 * real {@code playerTouch}/{@code dealDamage} unmodified - verified real via a {@code javap} disassembly of
 * that exact call chain (own doc comment on the GameTest confirms why a live in-test check against a real
 * player isn't possible: {@code GameTestHelper}'s own mock players hardcode {@code isSpectator()}/
 * {@code isCreative()} true, a structural test-harness limit, not a gap here) rather than a live assertion.
 */
public class EntityThaumicSlime extends Slime implements ITaintedMob {

	private boolean wasOnGroundLastTick;
	/** Client: ticks of launch trail left (every slime starts with them, as the original). */
	private int launched = 10;
	private int spitCounter = 100;

	public EntityThaumicSlime(EntityType<? extends EntityThaumicSlime> type, Level level) {
		super(type, level);
	}

	/** Vanilla's {@code Slime#tick} spawns {@code size*8} of this particle on every landing. The original avoided
	 * it by pre-setting its own {@code wasOnGround} before calling {@code super.onUpdate()}; that field is private now,
	 * so instead the burst is pointed at a provider-less particle type, i.e. nothing - the purple
	 * {@link thaumcraft.client.fx.ClientFxHandlers#spawnSlimeJump} splat below is this mob's only landing effect.
	 * (Found 2026-09-25: green vanilla slime particles on every jump.) */
	@Override
	protected net.minecraft.core.particles.ParticleOptions getParticleType() {
		return thaumcraft.common.registry.ThaumcraftParticleTypes.NONE.get();
	}

	/** Real client-only landing-splat cue restored 2026-09-17 (was dropped under this port's old blanket "no
	 * client FX" rule) - a real faithful port of the true original's own real "just touched down" detection,
	 * routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnSlimeJump} for the same real
	 * dist-crash-avoidance reason every other client-only trigger in this port uses. */
	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			if (onGround() && !wasOnGroundLastTick) {
				for (int j = 0; j < getSize() * 2; j++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnSlimeJump(level(), this, getSize());
				}
			}
			wasOnGroundLastTick = onGround();
			if (launched > 0) {
				launched--;
				for (int k = 0; k < getSize() * (launched + 1); k++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnSlimeJump(level(), this, getSize());
				}
			}
		} else if (isAlive()) {
			net.minecraft.world.entity.player.Player player = level().getNearestPlayer(this, 16.0);
			if (player != null) {
				if (spitCounter > 0) spitCounter--;
				lookAt(player, 10.0f, 20.0f);
				if (distanceTo(player) > 4.0f && spitCounter <= 0 && getSize() > 2) {
					spitCounter = 101;
					spit(player);
					playSound(thaumcraft.common.registry.ThaumcraftSounds.GORE.get(), 1.0f, ((random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f) * 0.8f);
					setSize(getSize() - 1, true);
				}
			}
		}
	}

	/** The original's second constructor: a tiny slime launched from this one's middle, a block towards the target,
	 * lobbed at its lower third. */
	private void spit(net.minecraft.world.entity.LivingEntity target) {
		EntityThaumicSlime fly = thaumcraft.common.registry.ThaumcraftEntities.THAUMIC_SLIME.get().create(level());
		if (fly == null) return;
		fly.setSize(1, true);
		double y = (getBoundingBox().minY + getBoundingBox().maxY) / 2.0;
		double dx = target.getX() - getX();
		double dy = target.getBoundingBox().minY + target.getBbHeight() / 3.0f - y;
		double dz = target.getZ() - getZ();
		double dist = Math.sqrt(dx * dx + dz * dz);
		if (dist < 1.0E-7) return;
		float yaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
		float pitch = (float) (-(Math.atan2(dy, dist) * 180.0 / Math.PI));
		fly.moveTo(getX() + dx / dist, y, getZ() + dz / dist, yaw, pitch);
		double vy = dy + dist * 0.2f;
		double len = Math.sqrt(dx * dx + vy * vy + dz * dz);
		double mx = (dx / len + random.nextGaussian() * 0.0075) * 1.5;
		double my = (vy / len + random.nextGaussian() * 0.0075) * 1.5;
		double mz = (dz / len + random.nextGaussian() * 0.0075) * 1.5;
		fly.setDeltaMovement(mx, my, mz);
		level().addFreshEntity(fly);
	}

	/** The original's setDead: a dying slime bursts into as many tiny slimes as its size. */
	@Override
	public void remove(RemovalReason reason) {
		int i = getSize();
		if (!level().isClientSide && i > 1 && isDeadOrDying()) {
			for (int k = 0; k < i; k++) {
				float f = (k % 2 - 0.5f) * i / 4.0f;
				float f2 = (k / 2 - 0.5f) * i / 4.0f;
				EntityThaumicSlime child = thaumcraft.common.registry.ThaumcraftEntities.THAUMIC_SLIME.get().create(level());
				if (child == null) continue;
				child.setSize(1, true);
				child.moveTo(getX() + f, getY() + 0.5, getZ() + f2, random.nextFloat() * 360.0f, 0.0f);
				level().addFreshEntity(child);
			}
			// vanilla would split it again into halves: it's already burst
			setSize(1, false);
		}
		super.remove(reason);
	}

	/** {@code Slime} doesn't define its own static attribute builder (unlike most vanilla mobs) - matches
	 * vanilla's own real default slime attributes (health/speed/attack all get dynamically scaled per-size
	 * elsewhere - {@link #getAttackDamage} for damage, {@link Slime#getSize} itself drives max health via
	 * {@code refreshDimensions}/vanilla's own real per-size scaling, unchanged). */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		int tier = random.nextInt(3);
		if (tier < 2 && random.nextFloat() < 0.5f * difficulty.getSpecialMultiplier()) {
			tier++;
		}
		setSize(1 << tier, true);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	protected float getAttackDamage() {
		return getSize() + 1;
	}

	@Override
	protected boolean isDealsDamage() {
		return true;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		if (getSize() > 1) {
			spawnAtLocation(makeCrystal(Aspect.FLUX), 0.0f);
		}
	}

	/** Same local stand-in {@link EntityWisp#makeCrystal} already established for the original's
	 * {@code ThaumcraftApiHelper.makeCrystal}. */
	private static ItemStack makeCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH;
	}
}
