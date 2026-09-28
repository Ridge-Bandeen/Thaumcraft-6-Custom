package thaumcraft.api.research;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces the old (1.12.2) {@code ScanEntity} - a scan target that matches a kind of entity.
 *
 * <p>Two real forms, matching the original's own two:
 * <ul>
 * <li>{@link #ScanEntity(String, Class, boolean)} - the original's own real shape (restored 2026-09-27): an entity
 * class, plus whether subclasses/implementers count too. Several of the original's own real scan targets depend on
 * that inheritance - {@code "!ORMOB"} is any {@code IEldritchMob}, {@code "!ORBOSS"} any Thaumcraft boss,
 * {@code "f_SPIDER"} any spider (cave spiders and mind spiders included), {@code "f_arrow"} any arrow - which an
 * exact-type match could never express.</li>
 * <li>{@link #ScanEntity(String, EntityType)} - an exact {@link EntityType}, kept for callers that want one.</li>
 * </ul>
 *
 * Drops the original's generic {@code EntityTagsNBT} condition system (matching by an NBT sub-tag): no scan
 * target the original actually registers uses it.
 */
public class ScanEntity implements IScanThing {

	private final String research;
	private final EntityType<?> entityType;
	private final Class<?> entityClass;
	private final boolean inherited;

	public ScanEntity(String research, EntityType<?> entityType) {
		this.research = research;
		this.entityType = entityType;
		this.entityClass = null;
		this.inherited = false;
	}

	public ScanEntity(String research, Class<?> entityClass, boolean inheritedClasses) {
		this.research = research;
		this.entityType = null;
		this.entityClass = entityClass;
		this.inherited = inheritedClasses;
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (!(obj instanceof Entity entity)) return false;
		if (entityType != null) return entity.getType() == entityType;
		return inherited ? entityClass.isInstance(entity) : entityClass == entity.getClass();
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		return research;
	}
}
