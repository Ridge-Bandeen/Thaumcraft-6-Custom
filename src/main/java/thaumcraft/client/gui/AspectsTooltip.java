package thaumcraft.client.gui;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import thaumcraft.api.aspects.AspectList;

/** Data for the aspect row shown at the bottom of an item's tooltip while shift is held in a container screen (see {@link AspectTooltipEvents}). */
public record AspectsTooltip(AspectList aspects) implements TooltipComponent {
}
