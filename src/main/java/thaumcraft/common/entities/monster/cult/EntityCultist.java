package thaumcraft.common.entities.monster.cult;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.monster.boss.EntityCultistLeader;

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
 * Real ally-check ({@link #isAlliedTo}) between all {@code EntityCultist} subtypes and the real
 * {@link EntityCultistLeader} boss - restored 2026-09-15 (the leader arc shipped a while before this fix
 * caught up to it), matching the true original's own {@code isOnSameTeam} exactly.
 *
 * Not carried over because they did nothing in the original: its "any light" spawn rule (cultists never spawn
 * naturally, only from portals), its spawn-in particles (only ever called on the server, where they didn't draw),
 * and {@code canAttackClass} (cultists only ever target players). Real home-position restriction
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
		return other instanceof EntityCultist || other instanceof EntityCultistLeader || super.isAlliedTo(other);
	}
}
