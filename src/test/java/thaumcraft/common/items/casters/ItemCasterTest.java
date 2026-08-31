package thaumcraft.common.items.casters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import thaumcraft.api.items.ItemsTC;

/**
 * Plain JUnit coverage for {@link ItemCaster}'s focus-storage data-component round trip
 * (getFocus/getFocusStack/setFocus) - the wand's actual "shift-click a focus into the offhand to load it"
 * data model, which none of the GameTests reproduce end-to-end since {@code cast()} there builds a fresh
 * focus {@link ItemStack} directly rather than routing through a wand's stored {@code WAND_FOCUS}
 * component. No Level/player involved, so this stays JUnit rather than GameTest.
 */
class ItemCasterTest {

	private static ItemCaster caster() {
		return (ItemCaster) ItemsTC.casterBasic.get();
	}

	@Test
	void freshWandHasNoFocusLoaded() {
		ItemStack wand = new ItemStack(caster());
		assertTrue(caster().getFocusStack(wand).isEmpty());
		assertNull(caster().getFocus(wand));
	}

	@Test
	void setFocusStoresItAndGetFocusReturnsItsItem() {
		ItemStack wand = new ItemStack(caster());
		ItemStack focus = new ItemStack(ItemsTC.focus1.get());

		caster().setFocus(wand, focus);

		assertTrue(ItemStack.isSameItemSameComponents(caster().getFocusStack(wand), focus.copyWithCount(1)));
		assertEquals(ItemsTC.focus1.get(), caster().getFocus(wand));
	}

	@Test
	void setFocusAlwaysStoresExactlyOneRegardlessOfTheInputStackCount() {
		ItemStack wand = new ItemStack(caster());
		ItemStack focus = new ItemStack(ItemsTC.focus1.get(), 5);

		caster().setFocus(wand, focus);

		assertEquals(1, caster().getFocusStack(wand).getCount(), "a wand only ever holds a single focus, regardless of how many were in hand when loaded");
	}

	@Test
	void setFocusWithEmptyStackClearsAPreviouslyLoadedFocus() {
		ItemStack wand = new ItemStack(caster());
		caster().setFocus(wand, new ItemStack(ItemsTC.focus1.get()));
		assertTrue(!caster().getFocusStack(wand).isEmpty());

		caster().setFocus(wand, ItemStack.EMPTY);

		assertTrue(caster().getFocusStack(wand).isEmpty());
		assertNull(caster().getFocus(wand));
	}

	@Test
	void setFocusWithNullClearsAPreviouslyLoadedFocusToo() {
		ItemStack wand = new ItemStack(caster());
		caster().setFocus(wand, new ItemStack(ItemsTC.focus1.get()));

		caster().setFocus(wand, null);

		assertTrue(caster().getFocusStack(wand).isEmpty());
	}

	@Test
	void loadingADifferentFocusReplacesTheOldOneEntirely() {
		ItemStack wand = new ItemStack(caster());
		caster().setFocus(wand, new ItemStack(ItemsTC.focus1.get()));

		caster().setFocus(wand, new ItemStack(ItemsTC.focusFrost.get()));

		assertEquals(ItemsTC.focusFrost.get(), caster().getFocus(wand));
	}
}
