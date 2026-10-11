package thaumcraft.common.entities.monster.tainted;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import thaumcraft.api.entities.ITaintedMob;

/**
 * Replaces the old (1.12.2) {@code EntityTaintacleSmall} - a short-lived {@link EntityTaintacle} conjured by
 * a real {@code EntityTaintSeed}/{@code EntityTaintacle} near a distant target: real 200-tick lifetime timer,
 * then a real 10-damage self-hit (magic damage source, matching the original's own {@code DamageSource.MAGIC}
 * exactly) discards it. Never counts as a valid spawn location on its own ({@link #canSpawnHere} always
 * false) and drops nothing (no {@code dropCustomDeathLoot} override needed - matches the original's own
 * both-no-op {@code getDropItem}/{@code dropFewItems}, same as several other taint-terrain blocks/mobs this
 * arc's own {@code BlockTaint}/{@code BlockTaintFeature} already established this exact "drops nothing"
 * shape for).
 */
public class EntityTaintacleSmall extends EntityTaintacle implements ITaintedMob {

	private int lifetime = 200;

	public EntityTaintacleSmall(EntityType<? extends EntityTaintacleSmall> type, Level level) {
		super(type, level);
		xpReward = 0; // as the original
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityTaintacle.createAttributes()
				.add(Attributes.MAX_HEALTH, 5.0)
				.add(Attributes.ATTACK_DAMAGE, 2.0);
	}

	@Override
	protected boolean canSpawnMoreTentacles() {
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && lifetime-- <= 0) {
			hurt(damageSources().magic(), 10.0f);
		}
	}

	@Override
	public boolean canSpawnHere() {
		return false;
	}
}
