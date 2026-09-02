package thaumcraft.common.items.casters;

import java.util.List;

import thaumcraft.api.casters.IFocusElement;

/**
 * Replaces the original 1.12.2 {@code ItemFocus("focus_1"/"focus_2"/"focus_3", complexity)} - in the
 * original there was no per-effect focus item at all, just these three blank, tiered canvases a player
 * assembled a real node graph onto at a Focal Manipulator. The 10 fixed-chain foci this port shipped
 * earlier this session ({@link ItemFocusFire} and its siblings) were an invented stand-in for the then-
 * missing Manipulator - kept alongside these (not replaced by them) now that assembly is real again, see
 * {@link ItemFocus}'s own doc comment.
 *
 * {@link #createNodes} returns an empty chain (a blank focus does nothing until a real graph is assembled
 * onto it and stored in {@code ASSEMBLED_FOCUS}) - {@link #getMaxComplexity} is the real per-tier cap a
 * Manipulator checks before accepting a build.
 */
public class ItemFocusBlank extends ItemFocus {

	private final int maxComplexity;

	public ItemFocusBlank(Properties properties, int maxComplexity) {
		super(properties);
		this.maxComplexity = maxComplexity;
	}

	@Override
	protected List<IFocusElement> createNodes() {
		return List.of();
	}

	@Override
	public int getMaxComplexity() {
		return maxComplexity;
	}
}
