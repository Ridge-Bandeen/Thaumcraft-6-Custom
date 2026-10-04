package thaumcraft.common.lib.utils;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The original's {@code EntityUtils} checks the foci and constructs use: friendliness, the view cone, line of sight. */
public final class EntityChecks {

	private EntityChecks() {
	}

	/** The original's isFriendly: the same entity, riding together, on one team, owned by {@code source}, or a player
	 * while PvP is off. */
	public static boolean isFriendly(Entity source, Entity target) {
		if (source == null || target == null) return false;
		if (source.getId() == target.getId()) return true;
		if (source.isPassengerOfSameVehicle(target)) return true;
		if (source.isAlliedTo(target)) return true;
		if (target instanceof OwnableEntity ownable && ownable.getOwner() != null && ownable.getOwner().equals(source)) return true;
		return target instanceof Player && !target.level().isClientSide && target.level().getServer() != null
				&& !target.level().getServer().isPvpAllowed();
	}

	/** The original's isVisibleTo: whether {@code target}'s middle lies in a cone of {@code fov} radians (full aperture)
	 * from {@code apex} along {@code look}, no further than {@code range}. */
	public static boolean isInCone(float fov, Vec3 apex, Vec3 look, Entity target, float range) {
		Vec3 toTarget = new Vec3(target.getX(), target.getBoundingBox().minY + target.getBbHeight() / 2.0f, target.getZ()).subtract(apex);
		Vec3 axis = look.normalize().scale(range);
		double len = toTarget.length();
		if (len == 0 || axis.lengthSqr() == 0) return false;
		double dot = toTarget.dot(axis);
		if (dot / len / axis.length() <= Math.cos(fov / 2.0f)) return false;
		return dot / axis.length() < axis.length();
	}

	/** The original's canEntityBeSeen: no block between {@code from} and {@code target}'s eyes. */
	public static boolean canSee(Entity viewer, Vec3 from, Entity target) {
		Vec3 to = new Vec3(target.getX(), target.getEyeY(), target.getZ());
		return viewer.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer)).getType() == HitResult.Type.MISS;
	}
}
