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
 * While it homes it trails small blue bubbles (the original's type 10, which every shipped caller used: the
 * crucibleBubble effect, 0.33/0.33/1). The client learns that it is homing from a synced flag; the target itself only
 * matters on the server.
 */
public class EntityFollowingItem extends ItemEntity {

	private static final net.minecraft.network.syncher.EntityDataAccessor<Boolean> HOMING = net.minecraft.network.syncher.SynchedEntityData
			.defineId(EntityFollowingItem.class, net.minecraft.network.syncher.EntityDataSerializers.BOOLEAN);

	private double targetX;
	private double targetY;
	private double targetZ;
	private int age = 20;
	@Nullable
	private Entity followTarget;

	public EntityFollowingItem(EntityType<? extends EntityFollowingItem> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(HOMING, false);
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
		this.entityData.set(HOMING, true);
	}

	/** Homes toward a fixed point instead of a live entity - kept as real API (the original's own third
	 * constructor shape) even though no shipped call site uses it yet. */
	public EntityFollowingItem(Level level, double x, double y, double z, ItemStack stack, double tx, double ty, double tz) {
		this(level, x, y, z, stack);
		this.targetX = tx;
		this.targetY = ty;
		this.targetZ = tz;
		this.noPhysics = true;
		this.entityData.set(HOMING, true);
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
				entityData.set(HOMING, false);
			}
		}
		if (level().isClientSide && entityData.get(HOMING)) {
			// the original's trail, from the last position, at the item's bobbing height
			float h = getBbHeight() / 2.0f + net.minecraft.util.Mth.sin(getAge() / 10.0f + bobOffs) * 0.1f + 0.1f;
			thaumcraft.client.fx.ClientFxHandlers.spawnCrucibleBubble(level(), xo + (random.nextFloat() - random.nextFloat()) * 0.125f,
					yo + h + (random.nextFloat() - random.nextFloat()) * 0.125f, zo + (random.nextFloat() - random.nextFloat()) * 0.125f, 0.33f, 0.33f, 1.0f);
		}
		super.tick();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount);
	}
}
