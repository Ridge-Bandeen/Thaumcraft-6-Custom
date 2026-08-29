package thaumcraft.common.registry;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.container.ArcaneWorkbenchMenu;

public final class ThaumcraftMenus {

	public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "thaumcraft");

	public static final Supplier<MenuType<ArcaneWorkbenchMenu>> ARCANE_WORKBENCH = MENUS.register("arcane_workbench",
			() -> IMenuTypeExtension.create((windowId, inv, buf) -> new ArcaneWorkbenchMenu(windowId, inv)));

	private ThaumcraftMenus() {
	}
}
