package thaumcraft.common.entities.projectile;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code EntityGrapple} - the hookshot projectile behind
 * {@link thaumcraft.common.items.tools.ItemGrappleGun}. Flies until it hits something, then pulls the
 * thrower toward the impact point using the same spring-like velocity formula the original used (stronger
 * pull the further away, capped once close). Extends vanilla's {@link ThrowableProjectile}, same base every
 * other simple projectile in this port uses.
 *
 * {@link #ACTIVE} (thrower UUID -> grapple entity id) replaces the original's own static map, keyed by UUID
 * instead of a raw int entity id (more robust across id reuse) - it exists for two things: only one grapple
 * can pull a given player at a time ({@link #discardActiveFor} is called before firing a new one, moved to
 * fire-time instead of the original's own reactive every-tick check), and it's what
 * {@code ItemGrappleGun}'s "loaded" item-model override reads via {@link #hasActive} - no stored NBT/data
 * component flag needed on the item itself, since "is there a live grapple for this player" is already real,
 * live, queryable state.
 *
 * As the original, a pull also resets the server's "airborne too long" counter for the player, so a long pull never
 * gets them kicked for flying.
 */
public class EntityGrapple extends ThrowableProjectile {

	private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

	private boolean pulling;
	private boolean boosted;

	public EntityGrapple(EntityType<? extends EntityGrapple> type, Level level) {
		super(type, level);
	}

	public EntityGrapple(LivingEntity thrower, Level level) {
		super(ThaumcraftEntities.GRAPPLE.get(), thrower, level);
	}

	/** Discards any grapple this thrower already has active, so only one can ever be pulling them. */
	public static void discardActiveFor(LivingEntity thrower) {
		Integer existingId = ACTIVE.remove(thrower.getUUID());
		if (existingId != null && thrower.level().getEntity(existingId) instanceof EntityGrapple grapple) {
			grapple.discard();
		}
	}

	public static boolean hasActive(LivingEntity thrower) {
		return ACTIVE.containsKey(thrower.getUUID());
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		// no synced state - purely a server-side physics helper, nothing client-visible to sync.
	}

	@Override
	protected double getDefaultGravity() {
		return pulling ? 0.0 : 0.03;
	}

	@Override
	public void tick() {
		super.tick();
		Entity owner = getOwner();
		if (owner == null || (!pulling && tickCount > 30)) {
			discardSelf(owner);
			return;
		}
		if (!level().isClientSide && pulling) {
			if (owner.isShiftKeyDown()) {
				discardSelf(owner);
				return;
			}
			pull((LivingEntity) owner);
		}
	}

	private void discardSelf(Entity owner) {
		if (owner != null) {
			ACTIVE.remove(owner.getUUID());
		}
		discard();
	}

	/** Same spring formula as the original: pull strength grows the closer you already are (capped once very
	 * close so you don't overshoot violently past the anchor), plus a one-time upward boost on first pull to
	 * get the thrower off the ground. */
	private void pull(LivingEntity owner) {
		double dis = owner.distanceTo(this);
		double mx = getX() - owner.getX();
		double my = getY() - owner.getY();
		double mz = getZ() - owner.getZ();
		double dd = dis < 8.0 ? dis * (8.0 - dis) : dis;
		dd = Math.max(1.0E-9, dd);
		mx /= dd * 5.0;
		my /= dd * 5.0;
		mz /= dd * 5.0;
		Vec3 pull = new Vec3(mx, my, mz);
		if (pull.lengthSqr() > 0.0625) {
			pull = pull.normalize().scale(0.25);
		}
		Vec3 motion = owner.getDeltaMovement().add(pull.x, pull.y + 0.033, pull.z);
		if (!boosted) {
			motion = motion.add(0, 0.4, 0);
			boosted = true;
		}
		owner.setDeltaMovement(motion);
		owner.resetFallDistance();
		owner.hurtMarked = true;
		if (owner instanceof net.minecraft.server.level.ServerPlayer sp) sp.connection.aboveGroundTickCount = 0;
	}

	@Override
	protected void onHitEntity(EntityHitResult result) {
		super.onHitEntity(result);
		land(result.getLocation());
	}

	@Override
	protected void onHitBlock(BlockHitResult result) {
		super.onHitBlock(result);
		land(result.getLocation());
	}

	private void land(Vec3 pos) {
		if (level().isClientSide) {
			return;
		}
		pulling = true;
		setDeltaMovement(Vec3.ZERO);
		setPos(pos.x, pos.y, pos.z);
		Entity owner = getOwner();
		if (owner != null) {
			ACTIVE.put(owner.getUUID(), getId());
		}
	}
}
