package thaumcraft.common.entities.projectile;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
 * As the original it gives Flux Taint to living things within 5 blocks (not tainted or undead ones -
 * {@code EntityTypeTags.UNDEAD} now) and splashes Flux Goo around: ten tries at spots up to 3 blocks off, half of them
 * placing goo on a free spot over a solid block (or one lower).
 *
 * <p>Real shatter FX restored 2026-09-17 (was dropped under this port's old blanket "no client FX" rule) - a
 * real vanilla entity-event trigger (id 3, matching the true original's own real
 * {@code world.setEntityState(this, 3)}) fires both halves of the true original's own real
 * {@code taintsplosionFX}/{@code bottleTaintBreak} pair: 100 real {@link
 * thaumcraft.client.fx.particles.FXBreakingFade} shards (routed through {@link
 * thaumcraft.client.fx.ClientFxHandlers#spawnTaintsplosion} for the same real dist-crash-avoidance reason
 * every other client-only trigger in this port uses) plus a real plain vanilla item-crack burst and splash
 * sound (the true original's own second half never needed a custom particle class at all, so it's just
 * called directly here rather than through {@code ClientFxHandlers} - {@link Level#addParticle} is already
 * real and dist-safe on the plain common-side {@link Level} type).
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

	private static final byte BREAK_EVENT = 3;

	/** The original's goo rule: a replaceable spot over a full solid block. */
	private boolean gooFits(net.minecraft.core.BlockPos p) {
		net.minecraft.core.BlockPos below = p.below();
		return level().getBlockState(below).isCollisionShapeFullBlock(level(), below) && level().getBlockState(p).canBeReplaced();
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
			for (int a = 0; a < 10; a++) {
				int xx = (int) ((random.nextFloat() - random.nextFloat()) * 4.0f);
				int zz = (int) ((random.nextFloat() - random.nextFloat()) * 4.0f);
				net.minecraft.core.BlockPos p = blockPosition().offset(xx, 0, zz);
				if (!level().random.nextBoolean()) continue;
				if (!gooFits(p)) p = p.below();
				if (gooFits(p)) level().setBlockAndUpdate(p, thaumcraft.api.blocks.BlocksTC.fluxGoo.get().defaultBlockState());
			}
			level().broadcastEntityEvent(this, BREAK_EVENT);
			discard();
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == BREAK_EVENT) {
			Level level = level();
			for (int a = 0; a < 100; a++) {
				thaumcraft.client.fx.ClientFxHandlers.spawnTaintsplosion(level, this);
			}
			for (int k = 0; k < 8; k++) {
				level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(ItemsTC.bottleTaint.get())),
						getX(), getY(), getZ(), random.nextGaussian() * 0.15, random.nextDouble() * 0.2, random.nextGaussian() * 0.15);
			}
			level.playLocalSound(getX(), getY(), getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.NEUTRAL,
					1.0f, random.nextFloat() * 0.1f + 0.9f, false);
		} else {
			super.handleEntityEvent(id);
		}
	}
}
