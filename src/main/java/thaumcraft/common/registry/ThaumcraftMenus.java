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

	private ThaumcraftMenus() {
	}
}
