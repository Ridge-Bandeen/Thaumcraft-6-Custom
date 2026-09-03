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

	private ThaumcraftMenus() {
	}
}
