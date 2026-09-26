package thaumcraft.common.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.container.ArcaneWorkbenchMenu;
import thaumcraft.common.container.FocalManipulatorMenu;
import thaumcraft.common.container.ResearchTableMenu;

public final class ThaumcraftMenus {

	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "thaumcraft");

	public static final Supplier<MenuType<ArcaneWorkbenchMenu>> ARCANE_WORKBENCH = MENUS.register("arcane_workbench",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new ArcaneWorkbenchMenu(windowId, inv)));

	public static final Supplier<MenuType<FocalManipulatorMenu>> FOCAL_MANIPULATOR = MENUS.register("focal_manipulator",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new FocalManipulatorMenu(windowId, inv)));

	public static final Supplier<MenuType<ResearchTableMenu>> RESEARCH_TABLE = MENUS.register("research_table",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new ResearchTableMenu(windowId, inv)));

	public static final Supplier<MenuType<thaumcraft.common.container.GolemBuilderMenu>> GOLEM_BUILDER = MENUS.register("golem_builder",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.GolemBuilderMenu(windowId, inv)));

	/** Unlike every menu above, {@code SealConfigMenu}'s own client constructor genuinely needs the extra-data
	 * buffer (variable per-seal shape - see that class's own doc comment), so this one really uses the
	 * {@code (windowId, inv, buf)} parameter instead of ignoring it. */
	public static final Supplier<MenuType<thaumcraft.common.container.SealConfigMenu>> SEAL_CONFIG = MENUS.register("seal_config",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.SealConfigMenu::new));

	/** Like {@link #GOLEM_BUILDER} above, the client constructor ignores the buffer entirely - {@code
	 * TurretConfigMenu}'s own shape never varies per instance, see its own doc comment. */
	public static final Supplier<MenuType<thaumcraft.common.container.TurretConfigMenu>> TURRET_CONFIG = MENUS.register("turret_config",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.TurretConfigMenu::new));

	public static final Supplier<MenuType<thaumcraft.common.container.ThaumonomiconMenu>> THAUMONOMICON = MENUS.register("thaumonomicon",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.ThaumonomiconMenu(windowId, inv)));

	/** Like {@link #GOLEM_BUILDER} above, the client constructor ignores the buffer - {@code PechMenu}'s own
	 * shape never varies per instance. This port's first menu attached to a live entity rather than a block
	 * position - see that class's own doc comment. */
	public static final Supplier<MenuType<thaumcraft.common.container.PechMenu>> PECH_TRADE = MENUS.register("pech_trade",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.PechMenu(windowId, inv)));

	/** Unlike most menus here this one needs the extra-data buffer: the client constructor reads the block position
	 * to find the Thaumatorium's tile (its recipe selection is tile state). */
	public static final Supplier<MenuType<thaumcraft.common.container.ThaumatoriumMenu>> THAUMATORIUM = MENUS.register("thaumatorium",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.ThaumatoriumMenu::new));

	public static final Supplier<MenuType<thaumcraft.common.container.SmelterMenu>> SMELTER = MENUS.register("smelter",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.SmelterMenu(windowId, inv)));

	/** Needs the block position from the extra data, like {@link #THAUMATORIUM}. */
	public static final Supplier<MenuType<thaumcraft.common.container.VoidSiphonMenu>> VOID_SIPHON = MENUS.register("void_siphon",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.VoidSiphonMenu::new));

	/** Entity-bound: the client constructor reads the bore's entity id from the extra data. */
	public static final Supplier<MenuType<thaumcraft.common.container.ArcaneBoreMenu>> ARCANE_BORE = MENUS.register("arcane_bore",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.ArcaneBoreMenu::new));

	/** Needs the block position from the extra data, like {@link #THAUMATORIUM}. */
	public static final Supplier<MenuType<thaumcraft.common.container.PotionSprayerMenu>> POTION_SPRAYER = MENUS.register("potion_sprayer",
			() -> IMenuTypeExtension.create(thaumcraft.common.container.PotionSprayerMenu::new));

	public static final Supplier<MenuType<thaumcraft.common.container.SpaMenu>> SPA = MENUS.register("spa",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.SpaMenu(windowId, inv)));

	/** Like {@link #PECH_TRADE} above, this menu isn't attached to a block position either - {@code
	 * FocusPouchMenu}'s own real pouch stack is item-carried, not block-carried. The client constructor
	 * ignores the buffer; every real slot gets synced right after construction regardless. */
	public static final Supplier<MenuType<thaumcraft.common.container.FocusPouchMenu>> FOCUS_POUCH = MENUS.register("focus_pouch",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.FocusPouchMenu(windowId, inv)));

	/** Like {@link #FOCUS_POUCH} above, item-carried rather than block-position-based. */
	public static final Supplier<MenuType<thaumcraft.common.container.HandMirrorMenu>> HAND_MIRROR = MENUS.register("hand_mirror",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new thaumcraft.common.container.HandMirrorMenu(windowId, inv)));

	private ThaumcraftMenus() {
	}
}
