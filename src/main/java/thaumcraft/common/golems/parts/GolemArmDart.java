package thaumcraft.common.golems.parts;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemArm;

/**
 * Replaces the old (1.12.2) {@code GolemArmDart} - the real {@link GolemArm.IArmFunction} the "DARTS" arm
 * (see {@link thaumcraft.common.golems.GolemProperties#init}) was registered without until now, closing a
 * real gap that class's own doc comment flagged ("no shipped {@code GolemArm} has a real
 * {@code IArmFunction} yet"). Real ranged attack: an {@link thaumcraft.common.entities.projectile.EntityGolemDart} (as the original: an
 * arrow with a small hitbox, drawn smaller), damage
 * scaled to a third of the golem's own real attack damage plus range plus a small real random spread, same
 * real formula and 1.6/3.0 velocity/inaccuracy as the original's own {@code onRangedAttack}.
 *
 * {@link #getRangedAttackAI} returns vanilla's own {@link RangedAttackGoal} directly (same real interval/
 * range parameters - 20-25 ticks, 16 blocks - as the original's own hand-rolled {@code AIArrowAttack}) - the
 * {@link GolemArm.IArmFunction} interface itself already expects that return type, so no custom {@code Goal}
 * subclass was ever needed here, matching the same real "reuse vanilla directly" precedent
 * {@link thaumcraft.common.golems.EntityThaumcraftGolem}'s own doc comment already established for
 * {@code FollowOwnerGoal}/{@code OwnerHurtByTargetGoal}/{@code OwnerHurtTargetGoal}.
 */
public class GolemArmDart implements GolemArm.IArmFunction {

	@Override
	public void onMeleeAttack(IGolemAPI golem, Entity target) {
	}

	@Override
	public void onRangedAttack(IGolemAPI golem, LivingEntity target, float range) {
		LivingEntity shooter = golem.getGolemEntity();
		if (!(shooter.level() instanceof ServerLevel level)) return;

		thaumcraft.common.entities.projectile.EntityGolemDart dart = new thaumcraft.common.entities.projectile.EntityGolemDart(level, shooter);
		var atk = shooter.getAttribute(Attributes.ATTACK_DAMAGE);
		double baseDamage = (atk != null ? atk.getValue() : 1.0) / 3.0;
		dart.setBaseDamage(baseDamage + range + level.getRandom().nextGaussian() * 0.25);

		double dx = target.getX() - shooter.getX();
		double dy = target.getBoundingBox().minY + target.getEyeHeight() + (double) (range * range) - dart.getY();
		double dz = target.getZ() - shooter.getZ();
		dart.shoot(dx, dy, dz, 1.6f, 3.0f);

		level.addFreshEntity(dart);
		shooter.playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
	}

	@Override
	public RangedAttackGoal getRangedAttackAI(RangedAttackMob golem) {
		return new RangedAttackGoal(golem, 1.0, 20, 25, 16.0f);
	}

	@Override
	public void onUpdateTick(IGolemAPI golem) {
	}
}
