package thaumcraft.api.research;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces the old (1.12.2) {@code ScanEntity} - a scan target that matches by entity type. Re-keyed from a
 * raw {@code Class} (with a separate {@code inheritedClasses} flag for "or any subclass") to a real
 * {@link EntityType}, the same idiom {@code ThaumcraftEntityTags} already established for this port's other
 * entity-keyed lookup - compiler-checked, no raw-class/subclass-inheritance matching needed since a modern
 * {@link EntityType} is already the canonical "what kind of entity is this" identity.
 *
 * Drops the original's generic {@code EntityTagsNBT} condition system (matching by an NBT sub-tag, e.g. a
 * powered Creeper) rather than porting it - {@code ThaumcraftEntityTags} already chose to special-case its
 * own one real NBT-driven case (a powered Creeper) as a direct field check instead of a generic mechanism,
 * once Elder Guardian turned out to be its own real {@link EntityType} rather than an NBT flag; this class
 * follows the same precedent. A future scan target that genuinely needs a live-state condition (not just
 * "which entity type") can filter in its own {@code checkThing} override directly.
 */
public class ScanEntity implements IScanThing {

	private final String research;
	private final EntityType<?> entityType;

	public ScanEntity(String research, EntityType<?> entityType) {
		this.research = research;
		this.entityType = entityType;
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		return obj instanceof Entity entity && entity.getType() == entityType;
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
