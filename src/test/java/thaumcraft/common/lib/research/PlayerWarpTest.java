package thaumcraft.common.lib.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType;

/** Plain JUnit coverage for {@link PlayerWarp} - pure logic, no Level/player dependency. */
class PlayerWarpTest {

	private PlayerWarp warp;

	@BeforeEach
	void setUp() {
		warp = new PlayerWarp();
	}

	@Test
	void newPlayerStartsAtZeroForEveryType() {
		for (EnumWarpType type : EnumWarpType.values()) {
			assertEquals(0, warp.get(type));
		}
		assertEquals(0, warp.getCounter());
	}

	@Test
	void addAccumulatesAndReturnsTheNewTotal() {
		assertEquals(5, warp.add(EnumWarpType.NORMAL, 5));
		assertEquals(8, warp.add(EnumWarpType.NORMAL, 3));
		assertEquals(8, warp.get(EnumWarpType.NORMAL));
	}

	@Test
	void reduceIsAddWithTheAmountNegated() {
		warp.set(EnumWarpType.TEMPORARY, 10);
		assertEquals(4, warp.reduce(EnumWarpType.TEMPORARY, 6));
		assertEquals(4, warp.get(EnumWarpType.TEMPORARY));
	}

	@Test
	void setAndAddBothClampAtZeroAndNeverGoNegative() {
		warp.set(EnumWarpType.PERMANENT, -50);
		assertEquals(0, warp.get(EnumWarpType.PERMANENT), "set() should clamp a negative amount to 0");

		warp.set(EnumWarpType.PERMANENT, 5);
		warp.reduce(EnumWarpType.PERMANENT, 100);
		assertEquals(0, warp.get(EnumWarpType.PERMANENT), "reducing past 0 should clamp there, not go negative");
	}

	@Test
	void warpTypesAreIndependentOfEachOther() {
		warp.set(EnumWarpType.NORMAL, 5);
		warp.set(EnumWarpType.PERMANENT, 20);
		assertEquals(5, warp.get(EnumWarpType.NORMAL));
		assertEquals(20, warp.get(EnumWarpType.PERMANENT));
		assertEquals(0, warp.get(EnumWarpType.TEMPORARY));
	}

	@Test
	void clearResetsEverythingIncludingTheCounter() {
		warp.set(EnumWarpType.NORMAL, 5);
		warp.setCounter(42);

		warp.clear();

		assertEquals(0, warp.get(EnumWarpType.NORMAL));
		assertEquals(0, warp.getCounter());
	}

	@Test
	void nbtRoundTripPreservesEveryTypeAndTheCounter() {
		warp.set(EnumWarpType.NORMAL, 5);
		warp.set(EnumWarpType.PERMANENT, 20);
		warp.setCounter(7);

		CompoundTag tag = warp.serializeNBT(null);

		PlayerWarp restored = new PlayerWarp();
		restored.deserializeNBT(null, tag);

		assertEquals(5, restored.get(EnumWarpType.NORMAL));
		assertEquals(20, restored.get(EnumWarpType.PERMANENT));
		assertEquals(0, restored.get(EnumWarpType.TEMPORARY), "a type that was never set should round-trip as 0, not absent-and-error");
		assertEquals(7, restored.getCounter());
	}
}
