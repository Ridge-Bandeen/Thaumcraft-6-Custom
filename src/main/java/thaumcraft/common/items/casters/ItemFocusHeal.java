package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectHeal;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A fixed Touch + Heal focus - same "no Focal Manipulator yet" simplification as the rest of the
 * roster (see {@link ItemFocusFire}). Heals the living, hurts the undead.
 */
public class ItemFocusHeal extends ItemFocus {

	public ItemFocusHeal(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectHeal());
	}
}
