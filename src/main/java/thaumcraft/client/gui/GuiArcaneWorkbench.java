package thaumcraft.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import thaumcraft.common.container.ArcaneWorkbenchMenu;

/**
 * Replaces the old (1.12.2) {@code GuiArcaneWorkbench}. Reuses the original background texture and its
 * xSize/ySize (190/234) - the crystal-ring glow animation and vis cost/availability text are dropped for
 * now since they depend on the not-yet-ported aura system (see TileArcaneWorkbench); this just draws the
 * static background.
 */
public class GuiArcaneWorkbench extends AbstractContainerScreen<ArcaneWorkbenchMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/arcaneworkbench.png");

	public GuiArcaneWorkbench(ArcaneWorkbenchMenu menu, Inventory playerInventory, net.minecraft.network.chat.Component title) {
		super(menu, playerInventory, title);
		imageWidth = 190;
		imageHeight = 234;
		inventoryLabelY = imageHeight - 94;
	}

	/** {@code AbstractContainerScreen#render} doesn't draw item tooltips by itself in 1.21 - vanilla's own container
	 * screens call {@code renderTooltip} at the end of their own {@code render}. Missing here until the 2026-09-25
	 * playtest found no tooltips on this screen. */
	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		super.render(graphics, mouseX, mouseY, partialTick);
		renderTooltip(graphics, mouseX, mouseY);
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
		graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
	}
}
