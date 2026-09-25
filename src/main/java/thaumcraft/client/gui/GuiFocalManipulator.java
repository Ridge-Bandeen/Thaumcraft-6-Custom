package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.common.container.FocalManipulatorMenu;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.lib.network.ServerboundSaveFocusGraphPacket;
import thaumcraft.common.tiles.crafting.FocusElementNode;

/**
 * Replaces the old (1.12.2) {@code GuiFocalManipulator} - the original's real screen (1116 lines: a
 * freeform 2D canvas with drag-to-place nodes, drag-to-connect wiring, a per-node settings spinner, and
 * its own scrollbars, all built on legacy LWJGL2 input handling and now-unported custom widget classes
 * like {@code GuiImageButton}/{@code GuiSliderTC}). This session's own testing discipline (a GameTest per
 * real behaviour) can't reach client-only rendering/input code at all - the user explicitly accepted
 * lighter verification for this one commit rather than leaving the whole arc's real payoff unbuilt.
 *
 * Real, working, but deliberately simplified compared to the original:
 * <ul>
 * <li><b>Linear chain, not a 2D graph.</b> Clicking a palette button appends a new node to the end of a
 * single ordered chain (medium -> effect -> effect -> ...) instead of placing/connecting nodes freely -
 * covers the overwhelming majority of real foci (anything not using {@code FocusModSplit}'s branching),
 * and lets the save packet reuse {@link FocusElementNode}'s existing id/parent/children shape unchanged
 * (a straight chain: node i's parent is i-1, its only child is i+1) rather than needing new server-side
 * graph-shape handling.</li>
 * <li><b>Remove-last only</b>, not arbitrary per-node removal - avoids a dynamically resized list of
 * per-row remove buttons.</li>
 * <li><b>No inline per-node settings editor</b> - every added node keeps its default {@link
 * thaumcraft.api.casters.NodeSetting} values (the same defaults {@code FocusNode}'s own constructor
 * already assigns). A real, documented gap versus the original's spinner widget, not a silent one.</li>
 * <li><b>No round-trip of the tile's own previously-saved graph on open</b> - {@code nodes}/
 * {@code focusName} are plain {@link thaumcraft.common.tiles.crafting.TileFocalManipulator} fields with no
 * sync path of their own (unlike the focus item slot, which syncs automatically through the menu's own
 * {@code Slot}). The screen always starts from an empty chain; only what's assembled in the current
 * session gets sent. A real cut, not yet worth the extra {@code BlockEntity} sync machinery for a first
 * working cut of the canvas.</li>
 * </ul>
 *
 * The palette lists every {@link FocusEngine} element the local player has already researched (mirrors
 * the original's own research-gated visibility). The graph is sent to the server
 * ({@link ServerboundSaveFocusGraphPacket}) when the screen closes; the "Craft" button reuses vanilla's own
 * generic container-button-click packet against {@link FocalManipulatorMenu}'s button id 0, exactly like
 * an enchanting table's own confirm button.
 */
public class GuiFocalManipulator extends AbstractContainerScreen<FocalManipulatorMenu> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_wandtable.png");

	private final List<FocusNode> chain = new ArrayList<>();
	private EditBox nameField;

	public GuiFocalManipulator(FocalManipulatorMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 231;
		imageHeight = 231;
		inventoryLabelY = imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();

		nameField = new EditBox(font, leftPos + 8, topPos + 8, 120, 14, Component.translatable("wandtable.name"));
		nameField.setMaxLength(32);
		addRenderableWidget(nameField);

		int paletteY = topPos + 8;
		for (String key : FocusEngine.elements.keySet()) {
			IFocusElement element = FocusEngine.getElement(key);
			if (!(element instanceof FocusNode representative)) continue;
			if (minecraft.player != null && !ThaumcraftCapabilities.knowsResearchStrict(minecraft.player, representative.getResearch())) continue;

			int y = paletteY;
			addRenderableWidget(Button.builder(Component.translatable(representative.getUnlocalizedName()), b -> addToChain(key))
					.bounds(leftPos + 140, y, 82, 14)
					.build());
			paletteY += 16;
		}

		addRenderableWidget(Button.builder(Component.translatable("wandtable.remove"), b -> removeLastFromChain())
				.bounds(leftPos + 8, topPos + 160, 90, 16)
				.build());
		addRenderableWidget(Button.builder(Component.translatable("wandtable.text3"), b -> startCraft())
				.bounds(leftPos + 8, topPos + 180, 90, 16)
				.build());
	}

	private void addToChain(String key) {
		IFocusElement element = FocusEngine.getElement(key);
		if (element instanceof FocusNode node) {
			chain.add(node);
		}
	}

	private void removeLastFromChain() {
		if (!chain.isEmpty()) {
			chain.remove(chain.size() - 1);
		}
	}

	private void startCraft() {
		if (minecraft != null && minecraft.gameMode != null) {
			minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
		}
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

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
		super.renderLabels(graphics, mouseX, mouseY);
		int y = 30;
		for (int i = 0; i < chain.size(); i++) {
			graphics.drawString(font, (i + 1) + ". " + Component.translatable(chain.get(i).getUnlocalizedName()).getString(), 8, y, 0x404040, false);
			y += 10;
		}

		ItemStack focus = menu.slots.get(0).getItem();
		if (!focus.isEmpty() && focus.getItem() instanceof ItemFocus item) {
			int max = item.getMaxComplexity();
			if (max < Integer.MAX_VALUE) {
				int total = 0;
				for (FocusNode node : chain) total += node.getComplexity();
				graphics.drawString(font, total + " / " + max, 8, 150, total > max ? 0xCC0000 : 0x404040, false);
			}
		}
	}

	@Override
	public void removed() {
		super.removed();
		if (chain.isEmpty()) {
			return;
		}
		List<CompoundTag> nodeTags = new ArrayList<>(chain.size());
		for (int i = 0; i < chain.size(); i++) {
			FocusElementNode entry = new FocusElementNode();
			entry.id = i;
			entry.node = chain.get(i);
			entry.parent = i - 1;
			entry.children = i < chain.size() - 1 ? new int[] { i + 1 } : new int[0];
			nodeTags.add(entry.serialize());
		}
		PacketDistributor.sendToServer(new ServerboundSaveFocusGraphPacket(nodeTags, nameField.getValue()));
	}
}
