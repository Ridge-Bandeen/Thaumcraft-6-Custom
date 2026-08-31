package thaumcraft.api.casters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import thaumcraft.api.casters.NodeSetting.NodeSettingIntList;
import thaumcraft.api.casters.NodeSetting.NodeSettingIntRange;

/**
 * Plain JUnit coverage for {@link NodeSetting} and its two {@code INodeSettingType} implementations - the
 * clamping/stepping logic every focus's power/duration/option setting (see every {@code FocusEffect}
 * ported so far) is built on. No Level/player involved anywhere in this class.
 */
class NodeSettingTest {

	@Test
	void intRangeDefaultsToMinimum() {
		NodeSetting setting = new NodeSetting("power", "focus.common.power", new NodeSettingIntRange(1, 5));
		assertEquals(1, setting.getValue());
	}

	@Test
	void intRangeIncrementAndDecrementClampAtBounds() {
		NodeSetting setting = new NodeSetting("power", "focus.common.power", new NodeSettingIntRange(1, 3));

		setting.decrement();
		assertEquals(1, setting.getValue(), "decrementing below the minimum should clamp, not go below it");

		setting.increment();
		setting.increment();
		setting.increment();
		setting.increment();
		assertEquals(3, setting.getValue(), "incrementing past the maximum should clamp, not go above it");
	}

	@Test
	void intRangeSetValueConvergesOnAnInBoundsTarget() {
		NodeSetting setting = new NodeSetting("power", "focus.common.power", new NodeSettingIntRange(1, 5));
		setting.setValue(4);
		assertEquals(4, setting.getValue());
	}

	@Test
	void intRangeSetValueOnAnOutOfBoundsTargetSettlesAtTheNearestClampedBound() {
		// setValue increments from 0 until getValue() matches or the value stops changing (clamped) -
		// for an out-of-range target it settles at whichever bound is nearest to where it got stuck, not
		// an infinite loop or an exception.
		NodeSetting setting = new NodeSetting("power", "focus.common.power", new NodeSettingIntRange(2, 5));
		setting.setValue(100);
		assertEquals(5, setting.getValue(), "should settle at the max instead of looping forever chasing an unreachable target");
	}

	@Test
	void intListDefaultsToFirstEntry() {
		NodeSetting setting = new NodeSetting("option", "focus.common.options",
				new NodeSettingIntList(new int[] { 10, 20, 30 }, new String[] { "a", "b", "c" }));
		assertEquals(10, setting.getValue());
	}

	@Test
	void intListStepsThroughIndicesAndClampsAtTheEnds() {
		NodeSetting setting = new NodeSetting("option", "focus.common.options",
				new NodeSettingIntList(new int[] { 10, 20, 30 }, new String[] { "a", "b", "c" }));

		setting.increment();
		assertEquals(20, setting.getValue());
		setting.increment();
		assertEquals(30, setting.getValue());
		setting.increment();
		assertEquals(30, setting.getValue(), "incrementing past the last index should clamp there, not wrap or throw");

		setting.decrement();
		setting.decrement();
		setting.decrement();
		setting.decrement();
		assertEquals(10, setting.getValue(), "decrementing past the first index should clamp there, not wrap or throw");
	}

	@Test
	void researchGateDefaultsToNullMeaningAlwaysVisible() {
		NodeSetting setting = new NodeSetting("power", "focus.common.power", new NodeSettingIntRange(1, 5));
		assertEquals(null, setting.getResearch());

		NodeSetting gated = new NodeSetting("option", "focus.common.options",
				new NodeSettingIntList(new int[] { 0, 1 }, new String[] { "a", "b" }), "FOCUSADVANCED");
		assertEquals("FOCUSADVANCED", gated.getResearch());
	}
}
