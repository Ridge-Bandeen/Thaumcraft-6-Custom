package thaumcraft.common.entities;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * The 1.12 {@code World#getClosestPlayerToEntity} replacement. That method filtered with
 * {@code EntitySelectors.CAN_AI_TARGET}, i.e. it never returned a creative-mode or spectator player - every
 * "nearest player" check in the originals (wisp, fire bat, taint swarm, flux rift, both cultist portals) relied on
 * it. The obvious 1.21 name, {@code Level#getNearestPlayer(Entity, double)}, does NOT do that: it hands
 * {@code false} to a flag that only excludes creative players when {@code true}, so it returns them (a trap
 * found 2026-09-25 when a fire bat homed in on a creative-mode player every tick).
 */
public final class PlayerTargeting {

	private PlayerTargeting() {
	}

	/** Nearest player within {@code distance} who is neither creative nor a spectator, or null. */
	@Nullable
	public static Player nearestTargetable(Level level, Entity from, double distance) {
		return level.getNearestPlayer(from.getX(), from.getY(), from.getZ(), distance, EntitySelector.NO_CREATIVE_OR_SPECTATOR);
	}
}
