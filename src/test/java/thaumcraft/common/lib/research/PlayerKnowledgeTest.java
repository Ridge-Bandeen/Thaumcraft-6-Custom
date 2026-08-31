package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchFlag;
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumResearchStatus;

/**
 * Plain JUnit coverage for {@link PlayerKnowledge} - none of its logic touches a live Level/player (the
 * one {@code HolderLookup.Provider registries} parameter on {@code serializeNBT} is unused in the body,
 * so tests pass {@code null} for it rather than needing a real one).
 */
class PlayerKnowledgeTest {

	private PlayerKnowledge knowledge;

	@BeforeEach
	void setUp() {
		knowledge = new PlayerKnowledge();
	}

	@Test
	void newPlayerDefaultsToKnowingFirstStepsStageTwo() {
		// Documents the deliberate simplification in the constructor's own doc comment: there's no
		// research-granting path yet (no Thaumonomicon/scanning), so every new player starts knowing
		// FIRSTSTEPS@2 or every recipe ported so far becomes uncraftable. This test exists so removing
		// that default (once a real granting path exists) forces updating this test too, same pattern as
		// AspectListTest's ItemStack-constructor-stub canary.
		assertTrue(knowledge.isResearchKnown("FIRSTSTEPS"));
		assertTrue(knowledge.isResearchKnown("FIRSTSTEPS@2"));
		assertFalse(knowledge.isResearchKnown("FIRSTSTEPS@3"), "the default only grants stage 2, not further stages");
	}

	@Test
	void clearWipesTheDefaultTooForTestIsolation() {
		knowledge.clear();
		assertFalse(knowledge.isResearchKnown("FIRSTSTEPS"), "clear() should remove even the constructor's default grant");
	}

	@Test
	void addResearchGrantsStageZeroKnowledge() {
		knowledge.clear();
		assertTrue(knowledge.addResearch("BASEAUROMANCY"));

		assertTrue(knowledge.isResearchKnown("BASEAUROMANCY"));
		assertTrue(knowledge.isResearchComplete("BASEAUROMANCY"));
		assertEquals(0, knowledge.getResearchStage("BASEAUROMANCY"));
		assertEquals(EnumResearchStatus.COMPLETE, knowledge.getResearchStatus("BASEAUROMANCY"));
	}

	@Test
	void unknownResearchReadsAsUnknownNotAnException() {
		knowledge.clear();
		assertFalse(knowledge.isResearchKnown("NEVERGRANTED"));
		assertFalse(knowledge.isResearchComplete("NEVERGRANTED"));
		assertEquals(-1, knowledge.getResearchStage("NEVERGRANTED"));
		assertEquals(EnumResearchStatus.UNKNOWN, knowledge.getResearchStatus("NEVERGRANTED"));
	}

	@Test
	void emptyOrNullResearchQueryAlwaysKnown() {
		// isResearchKnown's doc comment: "Passing in an empty string will always return true (used by
		// crafting recipes)" - e.g. a recipe with no research requirement at all.
		assertTrue(knowledge.isResearchKnown(""));
		assertTrue(knowledge.isResearchComplete(null));
		assertTrue(knowledge.isResearchComplete(""));
	}

	@Test
	void stageQualifierSyntaxComparesNumerically() {
		knowledge.clear();
		knowledge.setResearchStage("FOCUSADVANCED", 3);

		assertTrue(knowledge.isResearchKnown("FOCUSADVANCED"), "bare key (no @) should just check known-ness, any stage");
		assertTrue(knowledge.isResearchKnown("FOCUSADVANCED@1"));
		assertTrue(knowledge.isResearchKnown("FOCUSADVANCED@3"), "exactly the recorded stage should count as known");
		assertFalse(knowledge.isResearchKnown("FOCUSADVANCED@4"), "a stage past what's recorded should not be known yet");
	}

	@Test
	void malformedStageQualifierFallsBackToBareKeyCheck() {
		knowledge.clear();
		knowledge.addResearch("FOCUSADVANCED");
		// "@notanumber" can't be parsed as a stage - isResearchKnown should degrade to a plain known-ness
		// check on the key rather than throwing.
		assertTrue(knowledge.isResearchKnown("FOCUSADVANCED@notanumber"));
	}

	@Test
	void removeResearchClearsBothProgressAndFlags() {
		knowledge.clear();
		knowledge.addResearch("BASEAUROMANCY");
		knowledge.setResearchFlag("BASEAUROMANCY", EnumResearchFlag.PAGE);
		assertTrue(knowledge.hasResearchFlag("BASEAUROMANCY", EnumResearchFlag.PAGE));

		knowledge.removeResearch("BASEAUROMANCY");

		assertFalse(knowledge.isResearchKnown("BASEAUROMANCY"));
		assertFalse(knowledge.hasResearchFlag("BASEAUROMANCY", EnumResearchFlag.PAGE), "removing the research should drop its flags too");
	}

	@Test
	void researchFlagsSetClearAndQuery() {
		knowledge.clear();
		knowledge.addResearch("BASEAUROMANCY");

		assertFalse(knowledge.hasResearchFlag("BASEAUROMANCY", EnumResearchFlag.POPUP));
		knowledge.setResearchFlag("BASEAUROMANCY", EnumResearchFlag.POPUP);
		assertTrue(knowledge.hasResearchFlag("BASEAUROMANCY", EnumResearchFlag.POPUP));

		knowledge.clearResearchFlag("BASEAUROMANCY", EnumResearchFlag.POPUP);
		assertFalse(knowledge.hasResearchFlag("BASEAUROMANCY", EnumResearchFlag.POPUP));
	}

	@Test
	void knowledgePointsAccumulateAndFloorByProgression() {
		knowledge.clear();
		// THEORY's progression is 32 - getKnowledge() should floor-divide the raw total by it.
		knowledge.addKnowledge(EnumKnowledgeType.THEORY, null, 50);
		assertEquals(50, knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, null));
		assertEquals(1, knowledge.getKnowledge(EnumKnowledgeType.THEORY, null), "50 / 32 should floor to 1");

		knowledge.addKnowledge(EnumKnowledgeType.THEORY, null, 50);
		assertEquals(100, knowledge.getKnowledgeRaw(EnumKnowledgeType.THEORY, null));
		assertEquals(3, knowledge.getKnowledge(EnumKnowledgeType.THEORY, null), "100 / 32 should floor to 3");
	}

	@Test
	void knowledgePointsNeverGoNegative() {
		knowledge.clear();
		knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, null, 10);
		knowledge.addKnowledge(EnumKnowledgeType.OBSERVATION, null, -100);
		assertEquals(0, knowledge.getKnowledgeRaw(EnumKnowledgeType.OBSERVATION, null), "should clamp at 0, not go negative");
	}

	@Test
	void nbtRoundTripPreservesResearchFlagsAndKnowledge() {
		knowledge.clear();
		knowledge.setResearchStage("FOCUSADVANCED", 2);
		knowledge.setResearchFlag("FOCUSADVANCED", EnumResearchFlag.PAGE);
		knowledge.setResearchFlag("FOCUSADVANCED", EnumResearchFlag.RESEARCH);
		knowledge.addKnowledge(EnumKnowledgeType.THEORY, null, 40);

		CompoundTag tag = knowledge.serializeNBT(null);

		PlayerKnowledge restored = new PlayerKnowledge();
		restored.deserializeNBT(null, tag);

		assertEquals(2, restored.getResearchStage("FOCUSADVANCED"));
		assertTrue(restored.hasResearchFlag("FOCUSADVANCED", EnumResearchFlag.PAGE));
		assertTrue(restored.hasResearchFlag("FOCUSADVANCED", EnumResearchFlag.RESEARCH));
		assertFalse(restored.hasResearchFlag("FOCUSADVANCED", EnumResearchFlag.POPUP));
		assertEquals(40, restored.getKnowledgeRaw(EnumKnowledgeType.THEORY, null));
		assertFalse(restored.isResearchKnown("FIRSTSTEPS"), "deserializeNBT clears first, so the constructor's default grant should not survive a round trip that didn't include it");
	}
}
