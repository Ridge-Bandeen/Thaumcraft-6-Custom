package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A fixed Touch + Flux focus - same "no Focal Manipulator yet" simplification as the rest of the
 * roster (see {@link ItemFocusFire}). Plain indirect-magic damage on the target.
 */
public class ItemFocusFlux extends ItemFocus {

	public ItemFocusFlux(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectFlux());
	}
}
