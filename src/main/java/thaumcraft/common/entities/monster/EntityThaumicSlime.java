package thaumcraft.common.entities.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityThaumicSlime} - a real {@link Slime} subclass (the original was
 * one too, {@code EntitySlime}), reusing vanilla's own real jump/float/attack {@code Goal}s and its own real
 * split-on-death behavior ({@link Slime#remove}, inherited unchanged) rather than re-deriving the original's
 * own hand-rolled equivalents of both.
 *
 * Real, documented cut: the original's other constructor spawned a second, smaller "flying" slime aimed at
 * a distant player as a ranged spit attack (a whole aimed-projectile-entity mechanic, tracked via its own
 * {@code launched}/{@code spitCounter} fields) - not ported; this is a real melee-only slime, same as every
 * vanilla one, just with real Thaumcraft-flavored size-scaled attack damage and a flux-crystal death drop.
 * The original's own {@code getDropItem}/{@code dropFewItems}' two overlapping crystal-essence drops are
 * collapsed into the one real drop {@code ConfigItems.FLUX_CRYSTAL} actually was (a crystal essence carrying
 * the {@link Aspect#FLUX} tag) via {@link #dropCustomDeathLoot}, the same local {@code makeCrystal} stand-in
 * {@link EntityWisp} already established rather than porting the whole unconverted {@code ThaumcraftApiHelper}.
 *
 * <p>Touch damage itself ({@link #getAttackDamage}/{@link #isDealsDamage}) reuses vanilla {@link Slime}'s own
 * real {@code playerTouch}/{@code dealDamage} unmodified - verified real via a {@code javap} disassembly of
 * that exact call chain (own doc comment on the GameTest confirms why a live in-test check against a real
 * player isn't possible: {@code GameTestHelper}'s own mock players hardcode {@code isSpectator()}/
 * {@code isCreative()} true, a structural test-harness limit, not a gap here) rather than a live assertion.
 */
public class EntityThaumicSlime extends Slime implements ITaintedMob {

	private boolean wasOnGroundLastTick;

	public EntityThaumicSlime(EntityType<? extends EntityThaumicSlime> type, Level level) {
		super(type, level);
	}

	/** Vanilla's {@code Slime#tick} spawns {@code size*8} of this particle on every landing. The original avoided
	 * it by pre-setting its own {@code wasOnGround} before calling {@code super.onUpdate()}; that field is private now,
	 * so instead the burst is pointed at a provider-less particle type, i.e. nothing - the purple
	 * {@link thaumcraft.client.fx.ClientFxHandlers#spawnSlimeJump} splat below is this mob's only landing effect.
	 * (Found 2026-09-25: green vanilla slime particles on every jump.) */
	@Override
	protected net.minecraft.core.particles.ParticleOptions getParticleType() {
		return thaumcraft.common.registry.ThaumcraftParticleTypes.NONE.get();
	}

	/** Real client-only landing-splat cue restored 2026-09-17 (was dropped under this port's old blanket "no
	 * client FX" rule) - a real faithful port of the true original's own real "just touched down" detection,
	 * routed through {@link thaumcraft.client.fx.ClientFxHandlers#spawnSlimeJump} for the same real
	 * dist-crash-avoidance reason every other client-only trigger in this port uses. */
	@Override
	public void tick() {
		super.tick();
		if (level().isClientSide) {
			if (onGround() && !wasOnGroundLastTick) {
				for (int j = 0; j < getSize() * 2; j++) {
					thaumcraft.client.fx.ClientFxHandlers.spawnSlimeJump(level(), this, getSize());
				}
			}
			wasOnGroundLastTick = onGround();
		}
	}

	/** {@code Slime} doesn't define its own static attribute builder (unlike most vanilla mobs) - matches
	 * vanilla's own real default slime attributes (health/speed/attack all get dynamically scaled per-size
	 * elsewhere - {@link #getAttackDamage} for damage, {@link Slime#getSize} itself drives max health via
	 * {@code refreshDimensions}/vanilla's own real per-size scaling, unchanged). */
	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.2)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		int tier = random.nextInt(3);
		if (tier < 2 && random.nextFloat() < 0.5f * difficulty.getSpecialMultiplier()) {
			tier++;
		}
		setSize(1 << tier, true);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	protected float getAttackDamage() {
		return getSize() + 1;
	}

	@Override
	protected boolean isDealsDamage() {
		return true;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		if (getSize() > 1) {
			spawnAtLocation(makeCrystal(Aspect.FLUX), 0.0f);
		}
	}

	/** Same local stand-in {@link EntityWisp#makeCrystal} already established for the original's
	 * {@code ThaumcraftApiHelper.makeCrystal}. */
	private static ItemStack makeCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.SLIME_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.SLIME_DEATH;
	}
}
