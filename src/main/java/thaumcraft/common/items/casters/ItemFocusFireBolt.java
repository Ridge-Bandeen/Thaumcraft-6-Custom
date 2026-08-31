package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusMediumBolt;

/**
 * A fixed Bolt + Fire focus - same "no Focal Manipulator yet, so ship one fixed combination per
 * item" simplification as {@link ItemFocusFire}/{@link ItemFocusFrost}/{@link ItemFocusBreak}, but
 * this one exists specifically to exercise {@link FocusMediumBolt} rather than
 * {@link thaumcraft.common.items.casters.foci.FocusMediumTouch}: same Fire effect, but aimed and
 * fired at 16-block range instead of Touch's 5-block melee reach.
 */
public class ItemFocusFireBolt extends ItemFocus {

	public ItemFocusFireBolt(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumBolt(), new FocusEffectFire());
	}
}
