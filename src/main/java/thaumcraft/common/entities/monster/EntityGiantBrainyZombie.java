package thaumcraft.common.entities.monster;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * Replaces the old (1.12.2) {@code EntityGiantBrainyZombie} - a real {@link EntityBrainyZombie} subclass
 * with a real synced "anger" float (0-2): rises 0.1 per hit taken (capped at 2.0), decays 0.002/tick once
 * above 1.0, and while above 1.0 both grows the zombie's real hitbox ({@link #getDefaultDimensions}, a uniform
 * {@code 1+anger} scale - the original's own {@code setSize}/{@code multiplySize} calls, real translation
 * via modern {@link EntityDimensions#scale}) and its real attack damage ({@code 7 + (anger-1)*5}, mutating
 * the live {@link Attributes#ATTACK_DAMAGE} base value every tick, same technique the original's own
 * per-tick attribute write used).
 *
 * Real, documented cleanup: the original's own {@code dropFewItems} looped the exact same
 * "6 chances at 2 rotten flesh" block twice back to back (a real duplicate, not two different real drop
 * pools) - this port keeps just the one loop.
 */
public class EntityGiantBrainyZombie extends EntityBrainyZombie {

	private static final EntityDataAccessor<Float> DATA_ANGER =
			SynchedEntityData.defineId(EntityGiantBrainyZombie.class, EntityDataSerializers.FLOAT);

	public EntityGiantBrainyZombie(EntityType<? extends EntityGiantBrainyZombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityBrainyZombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 60.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_ANGER, 0.0f);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.addGoal(2, new LeapAtTargetGoal(this, 0.4f));
	}

	public float getAnger() {
		return entityData.get(DATA_ANGER);
	}

	public void setAnger(float value) {
		float clamped = Math.max(0.0f, Math.min(2.0f, value));
		if (clamped != getAnger()) {
			entityData.set(DATA_ANGER, clamped);
			refreshDimensions();
		}
	}

	@Override
	protected int getBaseExperienceReward() {
		return 15;
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).scale(1.0f + getAnger());
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		setAnger(getAnger() + 0.1f);
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (getAnger() > 1.0f) {
			setAnger(getAnger() - 0.002f);
		}
		AttributeInstance attackDamage = getAttribute(Attributes.ATTACK_DAMAGE);
		if (attackDamage != null) {
			attackDamage.setBaseValue(7.0 + (getAnger() - 1.0f) * 5.0);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		// as the original: two rounds of six chances at two flesh
		for (int i = 0; i < 12; i++) {
			if (level.random.nextBoolean()) {
				spawnAtLocation(new ItemStack(Items.ROTTEN_FLESH, 2));
			}
		}
	}
}
