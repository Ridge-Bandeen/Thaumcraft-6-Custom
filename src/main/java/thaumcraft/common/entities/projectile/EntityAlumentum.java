package thaumcraft.common.entities.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityAlumentum} - the thrown-fire-bomb projectile behind
 * {@link thaumcraft.common.items.consumables.ItemAlumentum}. Extends vanilla's
 * {@link ThrowableItemProjectile} (the same base {@code Snowball}/{@code ThrownEgg} use - a thrown item
 * that renders as a spinning icon of whatever {@code getItem()} returns) rather than the generic
 * {@link net.minecraft.world.entity.projectile.ThrowableProjectile} this port's other projectiles use,
 * since Alumentum (unlike a wand focus) is a real, visible held item being thrown.
 *
 * Simplified from the original: the explosion uses {@code Level.ExplosionInteraction.MOB} (damages
 * entities and sets fire, doesn't destroy blocks) instead of the original's block-destroying explosion -
 * consistent with this project's general preference for damage-only magic effects over terrain griefing,
 * and it keeps the GameTest coverage predictable (no block-state cleanup to account for). No particle
 * trail/impact FX, same as every other projectile in this project.
 */
public class EntityAlumentum extends ThrowableItemProjectile {

	public EntityAlumentum(EntityType<? extends EntityAlumentum> type, Level level) {
		super(type, level);
	}

	public EntityAlumentum(LivingEntity thrower, Level level) {
		super(ThaumcraftEntities.ALUMENTUM.get(), thrower, level);
	}

	@Override
	protected Item getDefaultItem() {
		return ItemsTC.alumentum.get();
	}

	@Override
	protected void onHit(HitResult result) {
		super.onHit(result);
		if (!level().isClientSide) {
			level().explode(this, getX(), getY(), getZ(), 1.1f, true, Level.ExplosionInteraction.MOB);
			discard();
		}
	}
}
