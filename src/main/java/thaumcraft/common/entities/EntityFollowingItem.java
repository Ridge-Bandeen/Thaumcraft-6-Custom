package thaumcraft.common.entities;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Replaces the old (1.12.2) {@code EntityFollowingItem}. A plain vanilla {@link ItemEntity} that homes in on
 * either a fixed point or a live target entity instead of just falling - used by
 * {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#COLLECTOR} to pull harvested drops (and,
 * on a kill, dropped essentia crystals) straight to the player instead of leaving them on the ground.
 *
 * The original was a two-level hierarchy ({@code EntitySpecialItem extends EntityItem}, explosion-immune and
 * always-falling, then {@code EntityFollowingItem extends EntitySpecialItem} adding the homing). Collapsed to
 * one class here - {@code EntitySpecialItem}'s only other real behaviour (falling under manual gravity
 * instead of an initial upward toss) isn't needed by anything else in this port, so there's no second
 * subclass to justify keeping the split.
 *
 * Dropped: the client-side {@code FXDispatcher} particle trail drawn every tick while homing (the usual
 * client-FX boundary this port has kept all session) and the original's manual network spawn-data sync
 * ({@code IEntityAdditionalSpawnData}) - {@link Entity}'s own synced-entity-data system already replicates
 * {@code ItemEntity}'s held stack to the client, and the homing target/destination only ever needs to be
 * known server-side (the client just sees the entity move, same as any other server-authoritative entity).
 */
public class EntityFollowingItem extends ItemEntity {

	private double targetX;
	private double targetY;
	private double targetZ;
	private int age = 20;
	@Nullable
	private Entity followTarget;

	public EntityFollowingItem(EntityType<? extends EntityFollowingItem> type, Level level) {
		super(type, level);
	}

	/** Homes toward a live entity (tracked every tick, so it still finds a moving player) - the shape used
	 * by every real call site this port ports (COLLECTOR's harvest-drop and kill-drop redirection). */
	public EntityFollowingItem(Level level, double x, double y, double z, ItemStack stack, Entity target) {
		this(level, x, y, z, stack);
		this.followTarget = target;
		this.targetX = target.getX();
		this.targetY = target.getBoundingBox().minY + target.getBbHeight() / 2.0;
		this.targetZ = target.getZ();
		this.noPhysics = true;
	}

	/** Homes toward a fixed point instead of a live entity - kept as real API (the original's own third
	 * constructor shape) even though no shipped call site uses it yet. */
	public EntityFollowingItem(Level level, double x, double y, double z, ItemStack stack, double tx, double ty, double tz) {
		this(level, x, y, z, stack);
		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		this.noPhysics = true;
	}

	private EntityFollowingItem(Level level, double x, double y, double z, ItemStack stack) {
		this(thaumcraft.common.registry.ThaumcraftEntities.FOLLOWING_ITEM.get(), level);
		this.setPos(x, y, z);
		this.setItem(stack);
	}

	@Override
	public void tick() {
		if (followTarget != null) {
			if (followTarget.isRemoved()) {
				followTarget = null;
			} else {
				targetX = followTarget.getX();
				targetY = followTarget.getBoundingBox().minY + followTarget.getBbHeight() / 2.0;
				targetZ = followTarget.getZ();
			}
		}
		if (targetX != 0.0 || targetY != 0.0 || targetZ != 0.0) {
			double xd = targetX - getX();
			double yd = targetY - getY();
			double zd = targetZ - getZ();
			if (age > 1) {
				age--;
			}
			double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);
			if (distance > 0.5) {
				distance *= age;
				setDeltaMovement(xd / distance, yd / distance, zd / distance);
			} else {
				setDeltaMovement(getDeltaMovement().scale(0.1));
				targetX = 0.0;
				targetY = 0.0;
				targetZ = 0.0;
				followTarget = null;
				noPhysics = false;
			}
		}
		super.tick();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount);
	}
}
