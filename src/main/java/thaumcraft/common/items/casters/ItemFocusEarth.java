package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectEarth;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A fixed Touch + Earth focus - same "no Focal Manipulator yet" simplification as the rest of the
 * roster (see {@link ItemFocusFire}). Plain physical damage on the target.
 */
public class ItemFocusEarth extends ItemFocus {

	public ItemFocusEarth(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectEarth());
	}
}
