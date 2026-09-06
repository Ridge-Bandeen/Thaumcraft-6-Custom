package thaumcraft.common.lib.research.theorycraft;

import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.TheorycraftCard;

/** Replaces the old (1.12.2) {@code AidPortal} - direct translation of its real vanilla-block variants only
 * ({@link AidPortalEnd}/{@link AidPortalNether}). The original's 3rd variant, {@code AidPortalCrimson},
 * triggered off a real {@code EntityCultistPortalLesser} - that entity hasn't been ported yet, so this
 * variant is deliberately left out rather than referencing something that doesn't exist; add it back once
 * that entity is real. */
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
}
