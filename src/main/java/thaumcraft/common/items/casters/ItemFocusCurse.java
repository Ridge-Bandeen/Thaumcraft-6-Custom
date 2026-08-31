package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectCurse;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A fixed Touch + Curse focus - same "no Focal Manipulator yet" simplification as the rest of the
 * roster (see {@link ItemFocusFire}). Damages the target and stacks poison plus a cascade of debuffs.
 */
public class ItemFocusCurse extends ItemFocus {

	public ItemFocusCurse(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectCurse());
	}
}
