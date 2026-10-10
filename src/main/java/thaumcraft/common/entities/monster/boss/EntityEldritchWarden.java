package thaumcraft.common.entities.monster.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchWarden} - the Eldritch dimension's real boss. Real {@link
 * EntityThaumcraftBoss} subclass (see that class's own doc comment for the real boss-bar/enrage/self-heal
 * mechanics it provides). Shares {@link thaumcraft.common.entities.monster.EntityEldritchGuardian}'s ranged
 * attack shape (same real {@link
 * EntityEldritchOrb}, same real melee-range Wither/Warp "scream" fallback, boss-tier stronger: Wither +
 * Weakness both 20s, 3-5 Warp vs. the Guardian's Wither-only 20s/1-3 Warp) and its own real random-title
 * naming (one of the original's own 15 Lovecraftian names, matching the original's own base {@code
 * entity.EldritchWarden.name} format). Real champion-mod name suffix, restored 2026-09-15 via
 * {@link #onChampionRolled} - see {@link EntityCultistLeader}'s own doc comment for why this needed its own
 * hook rather than the generic champion naming, and for the real {@code %d}/{@code String} format-string bug
 * the true original's own {@code entity.EldritchWarden.name.custom} ("%s the %d") shares with the Praetor's,
 * fixed the same way here. Now really {@link IEldritchMob} again too - a real, separate bug found while
 * building the field-frenzy phase below:
 * this port's class had silently dropped that interface (the true original's own class declaration carries
 * it), which meant other Eldritch mobs' own real {@code isAlliedTo} checks never actually recognized the
 * Warden as one of their own.
 *
 * <p>Real "field frenzy" phase, restored 2026-09-15 now that {@link BlocksTC#effectSap} is real: a real
 * vanilla absorption shield ({@link #getAbsorptionAmount}, 66% of max health, real cap via a real
 * {@code Attributes.MAX_ABSORPTION} entry - modern vanilla already routes all incoming damage through
 * absorption automatically, no hand-rolled soak logic needed) regenerating +1 every 25 ticks; the FIRST real
 * hit that drops it to 0 triggers a real one-shot (never again per spawn, matching the original's own real
 * {@code fieldFrenzy} boolean latch) 150-tick invulnerable window ({@link #isInvulnerableTo}) plus a real
 * teleport to a random safe spot near its own real restricted home (set at spawn via {@link #restrictTo}),
 * then spirals a real expanding, thickening ring of {@code effect_sap} outward from itself every 10 ticks
 * while the counter runs 120 down to 0 (real radius {@code (150-counter)/8}, real angular density
 * {@code 180/(1+counter/8)} - both exact original formulas, producing the same real "sparse ring near the
 * boss, wide dense ring by the end" expansion), each placement also getting the original's own real explicit
 * 250-400-tick backup removal (see {@link thaumcraft.common.blocks.misc.BlockEffectSap}'s own doc comment
 * for why that's a real belt-and-suspenders on top of the block's own random-tick self-destruct). Separately,
 * every tick regardless of frenzy state, the Warden seeds real sap at up to 4 positions right around its own
 * feet - the original's own real constant "the ground near me is dangerous" tell. Each frenzy-ring placement
 * also has a real 30% chance of a real {@code PacketFXBlockArc} pulse from the new sap block back to the
 * Warden itself, restored 2026-09-15 alongside its real {@code zap} sound (was a vanilla
 * {@code WARDEN_SONIC_BOOM} stand-in) - this specific gap was never actually self-documented anywhere in
 * this class before, only found by cross-checking the true original directly.
 *
 * <p>Real, documented simplifications on this restored phase (none drop real player-visible content): the
 * original's own {@code updateAITasks} froze the goal selector entirely during frenzy (an implementation
 * detail of 1.12.2's own AI-task dispatch, not attempted here - the Warden keeps acting on its last real
 * decision instead of literally pausing goal re-evaluation); the absorption regen's own real
 * "not this same tick I just got hit" throttle isn't reproduced (a real, minor pacing nicety, not a visible
 * mechanic); the client-side portal-teleport particle trail is dropped, matching every other client-FX cut
 * this whole project has made consistently.
 *
 * <p>Real arm-lift swing animation, restored 2026-09-24 alongside the real mesh port - see {@link
 * thaumcraft.common.entities.monster.EntityEldritchGuardian}'s own identical fields/event pair for the real
 * shape (this class's own {@code armLiftL}/{@code armLiftR} are a genuinely separate pair of fields, not
 * shared with the Guardian, matching the true original's own real independent {@code EntityEldritchWarden}
 * class).
 *
 * <p>Real sonic-scream client FX restored 2026-09-17, same shape as {@link
 * thaumcraft.common.entities.monster.EntityEldritchGuardian}'s own doc comment describes (a plain vanilla
 * entity-event trigger, drawn from {@link thaumcraft.client.render.RenderEldritchWarden}'s own render call
 * via {@link thaumcraft.client.render.EldritchSonicFx}).
 */
public class EntityEldritchWarden extends EntityThaumcraftBoss implements RangedAttackMob, IEldritchMob {

	private static final String[] TITLES = { "Aphoom-Zhah", "Basatan", "Chaugnar Faugn", "Mnomquah", "Nyogtha",
			"Oorn", "Shaikorth", "Rhan-Tegoth", "Rhogog", "Shudde M'ell", "Vulthoom", "Yag-Kosha", "Yibb-Tstll",
			"Zathog", "Zushakon" };

	private static final EntityDataAccessor<Byte> DATA_TITLE =
			SynchedEntityData.defineId(EntityEldritchWarden.class, EntityDataSerializers.BYTE);

	/** 66% of {@link Attributes#MAX_HEALTH} (400) - the real absorption shield's own real cap, matching the
	 * true original's own {@code MAX_HEALTH * 0.66} exactly. */
	private static final float SHIELD_AMOUNT = 264.0f;

	/** Real one-shot "field frenzy" state - see this class's own doc comment. */
	private int fieldFrenzyCounter;
	private boolean fieldFrenzyTriggered;

	private static final byte ORB_LEFT_EVENT = 15;
	private static final byte ORB_RIGHT_EVENT = 16;
	private static final byte SONIC_EVENT = 17;
	/** The original's event 18: the client starts its own spawn timer (the Warden rising out of the ground). */
	private static final byte RISE_EVENT = 18;

	/** Client-only "sonic scream" trigger state - see {@link
	 * thaumcraft.common.entities.monster.EntityEldritchGuardian}'s own identical fields for the real shape. */
	private int sonicStartTick = Integer.MIN_VALUE;
	private int sonicDuration;
	private float sonicYaw;
	private float sonicPitch;

	/** Client-only arm-lift swing state, decayed in {@link #aiStep}. Never synced directly; set from
	 * {@link #handleEntityEvent}. */
	private float armLiftL;
	private float armLiftR;
	private boolean lastBlast;

	public EntityEldritchWarden(EntityType<? extends EntityEldritchWarden> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityThaumcraftBoss.createAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.MAX_ABSORPTION, SHIELD_AMOUNT);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TITLE, (byte) 0);
	}

	private String getTitle() {
		return TITLES[entityData.get(DATA_TITLE)];
	}

	private void setTitle(int title) {
		entityData.set(DATA_TITLE, (byte) title);
	}

	@Override
	public void onChampionRolled(Component modName) {
		setCustomName(Component.translatable("entity.thaumcraft.eldritch_warden.name.custom", getTitle(), modName));
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 20, 40, 24.0f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(5, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, EntityCultist.class, true));
	}

	@Override
	public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(net.minecraft.world.level.ServerLevelAccessor level,
			net.minecraft.world.DifficultyInstance difficulty, net.minecraft.world.entity.MobSpawnType spawnType,
			net.minecraft.world.entity.SpawnGroupData spawnGroupData) {
		setTitle(random.nextInt(TITLES.length));
		setCustomName(Component.translatable("entity.thaumcraft.eldritch_warden.name", getTitle()));
		setAbsorptionAmount(SHIELD_AMOUNT);
		restrictTo(blockPosition(), 32);
		setSpawnTimer(150); // as the original: 7.5 s rising out of the ground, invulnerable and still
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN) || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) {
			return false;
		}
		boolean hurt = super.hurt(source, amount);
		if (hurt && !level().isClientSide && !fieldFrenzyTriggered && getAbsorptionAmount() <= 0.0f) {
			fieldFrenzyTriggered = true;
			fieldFrenzyCounter = 150;
			if (level() instanceof ServerLevel serverLevel) {
				teleportHome(serverLevel);
			}
		}
		return hurt;
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return fieldFrenzyCounter > 0 || super.isInvulnerableTo(source);
	}

	/** Whether the real one-shot field-frenzy phase is currently active - real, minor test/UI hook, same
	 * shape as {@code TileTube#isSideOpen}/{@code getFacing} elsewhere in this project. */
	public boolean isInFieldFrenzy() {
		return fieldFrenzyCounter > 0;
	}

	@Override
	public void tick() {
		if (!level().isClientSide && getSpawnTimer() == 150) {
			level().broadcastEntityEvent(this, RISE_EVENT);
		}
		super.tick();
	}

	@Override
	public void aiStep() {
		super.aiStep();
		seedSapUnderfoot();
		if (!level().isClientSide) {
			if (tickCount % 25 == 0 && getAbsorptionAmount() < SHIELD_AMOUNT) {
				setAbsorptionAmount(getAbsorptionAmount() + 1.0f);
			}
			if (fieldFrenzyCounter > 0 && level() instanceof ServerLevel serverLevel) {
				performFieldFrenzy(serverLevel);
				fieldFrenzyCounter--;
			}
		} else {
			if (armLiftL > 0.0f) {
				armLiftL -= 0.05f;
			}
			if (armLiftR > 0.0f) {
				armLiftR -= 0.05f;
			}

			// Real client-only ambient particle trail restored 2026-09-17 (was dropped under this port's old
			// blanket "no client FX" rule) - the true original's own real constant per-tick wispFXEG dust
			// trail, routed through ClientFxHandlers#spawnWispEG for the same real dist-crash-avoidance reason
			// every other client-only trigger in this port uses.
			float x = (float) (getX() + (random.nextFloat() - random.nextFloat()) * 0.2f);
			float z = (float) (getZ() + (random.nextFloat() - random.nextFloat()) * 0.2f);
			thaumcraft.client.fx.ClientFxHandlers.spawnWispEG(level(), x, getY() + 0.25 * getBbHeight(), z, this);

			// the materialising smoke while the spawn timer runs (the client's started by the rise event, as the
			// original - not the client's own age, which restarts whenever the Warden comes back into view)
			if (getSpawnTimer() > 0) {
				float growHeight = Math.max(1.0f, getBbHeight() * ((150 - getSpawnTimer()) / 150.0f));
				for (int a = 0; a < 33; a++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnSmokeSpiral(level(), getX(), getBoundingBox().minY + growHeight / 2.0, getZ(),
							growHeight, random.nextInt(360), Mth.floor(getBoundingBox().minY) - 1, 2232623);
				}
			}
		}
	}

	/** Real constant per-tick self-seeding, matching the true original's own unconditional
	 * {@code onLivingUpdate} loop - 4 real positions right around the Warden's own feet, each only placed if
	 * really still air. */
	private void seedSapUnderfoot() {
		if (level().isClientSide) return;
		int j = Mth.floor(getY());
		for (int l = 0; l < 4; l++) {
			int i = Mth.floor(getX() + (l % 2 * 2 - 1) * 0.25f);
			int k = Mth.floor(getZ() + (l / 2 % 2 * 2 - 1) * 0.25f);
			BlockPos bp = new BlockPos(i, j, k);
			if (level().getBlockState(bp).isAir()) {
				level().setBlockAndUpdate(bp, BlocksTC.effectSap.get().defaultBlockState());
			}
		}
	}

	/** Real expanding, thickening ring of {@code effect_sap}, exact original geometry - see this class's own
	 * doc comment. Only ever called while {@link #fieldFrenzyCounter} is still positive. */
	private void performFieldFrenzy(ServerLevel level) {
		if (fieldFrenzyCounter >= 121 || fieldFrenzyCounter % 10 != 0) return;

		double radius = (150 - fieldFrenzyCounter) / 8.0;
		int d = 1 + fieldFrenzyCounter / 8;
		int i = Mth.floor(getX());
		int j = Mth.floor(getY());
		int k = Mth.floor(getZ());
		for (int q = 0; q < 180 / d; q++) {
			double radians = Math.toRadians(q * 2 * d);
			int deltaX = (int) (radius * Math.cos(radians));
			int deltaZ = (int) (radius * Math.sin(radians));
			BlockPos bp = new BlockPos(i + deltaX, j, k + deltaZ);
			if (level.getBlockState(bp).isAir() && level.getBlockState(bp.below()).isFaceSturdy(level, bp.below(), Direction.UP)) {
				level.setBlockAndUpdate(bp, BlocksTC.effectSap.get().defaultBlockState());
				level.scheduleTick(bp, BlocksTC.effectSap.get(), 250 + random.nextInt(150));
				if (random.nextFloat() < 0.3f) {
					try {
						net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, getX(), getY(), getZ(), 32.0,
								thaumcraft.common.lib.network.fx.PacketFXBlockArc.of(bp, this,
										0.5f + random.nextFloat() * 0.2f, 0.0f, 0.5f + random.nextFloat() * 0.2f));
					} catch (Exception e) {
						thaumcraft.Thaumcraft.LOGGER.debug("Warden arc FX packet failed to send (no real client connection?)", e);
					}
				}
			}
		}
		playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 0.9f + random.nextFloat() * 0.1f);
	}

	/** Real teleport to a random safe spot near the Warden's own real restricted home (set at spawn), matching
	 * the true original's own real 20-try search (solid ground below, real air at the target) - jittering X/Z
	 * around the home position, Y fixed at the home's own real level. Does nothing if no real restriction is
	 * set, or no safe spot turns up within the real try budget (matching the original's own real "stay put"
	 * fallback). Drops the original's own client-side portal particle trail, matching this whole project's
	 * established client-FX-cut precedent. */
	private void teleportHome(ServerLevel level) {
		if (!hasRestriction()) return;
		BlockPos home = getRestrictCenter();
		for (int tries = 0; tries < 20; tries++) {
			int x = home.getX() + random.nextInt(8) - random.nextInt(8);
			int z = home.getZ() + random.nextInt(8) - random.nextInt(8);
			BlockPos candidate = new BlockPos(x, home.getY(), z);
			if (level.getBlockState(candidate).isAir() && level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)) {
				moveTo(candidate.getX() + 0.5, candidate.getY() + 0.1, candidate.getZ() + 0.5, getYRot(), getXRot());
				playSound(SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
				return;
			}
		}
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (random.nextFloat() > 0.2f) {
			EntityEldritchOrb blast = new EntityEldritchOrb(this);
			lastBlast = !lastBlast;
			level().broadcastEntityEvent(this, lastBlast ? ORB_RIGHT_EVENT : ORB_LEFT_EVENT);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getEyeHeight() / 2.0) + 2.0;
			double dz = target.getZ() - getZ();
			var dir = new net.minecraft.world.phys.Vec3(dx, dy, dz).normalize();
			blast.shoot(dir.x, dir.y, dir.z, 1.0f, 2.0f);
			playSound(SoundEvents.WITHER_SHOOT, 2.0f, 1.0f + random.nextFloat() * 0.1f);
			level().addFreshEntity(blast);
		} else if (hasLineOfSight(target)) {
			target.push(-Math.sin(getYRot() * Math.PI / 180.0) * 1.5, 0.1, Math.cos(getYRot() * Math.PI / 180.0) * 1.5);
			target.addEffect(new MobEffectInstance(MobEffects.WITHER, 400, 0));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
			if (target instanceof net.minecraft.server.level.ServerPlayer player) {
				thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(player, 3 + random.nextInt(3), IPlayerWarp.EnumWarpType.TEMPORARY);
			}
			playSound(SoundEvents.WARDEN_SONIC_BOOM, 4.0f, 1.0f + random.nextFloat() * 0.1f);
			level().broadcastEntityEvent(this, SONIC_EVENT);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == SONIC_EVENT) {
			sonicStartTick = tickCount;
			sonicDuration = 10 + random.nextInt(5);
			sonicYaw = getYHeadRot();
			sonicPitch = getXRot();
		} else if (id == ORB_LEFT_EVENT) {
			armLiftL = 0.5f;
		} else if (id == ORB_RIGHT_EVENT) {
			armLiftR = 0.5f;
		} else if (id == RISE_EVENT) {
			setSpawnTimer(150);
		} else {
			super.handleEntityEvent(id);
		}
	}

	public int getSonicStartTick() {
		return sonicStartTick;
	}

	public int getSonicDuration() {
		return sonicDuration;
	}

	public float getSonicYaw() {
		return sonicYaw;
	}

	public float getSonicPitch() {
		return sonicPitch;
	}

	public float getArmLiftL() {
		return armLiftL;
	}

	public float getArmLiftR() {
		return armLiftR;
	}
}
