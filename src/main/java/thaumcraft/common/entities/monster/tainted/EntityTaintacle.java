package thaumcraft.common.entities.monster.tainted;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.entities.ITaintedMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.blocks.world.taint.ITaintBlock;
import thaumcraft.common.items.resources.ItemCrystalEssence;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code EntityTaintacle} - a real, stationary {@link Monster} rooted to taint
 * terrain: real {@link #move} override zeroes horizontal motion and clamps vertical to never rise, matching
 * the original's own override exactly (same real "can't actually move" shape {@code EntityTaintSeed} also
 * uses). Real "starves" (takes real damage) once every 20 real ticks if it's not standing on/above a real
 * {@link ITaintBlock} - the modern replacement for the original's own {@code Material} comparison.
 *
 * Real "spawns a real {@link EntityTaintacleSmall} near a distant, visible target" mechanic, gated off for
 * {@link EntityTaintacleSmall} itself via {@link #canSpawnMoreTentacles} (a real, cleaner modern replacement
 * for the original's own {@code !(this instanceof EntityTaintacleSmall)} instanceof check - same real
 * behavior, just resolved through overriding rather than a runtime type test).
 *
 * Real {@code BiomeHandler.ELDRITCH}-gated behavior in {@link #spawnTentacles}, restored 2026-09-15 now that
 * the real {@code thaumcraft:eldritch} biome exists (see {@link thaumcraft.common.world.biomes.ThaumcraftBiomes}):
 * the outer taintlet-spawn gate is really an OR across being in that biome OR standing on/above real taint
 * terrain (previously only the taint-terrain half was ported), and being in that biome over real air adjacent
 * to a real solid block additionally places a real {@code taint_fibre} there too, matching the true original's
 * own real {@code BlockUtils.isAdjacentToSolidBlock} check (ported as {@link #isAdjacentToSolidBlock}, the
 * modern equivalent). Every attack swing sends entity event 16, which sets the client's {@link #flailIntensity} to 3
 * (the model whips faster and wider), decaying by 0.01 a tick back to 1, as the original. The original's own {@code
 * faceEntity}/{@code updateRotation} pair is real, harmless dead code in the original itself (a 2-argument
 * override that doesn't actually match vanilla 1.12's real 3-argument {@code EntityLiving#faceEntity}, so
 * nothing ever called it) - not reproduced. Real custom {@code tentacle} sound restored 2026-09-15 for
 * hurt/death/attack (was a vanilla Warden tendril-click stand-in) - see {@link ThaumcraftSounds}'s own doc
 * comment; the original's own ambient sound ({@code BLOCK_CHORUS_FLOWER_DEATH}) was already a real vanilla
 * choice, kept unchanged.
 */
public class EntityTaintacle extends Monster implements ITaintedMob {

	/** Client-side: how hard the model flails - 3 right after an attack, easing back to 1. */
	public float flailIntensity = 1.0f;

	public EntityTaintacle(EntityType<? extends EntityTaintacle> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 50.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 6.0f));
		goalSelector.addGoal(3, new RandomLookAroundGoal(this));
		targetSelector.addGoal(0, new HurtByTargetGoal(this));
		targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, true));
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof ITaintedMob || super.isAlliedTo(other);
	}

	public boolean canSpawnHere() {
		return isOnTaint() && level().getDifficulty() != Difficulty.PEACEFUL;
	}

	protected boolean isOnTaint() {
		BlockPos pos = blockPosition();
		return level().getBlockState(pos).getBlock() instanceof ITaintBlock
				|| level().getBlockState(pos.below()).getBlock() instanceof ITaintBlock;
	}

	/** {@code false} for {@link EntityTaintacleSmall} - see that class's own override. */
	protected boolean canSpawnMoreTentacles() {
		return true;
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		double y = movement.y > 0.0 ? 0.0 : movement.y;
		super.move(type, new Vec3(0.0, y, 0.0));
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && tickCount % 20 == 0) {
			if (!isOnTaint()) {
				hurt(damageSources().starve(), 1.0f);
			}
			if (canSpawnMoreTentacles() && tickCount % 40 == 0) {
				LivingEntity target = getTarget();
				if (target != null && distanceToSqr(target) > 16.0 && distanceToSqr(target) < 256.0 && hasLineOfSight(target)) {
					spawnTentacles(target);
				}
			}
		}
		// Real client-only "arising from the ground" cue restored 2026-09-17 (was dropped under this port's
		// old blanket "no client FX" rule) - routed through ClientFxHandlers#spawnTentacleArise for the same
		// real dist-crash-avoidance reason every other client-only trigger in this port uses.
		if (level().isClientSide && flailIntensity > 1.0f) {
			flailIntensity -= 0.01f;
		}
		if (level().isClientSide && tickCount < getBbHeight() * 10.0f && onGround()) {
			thaumcraft.client.fx.ClientFxHandlers.spawnTentacleArise(level(), this);
		}
	}

	protected void spawnTentacles(Entity entity) {
		BlockPos pos = entity.blockPosition();
		boolean inEldritchBiome = level().getBiome(pos).is(thaumcraft.common.world.biomes.ThaumcraftBiomes.ELDRITCH);
		boolean onTaint = level().getBlockState(pos).getBlock() instanceof ITaintBlock || level().getBlockState(pos.below()).getBlock() instanceof ITaintBlock;
		if (!inEldritchBiome && !onTaint) {
			return;
		}
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}
		EntityTaintacleSmall taintlet = thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE_SMALL.get().create(serverLevel);
		if (taintlet == null) {
			return;
		}
		taintlet.moveTo(entity.getX() + random.nextFloat() - random.nextFloat(), entity.getY(),
				entity.getZ() + random.nextFloat() - random.nextFloat(), 0.0f, 0.0f);
		serverLevel.addFreshEntity(taintlet);
		playSound(ThaumcraftSounds.TENTACLE.get(), getSoundVolume(), getVoicePitch());
		if (inEldritchBiome && level().getBlockState(pos).isAir() && isAdjacentToSolidBlock(level(), pos)) {
			level().setBlock(pos, thaumcraft.api.blocks.BlocksTC.taintFibre.get().defaultBlockState(), 3);
		}
	}

	/** Real modern equivalent of the true original's own {@code BlockUtils.isAdjacentToSolidBlock}. */
	public static boolean isAdjacentToSolidBlock(Level level, BlockPos pos) {
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			BlockPos neighbor = pos.relative(dir);
			if (level.getBlockState(neighbor).isFaceSturdy(level, neighbor, dir.getOpposite())) {
				return true;
			}
		}
		return false;
	}

	/** As the original: every swing flails the model (event 16) and plays the tentacle sound, hit or miss. */
	@Override
	public boolean doHurtTarget(Entity target) {
		level().broadcastEntityEvent(this, (byte) 16);
		playSound(ThaumcraftSounds.TENTACLE.get(), getSoundVolume(), getVoicePitch());
		return super.doHurtTarget(target);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 16) {
			flailIntensity = 3.0f;
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.CHORUS_FLOWER_DEATH;
	}

	@Override
	public float getVoicePitch() {
		return 1.3f - getBbHeight() / 10.0f;
	}

	@Override
	protected float getSoundVolume() {
		return getBbHeight() / 8.0f;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return ThaumcraftSounds.TENTACLE.get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ThaumcraftSounds.TENTACLE.get();
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(makeCrystal());
	}

	static ItemStack makeCrystal() {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemCrystalEssence) stack.getItem()).setAspects(stack, new AspectList().add(Aspect.FLUX, 1));
		return stack;
	}

	@Override
	protected int decreaseAirSupply(int air) {
		return air;
	}

	@Override
	public boolean isPushable() {
		return false;
	}
}
