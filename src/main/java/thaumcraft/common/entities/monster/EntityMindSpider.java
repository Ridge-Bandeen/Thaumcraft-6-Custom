package thaumcraft.common.entities.monster;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import thaumcraft.api.entities.IEldritchMob;

/**
 * Replaces the old (1.12.2) {@code EntityMindSpider} - a real {@link Spider} subclass (the original was
 * {@code EntitySpider} too), reusing vanilla's own real climbing/web/pathfinding AI entirely unchanged. A
 * tiny (1 health, 1 damage), self-terminating illusion: real synced {@code harmless} flag (when set, both
 * suppresses its own attack via {@link #doHurtTarget} and gives it a real 60-second lifespan after which it
 * discards itself - matches the original's own {@code lifeSpan} field, which otherwise never expires) and a
 * real synced {@code viewer} player-name string (read/write only in this port, same as the original itself -
 * nothing in the still-unported "warp" mental-effect system that presumably sets it exists in this port
 * yet, so it's a real, wired field with no real writer currently).
 *
 * Real, documented omission: the original's own {@code getDropItem}/{@code dropFewItems} were both explicit
 * no-ops (a mind spider drops nothing, not even vanilla's own normal spider eye/string) - this port doesn't
 * need a {@code dropCustomDeathLoot} override at all to match that: no custom loot table is registered for
 * this entity type, so it already drops nothing by default.
 */
public class EntityMindSpider extends Spider implements IEldritchMob {

	private static final EntityDataAccessor<Boolean> DATA_HARMLESS =
			SynchedEntityData.defineId(EntityMindSpider.class, EntityDataSerializers.BOOLEAN);
	private static final EntityDataAccessor<String> DATA_VIEWER =
			SynchedEntityData.defineId(EntityMindSpider.class, EntityDataSerializers.STRING);

	private int lifeSpan = Integer.MAX_VALUE;

	public EntityMindSpider(EntityType<? extends EntityMindSpider> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Spider.createAttributes()
				.add(Attributes.MAX_HEALTH, 1.0)
				.add(Attributes.ATTACK_DAMAGE, 1.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_HARMLESS, false);
		builder.define(DATA_VIEWER, "");
	}

	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(0.45f);
	}

	@Override
	protected int getBaseExperienceReward() {
		return isHarmless() ? 0 : super.getBaseExperienceReward();
	}

	public String getViewer() {
		return entityData.get(DATA_VIEWER);
	}

	public void setViewer(String player) {
		entityData.set(DATA_VIEWER, player);
	}

	public boolean isHarmless() {
		return entityData.get(DATA_HARMLESS);
	}

	public void setHarmless(boolean harmless) {
		if (harmless) {
			lifeSpan = 1200;
		}
		entityData.set(DATA_HARMLESS, harmless);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide && tickCount > lifeSpan) {
			discard();
		}
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		return !isHarmless() && super.doHurtTarget(target);
	}
}
