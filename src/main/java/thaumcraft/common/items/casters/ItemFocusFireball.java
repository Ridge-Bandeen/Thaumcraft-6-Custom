package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusMediumProjectile;

/**
 * A fixed Projectile + Fire focus - the wand's first entity-backed focus (see
 * {@link thaumcraft.common.entities.projectile.EntityFocusProjectile}'s doc comment). Same "no Focal
 * Manipulator yet" simplification as the rest of the roster (see {@link ItemFocusFire}): flies straight
 * until it hits something, then burns whatever it hits.
 */
public class ItemFocusFireball extends ItemFocus {

	public ItemFocusFireball(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumProjectile(), new FocusEffectFire());
	}
}
