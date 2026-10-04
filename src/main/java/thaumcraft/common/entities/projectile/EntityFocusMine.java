package thaumcraft.common.entities.projectile;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.lib.utils.EntityChecks;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityFocusMine}, backing {@link thaumcraft.common.items.casters.foci.FocusMediumMine}:
 * a trap holding the rest of the spell. As the original it is set down where the spell is cast and sinks slowly
 * (0.01 a tick) until it lands on something, which arms it; two seconds later it starts checking, every 5 ticks, for
 * living things within a block - foes, or friends if so set (the original's isFriendly: the caster, their team, their
 * pets, players with PvP off) touching the box a block out each way - and runs the spell on each of them, a tick apart, then is spent. It lasts at most a
 * minute, vanishes if its caster is gone, and once armed shows its effects' particles.
 */
public class EntityFocusMine extends ThrowableProjectile implements IEntityWithComplexSpawn {

	private static final EntityDataAccessor<Boolean> ARMED = SynchedEntityData.defineId(EntityFocusMine.class, EntityDataSerializers.BOOLEAN);

	private FocusPackage focusPackage;
	private boolean friendly;
	private int counter = 40;
	private FocusEffect[] effects;

	public EntityFocusMine(EntityType<? extends EntityFocusMine> type, Level level) {
		super(type, level);
	}

	public EntityFocusMine(LivingEntity caster, FocusPackage focusPackage, Trajectory trajectory, boolean friendly) {
		super(ThaumcraftEntities.FOCUS_MINE.get(), caster.level());
		setOwner(caster);
		this.focusPackage = focusPackage;
		this.friendly = friendly;
		setPos(trajectory.source.x, trajectory.source.y, trajectory.source.z);
		setDeltaMovement(Vec3.ZERO); // the original shot it at speed 0: it is placed, not thrown
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(ARMED, false);
	}

	public boolean isArmed() {
		return entityData.get(ARMED);
	}

	@Override
	protected double getDefaultGravity() {
		return 0.01;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		if (getOwner() != null) entityData.set(ARMED, true);
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		if (getOwner() != null) entityData.set(ARMED, true);
		setDeltaMovement(Vec3.ZERO);
	}

	@Override
	public void tick() {
		super.tick();
		if (isRemoved()) return;
		if (tickCount > 1200 || (!level().isClientSide && getOwner() == null)) {
			discard();
			return;
		}
		if (!isArmed()) return;
		if (counter > 0) counter--;
		if (counter > 0 || tickCount % 5 != 0) return;
		if (level().isClientSide) {
			if (effects == null) effects = focusPackage != null ? focusPackage.getFocusEffects() : new FocusEffect[0];
			if (effects != null && effects.length > 0) {
				FocusEffect effect = effects[random.nextInt(effects.length)];
				effect.renderParticleFX(level(), getX() + random.nextGaussian() * 0.1, getY() + random.nextGaussian() * 0.1, getZ() + random.nextGaussian() * 0.1,
						random.nextGaussian() * 0.01, random.nextGaussian() * 0.01, random.nextGaussian() * 0.01);
			}
			return;
		}
		if (focusPackage == null || !(getOwner() instanceof LivingEntity caster)) return;
		// (the original's getEntitiesInRange: a box a block out each way)
		List<LivingEntity> near = level().getEntitiesOfClass(LivingEntity.class, new AABB(position(), position()).inflate(1.0), LivingEntity::isAlive);
		int delay = 0;
		for (LivingEntity target : near) {
			if (EntityChecks.isFriendly(caster, target) != friendly) continue;
			Vec3 at = target.position().add(0, target.getBbHeight() / 2.0, 0);
			Vec3 from = position();
			FocusPackage copy = focusPackage.copy(caster);
			thaumcraft.common.lib.scheduling.BlockScheduler.addRunnableServer(level(), () -> FocusEngine.runFocusPackage(copy,
					new Trajectory[] { new Trajectory(from, at.subtract(from).normalize()) },
					new HitResult[] { new EntityHitResult(target, target.position().add(0, target.getBbHeight() / 2.0, 0)) }), delay++);
		}
		if (delay > 0) discard();
	}

	@Override
	public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
		buffer.writeNbt(focusPackage != null ? focusPackage.serialize() : new CompoundTag());
	}

	@Override
	public void readSpawnData(RegistryFriendlyByteBuf buffer) {
		focusPackage = readPackage(buffer.readNbt());
	}

	private FocusPackage readPackage(CompoundTag tag) {
		if (tag == null || tag.isEmpty()) return null;
		try {
			FocusPackage p = new FocusPackage();
			p.deserialize(tag, level());
			return p;
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("armed", isArmed());
		if (focusPackage != null) tag.put("pack", focusPackage.serialize());
		tag.putBoolean("friendly", friendly);
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		friendly = tag.getBoolean("friendly");
		entityData.set(ARMED, tag.getBoolean("armed"));
		if (isArmed()) counter = 0;
		focusPackage = readPackage(tag.getCompound("pack"));
	}
}
