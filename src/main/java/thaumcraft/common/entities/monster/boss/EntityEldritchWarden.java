package thaumcraft.common.entities.monster.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
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
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.projectile.EntityEldritchOrb;

/**
 * Replaces the old (1.12.2) {@code EntityEldritchWarden} - the Eldritch dimension's real boss. Real {@link
 * EntityThaumcraftBoss} subclass (see that class's own doc comment for the real boss-bar/enrage/self-heal
 * mechanics it provides). Shares {@link thaumcraft.common.entities.monster.EntityEldritchGuardian}'s ranged
 * attack shape (same real {@link
 * EntityEldritchOrb}, same real melee-range Wither/Warp "scream" fallback, boss-tier stronger: Wither +
 * Weakness both 20s, 3-5 Warp vs. the Guardian's Wither-only 20s/1-3 Warp) and its own real random-title
 * naming (one of the original's own 15 Lovecraftian names, matching the original's base {@code
 * entity.EldritchWarden.name.custom} format - minus the champion-mod suffix, same as {@link
 * EntityCultistLeader}'s own doc comment explains for the same unported system).
 *
 * Real, documented cuts: no "field frenzy" phase (the original's own absorption-shield-depletion trigger -
 * 150-tick invulnerability, teleport home, and an AoE spiral of {@code BlocksTC.effectSap} ground-hazard
 * placements around itself - a real, separate mechanic from {@link EntityThaumcraftBoss}'s own generic
 * enrage). This isn't a simplification choice - {@code effectSap} isn't a ported block at all yet, so the
 * whole mechanic is genuinely unbuildable, not just trimmed; {@link EntityThaumcraftBoss}'s own real boss-bar/
 * self-heal/enrage stand in for it instead of a parallel absorption-shield system. No arm-lift swing
 * animation (tied to the not-yet-ported mesh - see {@link thaumcraft.client.render.ModelEldritchPlaceholder}'s
 * own doc comment). No sonic-scream client FX broadcast ({@code PacketFXSonic}, unported {@code
 * FXDispatcher}). No champion-mod name suffix (unported champion system, same as {@code EntityCultistLeader}).
 */
public class EntityEldritchWarden extends EntityThaumcraftBoss implements RangedAttackMob {

	private static final String[] TITLES = { "Aphoom-Zhah", "Basatan", "Chaugnar Faugn", "Mnomquah", "Nyogtha",
			"Oorn", "Shaikorth", "Rhan-Tegoth", "Rhogog", "Shudde M'ell", "Vulthoom", "Yag-Kosha", "Yibb-Tstll",
			"Zathog", "Zushakon" };

	private static final EntityDataAccessor<Byte> DATA_TITLE =
			SynchedEntityData.defineId(EntityEldritchWarden.class, EntityDataSerializers.BYTE);

	public EntityEldritchWarden(EntityType<? extends EntityEldritchWarden> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityThaumcraftBoss.createAttributes()
				.add(Attributes.MAX_HEALTH, 400.0)
				.add(Attributes.MOVEMENT_SPEED, 0.33)
				.add(Attributes.ATTACK_DAMAGE, 10.0)
				.add(Attributes.ARMOR, 8.0);
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
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.is(net.minecraft.world.damagesource.DamageTypes.DROWN) || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER)) {
			return false;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (random.nextFloat() > 0.2f) {
			EntityEldritchOrb blast = new EntityEldritchOrb(this);
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
				ThaumcraftCapabilities.getWarp(player).add(IPlayerWarp.EnumWarpType.TEMPORARY, 3 + random.nextInt(3));
			}
			playSound(SoundEvents.WARDEN_SONIC_BOOM, 4.0f, 1.0f + random.nextFloat() * 0.1f);
		}
	}
}
