package thaumcraft.common.items.casters.foci;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusMediumBolt}. The original subclassed {@code FocusMediumTouch}
 * only to widen its range (5 -> 16 blocks, a real ranged attack rather than melee) and to broadcast a
 * "zap" network FX packet on execute() - the aiming/targeting logic itself was identical, reused as-is.
 * Our ported {@link FocusMediumTouch#getRange()} was already written as an overridable hook for exactly
 * this, so this class is just that override plus the metadata (research/key/complexity/aspect); the FX
 * broadcast is dropped for the same reason it was dropped everywhere else in this package - the network
 * layer isn't ported yet, and it never affected gameplay, only the client-side beam visual.
 */
public class FocusMediumBolt extends FocusMediumTouch {

	@Override
	public String getResearch() {
		return "FOCUSBOLT";
	}

	@Override
	public String getKey() {
		return "thaumcraft.BOLT";
	}

	@Override
	public int getComplexity() {
		return 5;
	}

	@Override
	public Aspect getAspect() {
		return Aspect.ENERGY;
	}

	@Override
	protected double getRange() {
		return 16.0;
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		return true;
	}
}
