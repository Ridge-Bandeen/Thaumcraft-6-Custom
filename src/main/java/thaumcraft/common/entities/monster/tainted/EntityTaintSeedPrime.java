package thaumcraft.common.entities.monster.tainted;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * Replaces the old (1.12.2) {@code EntityTaintSeedPrime} - a bigger, tougher {@link EntityTaintSeed} variant
 * (double the {@link #getArea}, real 1-3 Flux Crystal Essence death drops instead of always exactly one).
 */
public class EntityTaintSeedPrime extends EntityTaintSeed {

	public EntityTaintSeedPrime(EntityType<? extends EntityTaintSeedPrime> type, Level level) {
		super(type, level);
	}

	/** Real, own hitbox (bigger than the base {@link EntityTaintSeed}'s own real 1.5x1.25) - the true
	 * original's own real {@code setSize(2.0f, 2.0f)}, restored 2026-09-15 alongside the base class's own
	 * same real gap (see that class's own doc comment). */
	@Override
	public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return net.minecraft.world.entity.EntityDimensions.scalable(2.0f, 2.0f);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityTaintSeed.createAttributes()
				.add(Attributes.MAX_HEALTH, 150.0)
				.add(Attributes.ATTACK_DAMAGE, 7.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected int getArea() {
		return 2;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(makeCrystal());
		if (random.nextBoolean()) {
			spawnAtLocation(makeCrystal());
		}
		if (random.nextBoolean()) {
			spawnAtLocation(makeCrystal());
		}
	}
}
