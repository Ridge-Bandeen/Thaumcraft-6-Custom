package thaumcraft.common.items.casters.foci;

import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusModSplitTrajectory} - direct translation, just passes the parent
 * node's own real trajectories through.
 */
public class FocusModSplitTrajectory extends FocusModSplit {

	@Override
	public String getResearch() {
		return "FOCUSSPLIT";
	}

	@Override
	public String getKey() {
		return "thaumcraft.SPLITTRAJECTORY";
	}

	@Override
	public int getComplexity() {
		return 5;
	}

	@Override
	public EnumSupplyType[] mustBeSupplied() {
		return new EnumSupplyType[] { EnumSupplyType.TRAJECTORY };
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TRAJECTORY };
	}

	@Override
	public Trajectory[] supplyTrajectories() {
		return getParent().supplyTrajectories();
	}

	@Override
	public boolean execute() {
		return true;
	}
}
