package thaumcraft.api.aspects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;

/**
 * Plain JUnit coverage (not GameTest) for {@link AspectList} - none of this needs a live Level/player, so
 * the real-game-boot overhead GameTest carries isn't worth paying. Run with {@code ./gradlew test}, wired
 * up via NeoForge's own {@code unitTest} moddev-gradle DSL (build.gradle's {@code neoForge.unitTest}
 * block) - that still runs under the real bootstrapped game environment (registries etc.), just without
 * spinning up a dedicated server per test the way GameTest does.
 */
class AspectListTest {

	@Test
	void addAccumulatesAndGetAmountReflectsIt() {
		AspectList list = new AspectList();
		list.add(Aspect.FIRE, 5);
		list.add(Aspect.FIRE, 3);
		assertEquals(8, list.getAmount(Aspect.FIRE));
		assertEquals(0, list.getAmount(Aspect.WATER), "an aspect never added should read back as 0, not null/exception");
	}

	@Test
	void reduceSucceedsWithEnoughAndFailsWithoutMutating() {
		AspectList list = new AspectList().add(Aspect.FIRE, 5);

		assertTrue(list.reduce(Aspect.FIRE, 3));
		assertEquals(2, list.getAmount(Aspect.FIRE));

		assertFalse(list.reduce(Aspect.FIRE, 100), "reduce should refuse to go negative");
		assertEquals(2, list.getAmount(Aspect.FIRE), "a failed reduce must not have mutated the amount");
	}

	@Test
	void removeDropsTheEntryEntirelyOnceItHitsZero() {
		AspectList list = new AspectList().add(Aspect.FIRE, 5);

		list.remove(Aspect.FIRE, 3);
		assertEquals(2, list.getAmount(Aspect.FIRE));
		assertEquals(1, list.size());

		list.remove(Aspect.FIRE, 10);
		assertEquals(0, list.getAmount(Aspect.FIRE));
		assertEquals(0, list.size(), "reducing below zero via remove() should drop the entry, not leave a negative amount");
	}

	@Test
	void mergeKeepsTheHigherAmountInsteadOfAdding() {
		AspectList list = new AspectList().add(Aspect.FIRE, 5);

		list.merge(Aspect.FIRE, 3);
		assertEquals(5, list.getAmount(Aspect.FIRE), "merging a lower amount should leave the existing (higher) amount unchanged");

		list.merge(Aspect.FIRE, 8);
		assertEquals(8, list.getAmount(Aspect.FIRE), "merging a higher amount should replace the existing (lower) amount");
	}

	@Test
	void sizeCountsDistinctAspectsAndVisSizeSumsAllAmounts() {
		AspectList list = new AspectList().add(Aspect.FIRE, 5).add(Aspect.WATER, 3);
		assertEquals(2, list.size());
		assertEquals(8, list.visSize());
	}

	@Test
	void listVariantsOfAddRemoveMergeOperatePerEntry() {
		AspectList base = new AspectList().add(Aspect.FIRE, 5).add(Aspect.WATER, 2);
		AspectList delta = new AspectList().add(Aspect.FIRE, 3).add(Aspect.EARTH, 4);

		AspectList added = base.copy().add(delta);
		assertEquals(8, added.getAmount(Aspect.FIRE), "FIRE was in both - should accumulate");
		assertEquals(2, added.getAmount(Aspect.WATER), "WATER was only in base - should be untouched");
		assertEquals(4, added.getAmount(Aspect.EARTH), "EARTH was only in delta - should be added fresh");

		AspectList removed = base.copy().remove(delta);
		assertEquals(2, removed.getAmount(Aspect.FIRE), "5 - 3 == 2");
		assertEquals(2, removed.getAmount(Aspect.WATER), "WATER wasn't in delta - should be untouched");

		AspectList merged = base.copy().merge(delta);
		assertEquals(5, merged.getAmount(Aspect.FIRE), "merge should keep the higher of 5 and 3");
		assertEquals(4, merged.getAmount(Aspect.EARTH), "EARTH wasn't in base - merge should add it fresh");
	}

	@Test
	void copyIsIndependentOfTheOriginal() {
		AspectList original = new AspectList().add(Aspect.FIRE, 5);
		AspectList copy = original.copy();

		copy.add(Aspect.FIRE, 10);
		assertEquals(5, original.getAmount(Aspect.FIRE), "mutating the copy must not affect the original");
		assertEquals(15, copy.getAmount(Aspect.FIRE));
	}

	@Test
	void nbtRoundTripPreservesEveryEntry() {
		AspectList original = new AspectList().add(Aspect.FIRE, 5).add(Aspect.WATER, 2).add(Aspect.EARTH, 9);

		CompoundTag tag = new CompoundTag();
		original.writeToNBT(tag);

		AspectList restored = new AspectList();
		restored.readFromNBT(tag);

		assertEquals(original.size(), restored.size());
		assertEquals(5, restored.getAmount(Aspect.FIRE));
		assertEquals(2, restored.getAmount(Aspect.WATER));
		assertEquals(9, restored.getAmount(Aspect.EARTH));
	}

	@Test
	void getAspectsSortedByNameIsActuallySortedByTag() {
		AspectList list = new AspectList().add(Aspect.WATER, 1).add(Aspect.FIRE, 1).add(Aspect.EARTH, 1);
		Aspect[] sorted = list.getAspectsSortedByName();

		Aspect[] expected = { Aspect.WATER, Aspect.FIRE, Aspect.EARTH };
		java.util.Arrays.sort(expected, (a, b) -> a.getTag().compareTo(b.getTag()));
		assertArrayEquals(expected, sorted);
	}

	@Test
	void getAspectsSortedByAmountIsDescending() {
		AspectList list = new AspectList().add(Aspect.WATER, 1).add(Aspect.FIRE, 9).add(Aspect.EARTH, 5);
		Aspect[] sorted = list.getAspectsSortedByAmount();

		assertEquals(Aspect.FIRE, sorted[0], "highest amount (9) should sort first");
		assertEquals(Aspect.EARTH, sorted[1], "middle amount (5) should sort second");
		assertEquals(Aspect.WATER, sorted[2], "lowest amount (1) should sort last");
	}

	@Test
	void itemStackConstructorIsCurrentlyAStubReturningAnEmptyList() {
		// Documents a known, deliberate simplification (see the constructor's own doc comment): the
		// original looked aspects up via AspectHelper.getObjectAspects(stack), which needs the full
		// ThaumcraftApi/golems/capabilities/research chain that isn't ported yet. This test exists so that
		// whoever wires that lookup back up gets a test failure here as a prompt to update this test too,
		// rather than the stub behavior silently going unnoticed forever.
		AspectList list = new AspectList(net.minecraft.world.item.ItemStack.EMPTY);
		assertEquals(0, list.size());
	}
}
