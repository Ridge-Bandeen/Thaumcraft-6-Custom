package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;
import thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser;

/** Replaces the old (1.12.2) {@code AidPortal} - direct translation, all 3 real variants
 * ({@link AidPortalEnd}/{@link AidPortalNether}/{@link AidPortalCrimson}). {@code AidPortalCrimson} was
 * deliberately left out until 2026-09-15 (its real trigger, {@link EntityCultistPortalLesser}, hadn't been
 * ported yet at the time) - restored now that it's real and shipped. */
public abstract class AidPortal implements ITheorycraftAid {

	private final Object portal;

	protected AidPortal(Object portal) {
		this.portal = portal;
	}

	@Override
	public Object getAidObject() {
		return portal;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<TheorycraftCard>[] getCards() {
		return new Class[] {CardPortal.class};
	}

	public static class AidPortalEnd extends AidPortal {
		public AidPortalEnd() {
			super(Blocks.END_PORTAL);
		}
	}

	public static class AidPortalNether extends AidPortal {
		public AidPortalNether() {
			super(Blocks.NETHER_PORTAL);
		}
	}

	public static class AidPortalCrimson extends AidPortal {
		public AidPortalCrimson() {
			super(EntityCultistPortalLesser.class);
		}
	}
}
