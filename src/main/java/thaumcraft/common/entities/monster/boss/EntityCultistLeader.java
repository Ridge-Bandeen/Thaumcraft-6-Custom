package thaumcraft.common.entities.monster.boss;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.projectile.EntityGolemOrb;

/**
 * Replaces the old (1.12.2) {@code EntityCultistLeader} - the Crimson Cult's boss. Real {@link
 * EntityThaumcraftBoss} subclass (see that class's own doc comment for the real boss-bar/enrage/self-heal
 * mechanics it provides, and what's cut). Real {@link RangedAttackMob} using vanilla's own {@link
 * RangedAttackGoal}, same "no minimum-engagement-distance gate" simplification as {@link
 * thaumcraft.common.entities.monster.cult.EntityCultistCleric}'s own doc comment explains.
 *
 * Real random-title naming (one of the original's own 15 names, formatted as "Praetor &lt;name&gt;" -
 * matches the original's own base {@code entity.CultistLeader.name.custom} format string, minus the
 * champion-mod suffix - see this doc comment's own note on that below). Always-worn Crimson Praetor
 * equipment plus a real
 * difficulty-gated weapon choice (Void Sword on Easy, Crimson Blade otherwise - matching the original
 * exactly). Real "supports its own cult" mechanic: buffs nearby real {@link EntityCultist}s with
 * Regeneration every 20 ticks, a real, portable version of the original's own {@code updateAITasks} loop
 * (which used the same effect/radius/interval - only the original's unported {@code EntityUtils} exception-
 * swallowing wrapper is gone, not needed for a plain entity scan). Real ranged attack: a homing "red" {@link
 * EntityGolemOrb} blast, matching the original's own {@code attackEntityWithRangedAttack} exactly (including
 * its {@code d2 + 2.0} upward aim offset). Real guaranteed Primordial Pearl + rare loot bag death drop.
 *
 * Real, documented cuts: no champion-mod name suffix (the original's own {@code generateName} appended a
 * champion-modifier title when the champion system flagged this boss as one - that system isn't ported).
 * No random enchantment on the starting weapon (same rationale as {@code EntityCultistKnight}'s own rare-
 * weapon drop). {@link ItemsTC#lootBagRare} stands in for the original's single metadata-variant {@code
 * ItemsTC.lootBag} (meta 2) - this port's own loot bag item was split into three separate tier items
 * instead of one item with variants.
 */
public class EntityCultistLeader extends EntityThaumcraftBoss implements RangedAttackMob {

	private static final String[] TITLES = { "Alberic", "Anselm", "Bastian", "Beturian", "Chabier", "Chorache",
			"Chuse", "Dodorol", "Ebardo", "Ferrando", "Fertus", "Guillen", "Larpe", "Obano", "Zelipe" };

	private static final EntityDataAccessor<Byte> DATA_TITLE =
			SynchedEntityData.defineId(EntityCultistLeader.class, EntityDataSerializers.BYTE);

	public EntityCultistLeader(EntityType<? extends EntityCultistLeader> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityThaumcraftBoss.createAttributes()
				.add(Attributes.MOVEMENT_SPEED, 0.32)
				.add(Attributes.MAX_HEALTH, 150.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0);
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
		goalSelector.addGoal(2, new RangedAttackGoal(this, 1.0, 30, 40, 24.0f));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1, false));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof EntityCultist || super.isAlliedTo(other);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		setEquipment(difficulty);
		setTitle(random.nextInt(TITLES.length));
		setCustomName(net.minecraft.network.chat.Component.translatable("entity.thaumcraft.cultist_leader.name", getTitle()));
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	private void setEquipment(DifficultyInstance diff) {
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonPraetorHelm.get()));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonPraetorChest.get()));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonPraetorLegs.get()));
		setItemSlot(EquipmentSlot.FEET, new ItemStack(ItemsTC.crimsonBoots.get()));
		setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(diff.getDifficulty() == Difficulty.EASY
				? ItemsTC.voidSword.get() : ItemsTC.crimsonBlade.get()));
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (!level().isClientSide && tickCount % 20 == 0) {
			for (EntityCultist ally : level().getEntitiesOfClass(EntityCultist.class, new AABB(getX(), getY(), getZ(), getX() + 1.0, getY() + 1.0, getZ() + 1.0).inflate(8.0))) {
				if (!ally.hasEffect(MobEffects.REGENERATION)) {
					ally.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1));
				}
			}
		}
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (hasLineOfSight(target)) {
			swing(InteractionHand.MAIN_HAND);
			EntityGolemOrb blast = new EntityGolemOrb(this, target, true);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 2.0 - (getY() + getEyeHeight() / 2.0) + 2.0;
			double dz = target.getZ() - getZ();
			Vec3 dir = new Vec3(dx, dy, dz).normalize();
			blast.shoot(dir.x, dir.y, dir.z, 0.66f, 3.0f);
			level().addFreshEntity(blast);
		}
	}

	@Override
	protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(new ItemStack(ItemsTC.primordialPearl.get()));
		spawnAtLocation(new ItemStack(ItemsTC.lootBagRare.get()));
	}
}
