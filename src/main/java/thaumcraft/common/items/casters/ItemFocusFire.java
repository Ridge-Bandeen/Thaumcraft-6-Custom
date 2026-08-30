package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A single fixed Touch + Fire focus - not from the original 1.12.2 item roster (which had no
 * per-combination items; foci were assembled at a Focal Manipulator, not ported). This is the minimal
 * "prove the casting engine actually works end to end" focus until real focus assembly exists - see the
 * port-tracking memory notes.
 */
public class ItemFocusFire extends ItemFocus {

	public ItemFocusFire(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		// A blank-constructor FocusMediumRoot() is required as node 0: FocusPackage#initialize() only
		// wires up real cast-origin trajectories/targets (from the caster's eye position and look
		// direction) when it finds one there with no targets set yet.
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectFire());
	}
}
