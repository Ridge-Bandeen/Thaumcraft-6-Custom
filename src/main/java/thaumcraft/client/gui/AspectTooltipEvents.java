package thaumcraft.client.gui;

import com.mojang.datafixers.util.Either;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;

/**
 * The original's "hold shift over an item to see its aspects": in any container screen (inventory, chests, machines, the creative menu) with shift
 * held, the tooltip of an item that has aspects gets a row of aspect icons with their amounts along its bottom ({@code RenderEventHandler}'s two
 * {@code tooltipEvent} handlers - the original reserved blank lines and drew into them; a real tooltip component does it here). Its {@code showTags}
 * option (invert: show without shift) is not carried over. The aspects come from the item-aspect database ({@code ThaumcraftObjectTags}), so
 * that whole table's values show up here. Lost in the port until 2026-09-26.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class AspectTooltipEvents {

	private AspectTooltipEvents() {
	}

	@SubscribeEvent
	static void onRegisterFactories(RegisterClientTooltipComponentFactoriesEvent event) {
		event.register(AspectsTooltip.class, ClientAspectsTooltip::new);
	}

	@SubscribeEvent
	static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
		Screen screen = Minecraft.getInstance().screen;
		if (!(screen instanceof AbstractContainerScreen) || !Screen.hasShiftDown()) return;
		ItemStack stack = event.getItemStack();
		if (stack.isEmpty()) return;
		AspectList aspects = AspectHelper.getObjectAspects(stack);
		if (aspects == null || aspects.size() == 0) return;
		event.getTooltipElements().add(Either.right(new AspectsTooltip(aspects)));
	}
}
