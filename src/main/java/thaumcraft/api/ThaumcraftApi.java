package thaumcraft.api;

import thaumcraft.api.internal.DummyInternalMethodHandler;
import thaumcraft.api.internal.IInternalMethodHandler;

/**
 * Replaces the old (1.12.2) {@code ThaumcraftApi} - trimmed drastically from the original (555 lines).
 * This port keeps only what's actually still needed: the {@link #internalMethods} field
 * {@link IInternalMethodHandler} implementations register into (see
 * {@link thaumcraft.common.ThaumcraftInternalMethodHandler} for this port's real one), which is what
 * finally lets {@code AuraHelper} (and, eventually, addon/legacy code depending on this API) actually work
 * instead of being a permanent stub.
 *
 * Everything else in the original - {@code registerResearchLocation}, the whole family of
 * {@code addArcaneCraftingRecipe}/{@code addCrucibleRecipe}/{@code addInfusionCraftingRecipe}/
 * {@code addSmeltingBonus} static registration methods, scannable-thing registration, etc. - is
 * superseded entirely by this port's modern data-driven systems (JSON recipes under
 * {@code data/thaumcraft/recipe}, {@code DeferredRegister}-based item/block/entity registries) and isn't
 * called by anything this port's already-ported code needs. Not worth reviving a static registration API
 * nothing in this codebase would ever call. Revisit only if a genuine addon-compatibility need comes up.
 */
public class ThaumcraftApi {

	/**
	 * Calling methods from this only works properly once Thaumcraft's real handler has replaced this
	 * default - see {@link thaumcraft.common.ThaumcraftInternalMethodHandler}.
	 */
	public static IInternalMethodHandler internalMethods = new DummyInternalMethodHandler();
}
