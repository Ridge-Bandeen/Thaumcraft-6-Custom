package thaumcraft.common.entities.monster.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.projectile.EntityGolemOrb;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchGolem} - the Eldritch dimension's second boss. Real {@link
 * EntityThaumcraftBoss} subclass (see that class's own doc comment for the real boss-bar/enrage/self-heal
 * mechanics it provides). Real slam melee (10-tick cooldown, 75% of Attack Damage, matches the original's own
 * {@code attackEntityAsMob} exactly) via a {@link #hurtTimer} gate on {@link #doHurtTarget}, driven by vanilla
 * {@link MeleeAttackGoal} for the approach/swing timing. Real Iron Golem hurt/death/step sounds reused, same
 * as the original's own choice.
 *
 * Real "headless" second phase - the actual mechanical heart of this boss, faithfully kept: the first hit
 * that would otherwise kill it (and only the first - {@link #isHeadless()} gates it) is intercepted in
 * {@link #hurt} instead of landing: a real explosion goes off at head height (block damage disabled, matches
 * the original's own {@code world.createExplosion(..., false)}), the golem survives at 1 HP, goes headless,
 * gets a real temporary invulnerability window (reuses {@link EntityThaumcraftBoss#setSpawnTimer}), and gains
 * a real ranged attack from then on - a homing {@link EntityGolemOrb} (matches the original's own
 * {@code attackEntityWithRangedAttack} - same projectile type, same 0.66/5.0 shoot parameters), gated by a
 * real {@link #beamCharge} that charges up over time and is spent per shot (matches the original's own
 * charge-then-fire pacing).
 *
 * Real champion-mod name suffix, restored 2026-09-15 (found while re-checking the other 2 bosses' own
 * champion-mod restoration against the true original directly - this tie-in was missed on the first pass of
 * that correction, since nothing had flagged it): {@link #onChampionRolled} reformats this boss's name to
 * "&lt;modifier&gt; Eldritch Construct", matching the true original's own {@code entity.EldritchGolem.name}/
 * {@code .name.custom} ("%s Eldritch Construct") format strings exactly - no title mechanic here (unlike
 * {@link EntityCultistLeader}/{@link EntityEldritchWarden}), and no format-type bug either (a single
 * {@code %s} against the real {@code String} mod name lines up correctly, unlike the other two).
 *
 * Real arc-lightning "search for a ground target" client FX, restored 2026-09-15 (the original's own {@code
 * handleStatusUpdate} case 19 / {@code onUpdate}, purely decorative) - see {@link #handleEntityEvent}/{@link
 * #aiStep} and {@link thaumcraft.client.fx.beams.FXArc}'s own doc comment for the real modern rendering. No
 * "destroys the block it's standing on" griefing mechanic (a minor, real-risk-to-player-builds behavior, not
 * central to the fight).
 *
 * Real headless-vs-normal visual distinction and real attack-swing arm animation, both restored 2026-09-24
 * alongside the real mesh port ({@link thaumcraft.client.renderers.models.entity.ModelEldritchGolem}). The
 * attack swing needed a real, previously-unwired client entity-event ({@link #ATTACK_SWING_EVENT}, matching
 * the true original's own real {@code handleStatusUpdate} case 4 byte id exactly) broadcast from {@link
 * #doHurtTarget} - {@link #hurtTimer} already existed as this port's own real server-side attack-cooldown
 * gate, but nothing client-side ever needed its value before this mesh existed, so the matching real client
 * mirror was never wired. {@link net.minecraft.sounds.SoundEvents#IRON_GOLEM_ATTACK} (matching the true
 * original's own real sound choice) is restored alongside it, played directly server-side rather than through
 * the entity-event (modern {@code playSound} on a server-side entity already broadcasts to nearby clients on
 * its own - no need for the true original's own real client-side-triggered-sound workaround, which was itself
 * only needed for a 1.12.2-specific timing reason).
 */
public class EntityEldritchGolem extends EntityThaumcraftBoss implements RangedAttackMob, IEldritchMob {

	private static final EntityDataAccessor<Boolean> DATA_HEADLESS =
			SynchedEntityData.defineId(EntityEldritchGolem.class, EntityDataSerializers.BOOLEAN);

	private static final byte ATTACK_SWING_EVENT = 4;

	/** Real server-side attack-cooldown gate (see {@link #doHurtTarget}), and - since 2026-09-24 - also this
	 * port's own real client-mirrored attack-swing countdown (set from {@link #handleEntityEvent}, read by
	 * {@link #getHurtTimer} for {@link thaumcraft.client.renderers.models.entity.ModelEldritchGolem}'s own real
	 * arm animation). The server's own copy and the client's own copy are genuinely separate {@code Entity}
	 * instances (one per side, like every other client-only visual-state field this port uses), so sharing one
	 * field name causes no real conflict. */
	private int hurtTimer;
	private int beamCharge;

	/** Real, purely decorative, client-only arc-lightning "search for a ground target" state - restored
	 * 2026-09-15. See the class doc comment and {@link #handleEntityEvent}/{@link #aiStep} for the full real
	 * mechanic. No NBT/sync needed - {@code ax}/{@code ay}/{@code az} are picked randomly client-side off a
	 * real {@code byte 19} broadcast, never server-authoritative, matching the true original's own real
	 * {@code handleStatusUpdate} shape exactly. */
	private int arcing;
	private double ax, ay, az;

	public EntityEldritchGolem(EntityType<? extends EntityEldritchGolem> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityThaumcraftBoss.createAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, 6.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HEADLESS, false);
	}

	public boolean isHeadless() {
		return entityData.get(DATA_HEADLESS);
	}

	@Override
	public void onChampionRolled(net.minecraft.network.chat.Component modName) {
		setCustomName(net.minecraft.network.chat.Component.translatable("entity.thaumcraft.eldritch_golem.name.custom", modName));
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 24.0f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.IRON_GOLEM_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.IRON_GOLEM_DEATH;
	}

	@Override
	protected void playStepSound(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		playSound(SoundEvents.IRON_GOLEM_STEP, 1.0f, 1.0f);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide && !isHeadless() && amount > getHealth() && !isInvulnerableTo(source)) {
			entityData.set(DATA_HEADLESS, true);
			setSpawnTimer(100);
			setHealth(1.0f);
			double xx = Math.cos(getYRot() % 360.0f / 180.0f * Math.PI) * 0.75;
			double zz = Math.sin(getYRot() % 360.0f / 180.0f * Math.PI) * 0.75;
			level().explode(this, getX() + xx, getY() + getEyeHeight(), getZ() + zz, 2.0f, false, Level.ExplosionInteraction.NONE);
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (hurtTimer > 0) return false;
		hurtTimer = 10;
		level().broadcastEntityEvent(this, ATTACK_SWING_EVENT);
		playSound(SoundEvents.IRON_GOLEM_ATTACK, 1.0f, 1.0f);
		var atk = getAttribute(Attributes.ATTACK_DAMAGE);
		float damage = atk != null ? (float) atk.getValue() * 0.75f : 7.5f;
		boolean hit = target.hurt(level().damageSources().mobAttack(this), damage);
		if (hit && target instanceof LivingEntity living) {
			living.push(0.0, 0.2, 0.0);
			if (isHeadless()) {
				living.push(-Mth.sin(getYRot() * (float) Math.PI / 180.0f) * 1.5, 0.1, Mth.cos(getYRot() * (float) Math.PI / 180.0f) * 1.5);
			}
		}
		return hit;
	}

	/** Real 100-tick materialising window on initial spawn (invulnerable, see {@link EntityThaumcraftBoss#isInvulnerableTo}) -
	 * the original set {@code spawnTimer = 100} in its own {@code onInitialSpawn}. Added 2026-09-25 alongside the
	 * matching spawn-window heal in {@link #aiStep}; both were missing. */
	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnType,
			@org.jetbrains.annotations.Nullable net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
		setSpawnTimer(100);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public void aiStep() {
		// Real original behaviour (its own onUpdate): +2 HP every tick for as long as the spawn timer runs - the
		// materialising window on spawn AND the 100-tick regrowth after losing its head. Missing before
		// 2026-09-25, which left the post-"death" recovery at the base boss trickle (+1 HP per 30 ticks).
		if (!level().isClientSide && getSpawnTimer() > 0) {
			heal(2.0f);
		}
		super.aiStep();
		if (hurtTimer > 0) {
			hurtTimer--;
		}
		if (!level().isClientSide) {
			if (isHeadless() && beamCharge < 150) {
				beamCharge++;
				level().broadcastEntityEvent(this, (byte) 19);
			}
		} else if (arcing > 0) {
			thaumcraft.client.fx.ClientFxHandlers.spawnGolemArc(this, ax, ay, az, arcing);
			arcing--;
		}
	}

	/** Real client-side interpreter of the {@code byte 19} broadcast {@link #aiStep} fires every tick while
	 * charging - see the class doc comment. Matches the true original's own {@code handleStatusUpdate} case
	 * 19, including a real bug faithfully reproduced (same precedent {@code FocusEffectExchange}'s own
	 * operator-precedence bug already established - keep real original behavior, don't silently improve it):
	 * the original's own downward search loop constructs {@code bp} once, then never reassigns it inside the
	 * loop body despite decrementing its own counter, so the loop's own "probe up to 5 blocks downward for
	 * solid ground" never actually re-checks a lower block at all - it only ever tests the single position at
	 * the golem's own exact standing height. In practice this only picks a real target when a nearby random
	 * point (2-4 blocks out) happens to have solid ground with open air above it AT the golem's own foot
	 * height (a real ledge/step, not flat ground it's standing in the middle of) - a real, if narrow,
	 * occasional flourish rather than a guaranteed one, matching the true original's own real rarity. */
	@Override
	public void handleEntityEvent(byte id) {
		if (id == ATTACK_SWING_EVENT) {
			hurtTimer = 10;
		} else if (id == 19) {
			if (arcing == 0) {
				float radius = 2.0f + random.nextFloat() * 2.0f;
				double radians = Math.toRadians(random.nextInt(360));
				double deltaX = radius * Math.cos(radians);
				double deltaZ = radius * Math.sin(radians);
				int bx = net.minecraft.util.Mth.floor(getX() + deltaX);
				int by = net.minecraft.util.Mth.floor(getY());
				int bz = net.minecraft.util.Mth.floor(getZ() + deltaZ);
				net.minecraft.core.BlockPos bp = new net.minecraft.core.BlockPos(bx, by, bz);
				for (int c = 0; c < 5 && level().isEmptyBlock(bp); c++, by--) {
					// Faithfully empty - see this method's own doc comment for why bp is never reassigned here.
				}
				if (level().isEmptyBlock(bp.above()) && !level().isEmptyBlock(bp)) {
					ax = bx;
					ay = by;
					az = bz;
					arcing = 8 + random.nextInt(5);
					playSound(thaumcraft.common.registry.ThaumcraftSounds.JACOBS.get(), 0.8f, 1.0f + (random.nextFloat() - random.nextFloat()) * 0.05f);
				}
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Test-only accessor, not part of the true original's own API - lets GameTest assert the real
	 * client-side arc-search outcome directly instead of only indirectly (e.g. via the sound it plays). */
	public int getArcingForTesting() {
		return arcing;
	}

	public int getHurtTimer() {
		return hurtTimer;
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (!isHeadless() || beamCharge <= 0 || !hasLineOfSight(target)) return;
		beamCharge -= 15 + random.nextInt(5);
		EntityGolemOrb blast = new EntityGolemOrb(this, target, false);
		double dx = target.getX() - getX();
		double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - getY();
		double dz = target.getZ() - getZ();
		Vec3 dir = new Vec3(dx, dy, dz).normalize();
		blast.shoot(dir.x, dir.y, dir.z, 0.66f, 5.0f);
		playSound(thaumcraft.common.registry.ThaumcraftSounds.EGATTACK.get(), 1.0f, 1.0f + random.nextFloat() * 0.1f);
		level().addFreshEntity(blast);
	}
}
