package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectBreak;
import thaumcraft.common.items.casters.foci.FocusMediumTouch;

/**
 * A fixed Touch + Break focus - see {@link ItemFocusFire}'s doc for why this is a fixed combination
 * instead of a player-assembled one.
 */
public class ItemFocusBreak extends ItemFocus {

	public ItemFocusBreak(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumTouch(), new FocusEffectBreak());
	}
}
