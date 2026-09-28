package thaumcraft.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.research.ResearchEntry;

/**
 * The original's own {@code ResearchToast} - restored 2026-09-27: the "Research Completed!" popup the original showed
 * whenever a research entry finished (driven by the server's POPUP flag, see {@code ThaumcraftInternalMethodHandler
 * #progressResearchNoSync} and {@code ClientPlayerKnowledgeCache#handle}). Same art ({@code hud.png}'s own 160x32 strip
 * at 0,224), the entry's icon, the same two text colours, shrunk to fit when the name is long, shown for 5 seconds.
 */
public class ResearchToast implements Toast {

	private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/hud.png");

	private final ResearchEntry entry;

	public ResearchToast(ResearchEntry entry) {
		this.entry = entry;
	}

	@Override
	public Visibility render(GuiGraphics graphics, ToastComponent toastComponent, long timeSinceLastVisible) {
		graphics.blit(TEX, 0, 0, 0, 224, 160, 32);
		ThaumonomiconScreen.renderEntryIcon(graphics, entry, 6 + 8, 8 + 8, false);
		Font font = toastComponent.getMinecraft().font;
		graphics.drawString(font, Component.translatable("research.complete"), 30, 7, 10631665, false);
		String name = entry.getLocalizedName().getString();
		float w = font.width(name);
		if (w > 124.0f) {
			float scale = 124.0f / w;
			graphics.pose().pushPose();
			graphics.pose().translate(30.0f, 18.0f, 0.0f);
			graphics.pose().scale(scale, scale, scale);
			graphics.drawString(font, name, 0, 0, 16755465, false);
			graphics.pose().popPose();
		} else {
			graphics.drawString(font, name, 30, 18, 16755465, false);
		}
		return timeSinceLastVisible < 5000L ? Visibility.SHOW : Visibility.HIDE;
	}
}
