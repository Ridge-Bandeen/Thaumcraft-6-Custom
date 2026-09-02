package thaumcraft.common.entities.projectile;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code EntityBottleTaint} - the thrown-debuff-bomb projectile behind
 * {@link thaumcraft.common.items.consumables.ItemBottleTaint}. Same {@link ThrowableItemProjectile} shape as
 * {@link EntityAlumentum} (its closest sibling this port already has). First real consumer of
 * {@link MobEffectFluxTaint} - already ported, real, and functioning, just never registered or applied by
 * anything until now (see that class's own doc comment: "~16 not-yet-ported call sites").
 *
 * Simplified from the original:
 * <ul>
 * <li>No Flux Goo terrain placement on impact - {@code BlocksTC.fluxGoo} is a custom finite fluid
 * ({@code BlockFluidFinite}), the same hard boundary that already made {@code BlockStabilizer} drop its
 * Flux-Rift-pacifying mechanic this session. The debuff itself (this projectile's actual combat purpose)
 * stands on its own without it.</li>
 * <li>No particle/FX (the original's {@code taintsplosionFX}/{@code bottleTaintBreak}) - the usual "no
 * client FX" boundary this whole session.</li>
 * <li>"Is this an undead mob" now checks {@code EntityTypeTags.UNDEAD} - the original's
 * {@code EntityLivingBase#isEntityUndead()}/{@code CreatureAttribute} system doesn't exist anymore.</li>
 * </ul>
 */
public class EntityBottleTaint extends ThrowableItemProjectile {

	public EntityBottleTaint(EntityType<? extends EntityBottleTaint> type, Level level) {
		super(type, level);
	}

	public EntityBottleTaint(LivingEntity thrower, Level level) {
		super(ThaumcraftEntities.BOTTLE_TAINT.get(), thrower, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ItemsTC.bottleTaint.get();
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			AABB area = new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(5.0);
			for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, area)) {
				if (!(target instanceof ITaintedMob) && !target.getType().is(EntityTypeTags.UNDEAD)) {
					target.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, 100, 0, false, true));
				}
			}
			discard();
		}
	}
}
