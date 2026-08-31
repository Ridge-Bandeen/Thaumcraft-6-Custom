package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.items.casters.foci.FocusEffectCurse;
import thaumcraft.common.items.casters.foci.FocusMediumMine;

/**
 * A fixed Mine + Curse focus - the wand's first "trap" focus (see
 * {@link thaumcraft.common.entities.projectile.EntityFocusMine}'s doc comment). Plants a mine that arms on
 * landing and curses the next enemy (the "target" setting's default) that wanders within range. Same "no
 * Focal Manipulator yet" simplification as the rest of the roster (see {@link ItemFocusFire}).
 */
public class ItemFocusTrap extends ItemFocus {

	public ItemFocusTrap(Properties properties) {
		super(properties);
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of(new FocusMediumRoot(), new FocusMediumMine(), new FocusEffectCurse());
	}
}
