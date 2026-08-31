package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectHeal;
import thaumcraft.common.items.casters.foci.FocusMediumCloud;

/**
 * A fixed Cloud + Heal focus - the last of the five mediums, paired with the existing FocusEffectHeal for
 * a lingering healing (undead-hurting) zone. Same "no Focal Manipulator yet" simplification as the rest of
 * the roster (see {@link ItemFocusFire}).
 */
public class ItemFocusSanctuary extends ItemFocus {

	public ItemFocusSanctuary(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumCloud(), new FocusEffectHeal());
	}
}
