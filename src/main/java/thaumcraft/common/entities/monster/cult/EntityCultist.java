package thaumcraft.common.entities.monster.cult;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

/**
 * Replaces the old (1.12.2) {@code EntityCultist} - the shared base of the Crimson Cult's "regular" ranks
 * (see {@link EntityCultistKnight}; the cleric/ritualist rank and the boss-tier {@code EntityCultistLeader}
 * are a later sub-chunk of this same arc). Real {@link Monster} subclass (the original was {@code
 * EntityMob} too).
 *
 * The original's own source has NO {@code initEntityAI()} override at all - confirmed by direct read, not
 * an oversight this port is fixing. It's never spawned as a bare {@code EntityCultist} either (only {@code
 * EntityCultistKnight}/{@code EntityCultistCleric} are registered spawnable types in the original's own
 * {@code ConfigEntities}) - every real subclass supplies its own {@code initEntityAI()}/{@link
 * #registerGoals}, so this base class intentionally has none, matching the original exactly rather than
 * inventing default behavior it never had.
 *
 * Real door-breaking pathfinding (matches the original's own constructor-time {@code
 * ((PathNavigateGround) getNavigator()).setBreakDoors(true)}) and real 5% chest/feet/head/legs drop chance.
 * Real ally-check ({@link #isAlliedTo}) between all {@code EntityCultist} subtypes - {@code
 * EntityCultistLeader} isn't ported yet, so that half of the original's own check is a real, documented gap
 * until that class exists.
 *
 * Real, documented cuts vs the original: no natural-spawn light-level override (this whole session's
 * monster arc has consistently deferred {@code SpawnPlacements} wiring - see {@code EntityEldritchCrab}'s
 * own doc-comment precedent), no {@code spawnExplosionParticle} client spawn-in effect (tied to the
 * unported {@code FXDispatcher}, purely cosmetic), no {@code canAttackClass} sibling-exemption (the
 * original fed this into 1.12's own {@code EntityLiving} hook, which modern MC removed in favor of a
 * {@code TargetingConditions} predicate on the targeting Goal itself - moot anyway since {@link
 * EntityCultistKnight} only ever targets {@code Player}, not other mobs). Real home-position restriction
 * now comes free from vanilla's own {@link net.minecraft.world.entity.Mob#restrictTo} (persisted
 * automatically) - the original had to hand-roll its own NBT read/write for this because 1.12's {@code
 * EntityCreature} lacked it.
 */
public abstract class EntityCultist extends Monster {

	protected EntityCultist(EntityType<? extends EntityCultist> type, Level level) {
		super(type, level);
		setDropChance(EquipmentSlot.CHEST, 0.05f);
		setDropChance(EquipmentSlot.FEET, 0.05f);
		setDropChance(EquipmentSlot.HEAD, 0.05f);
		setDropChance(EquipmentSlot.LEGS, 0.05f);
		if (getNavigation() instanceof GroundPathNavigation ground) {
			ground.setCanOpenDoors(true);
		}
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.ATTACK_DAMAGE, 4.0);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof EntityCultist || super.isAlliedTo(other);
	}
}
