package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.theorycraft.ITheorycraftAid;
import thaumcraft.api.research.theorycraft.ResearchTableData;
import thaumcraft.api.research.theorycraft.TheorycraftManager;
import thaumcraft.client.ClientResearchTableCache;
import thaumcraft.common.container.ResearchTableMenu;
import thaumcraft.common.registry.ThaumcraftSounds;
import thaumcraft.common.tiles.crafting.TileResearchTable;

/**
 * The Research Table, ported from the original's {@code GuiResearchTable}. With no theory under way: the inspiration
 * bulbs you'd start with, the research aids around the table (click to use one - each costs a bulb) and "Create
 * Theory". During a theory: the bulbs left, bonus draws, the paper stack (click to draw two cards, three with a bonus
 * draw) and the used-card pile, the drawn cards fanned out as paper sheets (gilded when they came from an aid) with
 * their name, text, inspiration cost and required items (a pulsing marker on items the card uses up), the chosen card
 * flying onto the pile, the per-category progress list on the right counting up with sparkles, the out-of-ink and
 * out-of-paper warnings, and "Complete Theory" / "Scrap Theory". The card animations advance by frame, as in the
 * original.
 *
 * <p>State comes from the server ({@link ClientResearchTableCache}); clicks go through {@link ResearchTableMenu}'s buttons
 * (starting with aids uses its {@link ResearchTableMenu#START_WITH_AIDS} mask, where the original had a packet).
 */
public class GuiResearchTable extends AbstractContainerScreen<ResearchTableMenu> {

	private static final ResourceLocation TX_BACKGROUND = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_table.png");
	private static final ResourceLocation TX_BASE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_base.png");
	private static final ResourceLocation TX_PAPER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/paper.png");
	private static final ResourceLocation TX_PAPER_GILDED = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/papergilded.png");
	private static final ResourceLocation TX_QUESTION = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_unknown.png");

	private ResearchTableData.CardChoice lastDraw;
	private float[] cardHover = {0f, 0f, 0f};
	private float[] cardZoomOut = {0f, 0f, 0f};
	private float[] cardZoomIn = {0f, 0f, 0f};
	private boolean[] cardActive = {true, true, true};
	private boolean cardSelected;
	private final Map<String, Integer> tempCatTotals = new HashMap<>();
	private long nexCatCheck;
	private long nextCheck;
	private int dummyInspirationStart;
	private Set<String> currentAids = new HashSet<>();
	private final Set<String> selectedAids = new HashSet<>();
	private final List<ResearchTableData.CardChoice> cardChoices = new ArrayList<>();
	/** The data object last seen, to notice a fresh sync. */
	private ResearchTableData seenData;
	private final List<Object> hoverTip = new ArrayList<>();

	private final ImageButton buttonCreate = new ImageButton("button.create.theory", 8978346);
	private final ImageButton buttonComplete = new ImageButton("button.complete.theory", 8978346);
	private final ImageButton buttonScrap = new ImageButton("button.scrap.theory", 16720418);

	/** GuiImageButton: gui_base's 51x13 plate, tinted, with half-size centred text; positioned by its centre. */
	private static final class ImageButton {
		final String key;
		final int color;
		int x, y;
		boolean visible, active;

		ImageButton(String key, int color) {
			this.key = key;
			this.color = color;
		}

		boolean hovered(double mx, double my) {
			return mx >= x - 49 / 2 && my >= y - 11 / 2 && mx < x - 49 / 2 + 49 && my < y - 11 / 2 + 11;
		}

		void render(GuiGraphics g, net.minecraft.client.gui.Font font, int mx, int my) {
			if (!visible) return;
			boolean hover = hovered(mx, my);
			float cc = hover ? 1.0f : 0.9f;
			float ac = 1.0f;
			if (!active) {
				cc = 0.5f;
				ac = 0.9f;
			}
			RenderSystem.enableBlend();
			g.setColor(cc * (color >> 16 & 255) / 255f, cc * (color >> 8 & 255) / 255f, cc * (color & 255) / 255f, ac);
			g.blit(TX_BASE, x - 51 / 2, y - 13 / 2, 37, 66, 51, 13);
			g.setColor(1f, 1f, 1f, 1f);
			int j = !active ? 10526880 : (hover ? 16777120 : 16777215);
			g.pose().pushPose();
			g.pose().translate(x, y, 0);
			g.pose().scale(0.5f, 0.5f, 1f);
			g.drawCenteredString(font, Component.translatable(key), 0, -4, j);
			g.pose().popPose();
		}
	}

	public GuiResearchTable(ResearchTableMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		imageWidth = 255;
		imageHeight = 255;
		inventoryLabelY = -1000;
		titleLabelY = -1000;
	}

	@Override
	protected void init() {
		super.init();
		buttonCreate.x = leftPos + 128;
		buttonCreate.y = topPos + 22;
		buttonComplete.x = leftPos + 191;
		buttonComplete.y = topPos + 96;
		buttonScrap.x = leftPos + 128;
		buttonScrap.y = topPos + 168;
	}

	private ResearchTableData data() {
		return ClientResearchTableCache.getData();
	}

	private ItemStack ink() {
		return menu.getSlot(0).getItem();
	}

	private ItemStack paper() {
		return menu.getSlot(1).getItem();
	}

	private boolean inkEmpty() {
		ItemStack ink = ink();
		return ink.isEmpty() || ink.getDamageValue() == ink.getMaxDamage();
	}

	private void send(int id) {
		if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
	}

	private void playSound(SoundEvent sound, float volume) {
		if (minecraft != null && minecraft.player != null) minecraft.player.playSound(sound, volume, 1.0f);
	}

	/** A new sync replaces the data object: pick up its card choices and last draw, as the original's constructor and
	 * syncFromTableChoices did. */
	private void syncState() {
		ResearchTableData data = data();
		if (data == seenData) return;
		boolean fresh = seenData == null || data == null;
		seenData = data;
		if (data == null) {
			cardChoices.clear();
			lastDraw = null;
			cardSelected = false;
			return;
		}
		if (fresh) {
			tempCatTotals.clear();
			tempCatTotals.putAll(data.categoryTotals);
			cardChoices.clear();
			cardChoices.addAll(data.cardChoices);
			lastDraw = data.lastDraw;
		}
		if (cardChoices.isEmpty() && !cardSelected) lastDraw = data.lastDraw;
	}

	private void checkButtons() {
		ResearchTableData data = data();
		buttonComplete.active = buttonComplete.visible = false;
		buttonScrap.active = buttonScrap.visible = false;
		if (data != null) {
			buttonCreate.active = buttonCreate.visible = false;
			if (data.isComplete()) buttonComplete.active = buttonComplete.visible = true;
			else buttonScrap.active = buttonScrap.visible = true;
		} else {
			buttonCreate.visible = true;
			buttonCreate.active = !paper().isEmpty() && !inkEmpty();
		}
	}

	private void checkCards() {
		ResearchTableData data = data();
		if (data == null) return;
		if (!data.cardChoices.isEmpty() && cardChoices.isEmpty()) {
			cardChoices.addAll(data.cardChoices);
		}
		if (!cardSelected) {
			for (int a = 0; a < cardChoices.size(); a++) {
				if (data.cardChoices.size() > a && data.cardChoices.get(a).selected) {
					for (int q = 0; q < cardChoices.size() && q < data.cardChoices.size(); q++) cardActive[q] = data.cardChoices.get(q).selected;
					cardSelected = true;
					playSound(ThaumcraftSounds.PAGETURN.get(), 1.0f);
					send(1);
					break;
				}
			}
		}
	}

	// --------------------------------------------------------------------------------------------------------------

	@Override
	public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
		syncState();
		hoverTip.clear();
		super.render(graphics, mx, my, partialTick);
		int xx = leftPos;
		int yy = topPos;
		ResearchTableData data = data();
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 300);
		buttonCreate.render(graphics, font, mx, my);
		buttonComplete.render(graphics, font, mx, my);
		buttonScrap.render(graphics, font, mx, my);
		if (data == null) {
			if (!currentAids.isEmpty()) {
				int side = Math.min(currentAids.size(), 6);
				int c = 0;
				int r = 0;
				RenderSystem.enableBlend();
				graphics.setColor(1f, 1f, 1f, 0.2f);
				for (String key : currentAids) {
					if (TheorycraftManager.aids.get(key) == null) continue;
					int x = xx + 128 + 20 * c - side * 10;
					int y = yy + 85 + 35 * r;
					if (mx >= x && my >= y && mx < x + 16 && my < y + 16 && !selectedAids.contains(key)) graphics.blit(TX_BASE, x, y, 0, 96, 16, 16);
					if (++c >= side) {
						r++;
						c = 0;
					}
				}
				graphics.setColor(1f, 1f, 1f, 1f);
			}
		} else {
			int sx = 128;
			int cw = 110;
			int sz = cardChoices.size();
			int a = 0;
			if (!cardSelected) {
				for (ResearchTableData.CardChoice cardChoice : cardChoices) {
					if (cardZoomOut[a] >= 1.0f) {
						float dx = 55 + sx - 55 * sz + cw * a - 65;
						float fx = 65.0f + dx * cardZoomOut[a];
						float qx = 191.0f - fx;
						if (cardActive[a]) fx += qx * cardZoomIn[a];
						drawSheetOverlay(fx, 100, cardChoice, mx, my);
						a++;
					}
				}
			}
			int qq = 0;
			if (inkEmpty()) {
				String s0 = Component.translatable("tile.researchtable.noink.0").getString();
				String s1 = Component.translatable("tile.researchtable.noink.1").getString();
				int w = Math.max(font.width(s0), font.width(s1)) / 2;
				ResearchTooltip.draw(graphics, font, List.of(s0, s1), xx - w + 116, yy + 60 + qq, 11, width, height);
				qq += 40;
			}
			if (paper().isEmpty()) {
				String s0 = Component.translatable("tile.researchtable.nopaper.0").getString();
				ResearchTooltip.draw(graphics, font, List.of(s0), xx - font.width(s0) / 2 + 116, yy + 60 + qq, 11, width, height);
			}
		}
		graphics.pose().popPose();
		if (!hoverTip.isEmpty()) {
			List<Component> lines = new ArrayList<>();
			for (Object o : hoverTip) lines.add(o instanceof Component c ? c : Component.literal(String.valueOf(o)));
			graphics.renderComponentTooltip(font, lines, mx, my);
		}
		renderTooltip(graphics, mx, my);
	}

	@Override
	protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
	}

	@Override
	protected void renderBg(GuiGraphics graphics, float partialTicks, int mx, int my) {
		checkButtons();
		int xx = leftPos;
		int yy = topPos;
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.blit(TX_BACKGROUND, xx, yy, 0, 0, 255, 255);
		ResearchTableData data = data();
		var player = minecraft.player;
		if (data == null) {
			if (nextCheck < player.tickCount) {
				currentAids = scanAids();
				dummyInspirationStart = ResearchTableData.getAvailableInspiration(player);
				nextCheck = player.tickCount + 100;
			}
			graphics.pose().pushPose();
			graphics.pose().translate(xx + 128 - dummyInspirationStart * 5, yy + 55, 0);
			graphics.pose().scale(0.5f, 0.5f, 1f);
			for (int a = 0; a < dummyInspirationStart; a++) {
				graphics.blit(TX_BASE, 20 * a, 0, dummyInspirationStart - selectedAids.size() <= a ? 48 : 32, 96, 16, 16);
			}
			graphics.pose().popPose();
			if (!currentAids.isEmpty()) {
				int side = Math.min(currentAids.size(), 6);
				int c = 0;
				int r = 0;
				for (String key : currentAids) {
					ITheorycraftAid aid = TheorycraftManager.aids.get(key);
					if (aid == null) continue;
					int x = xx + 128 + 20 * c - side * 10;
					int y = yy + 85 + 35 * r;
					if (selectedAids.contains(key)) graphics.blit(TX_BASE, x, y, 0, 96, 16, 16);
					ItemStack s = aid.getAidObject() instanceof ItemStack st ? st
							: aid.getAidObject() instanceof Block b ? new ItemStack(b) : ItemStack.EMPTY;
					if (!s.isEmpty()) graphics.renderItem(s, x, y);
					if (mx >= x && my >= y && mx < x + 16 && my < y + 16) {
						hoverTip.add(aidName(aid, s));
					}
					if (++c >= side) {
						r++;
						c = 0;
					}
				}
			}
			return;
		}

		checkCards();
		graphics.pose().pushPose();
		graphics.pose().translate(xx + 15, yy + 150, 0);
		for (int a = 0; a < data.bonusDraws; a++) graphics.blit(TX_BASE, a * 2, a, 64, 96, 16, 16);
		graphics.pose().popPose();
		graphics.pose().pushPose();
		graphics.pose().translate(xx + 128 - data.inspirationStart * 5, yy + 16, 0);
		graphics.pose().scale(0.5f, 0.5f, 1f);
		for (int a = 0; a < data.inspirationStart; a++) graphics.blit(TX_BASE, 20 * a, 0, data.inspiration <= a ? 48 : 32, 96, 16, 16);
		graphics.pose().popPose();

		int sheets = paper().isEmpty() ? 0 : 1 + paper().getCount() / 4;
		Random r2 = new Random(55L);
		if (sheets > 0 && !data.isComplete()) {
			for (int a2 = 0; a2 < sheets; a2++) drawSheet(graphics, xx + 65, yy + 100, 6.0, r2, 1.0f, 1.0f, null);
			int var7 = mx - (25 + xx);
			int var8 = my - (55 + yy);
			boolean highlight = cardChoices.isEmpty() && var7 >= 0 && var8 >= 0 && var7 < 75 && var8 < 90;
			graphics.pose().pushPose();
			graphics.setColor(1f, 1f, 1f, highlight ? 1.0f : 0.5f);
			graphics.pose().translate(xx + 65, yy + 100, 0);
			float s = highlight ? 1.75f : 1.5f;
			graphics.pose().scale(s, s, 1f);
			graphics.blit(TX_QUESTION, -8, -8, 0, 0, 16, 16, 16, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			graphics.pose().popPose();
		}
		for (Long seed : data.savedCards) drawSheet(graphics, xx + 191, yy + 100, 6.0, new Random(seed), 1.0f, 1.0f, null);
		if (lastDraw != null) drawSheet(graphics, xx + 191, yy + 100, 6.0, new Random(lastDraw.card.getSeed()), 1.0f, 1.0f, lastDraw);

		drawCategoryTotals(graphics, data, mx, my);

		int sx = 128;
		int cw = 110;
		int sz = cardChoices.size();
		for (int a3 = 0; a3 < sz && a3 < 3; a3++) {
			ResearchTableData.CardChoice cardChoice = cardChoices.get(a3);
			r2 = new Random(cardChoice.card.getSeed());
			int var11 = mx - (5 + sx - 55 * sz + xx + cw * a3);
			int var12 = my - (100 + yy - 60);
			if (cardZoomOut[a3] >= 0.95 && !cardSelected) {
				if (var11 >= 0 && var12 >= 0 && var11 < 100 && var12 < 120) cardHover[a3] += Math.max((0.25f - cardHover[a3]) / 3.0f * partialTicks, 0.0025f);
				else cardHover[a3] -= 0.1f * partialTicks;
			}
			if (a3 == sz - 1 || cardZoomOut[a3 + 1] > 0.6) {
				float f = cardZoomOut[a3];
				cardZoomOut[a3] += Math.max((1.0f - cardZoomOut[a3]) / 5.0f * partialTicks, 0.0025f);
				if (cardZoomOut[a3] > 0.0f && f == 0.0f) playSound(ThaumcraftSounds.PAGE.get(), 1.0f);
			}
			float prevZoomIn = cardZoomIn[a3];
			if (cardSelected) {
				cardZoomIn[a3] += cardActive[a3] ? Math.max((1.0f - cardZoomIn[a3]) / 3.0f * partialTicks, 0.0025f) : 0.3f * partialTicks;
				cardHover[a3] = 1.0f - cardZoomIn[a3];
			}
			cardZoomIn[a3] = Mth.clamp(cardZoomIn[a3], 0f, 1f);
			cardHover[a3] = Mth.clamp(cardHover[a3], 0f, 0.25f);
			cardZoomOut[a3] = Mth.clamp(cardZoomOut[a3], 0f, 1f);
			float dx = 55 + sx - 55 * sz + xx + cw * a3 - (xx + 65);
			float fx = xx + 65 + dx * cardZoomOut[a3];
			float qx = xx + 191 - fx;
			if (cardActive[a3]) fx += qx * cardZoomIn[a3];
			drawSheet(graphics, fx, yy + 100, 6.0f + cardZoomOut[a3] * 2.0f - cardZoomIn[a3] * 2.0f + cardHover[a3], r2,
					cardActive[a3] ? 1.0f : 1.0f - cardZoomIn[a3], Math.max(1.0f - cardZoomOut[a3], cardZoomIn[a3]), cardChoice);
			if (cardSelected && cardActive[a3] && cardZoomIn[a3] >= 1.0f && prevZoomIn < 1.0f) {
				playSound(ThaumcraftSounds.WRITE.get(), 0.3f);
				cardChoices.clear();
				cardSelected = false;
				lastDraw = data.lastDraw;
				break;
			}
		}
	}

	private static Component aidName(ITheorycraftAid aid, ItemStack stack) {
		if (!stack.isEmpty()) return stack.getHoverName();
		if (aid.getAidObject() instanceof Class<?> c) {
			return Component.literal(c.getSimpleName().replaceFirst("^Entity", ""));
		}
		return Component.literal(String.valueOf(aid.getAidObject()));
	}

	private Set<String> scanAids() {
		var pos = ClientResearchTableCache.getPos();
		if (pos == null || minecraft.level == null) return new HashSet<>();
		return minecraft.level.getBlockEntity(pos) instanceof TileResearchTable table ? new HashSet<>(table.checkSurroundingAids()) : new HashSet<>();
	}

	/** The per-category progress list off the right edge: counts toward the synced totals a point a tick, highest first;
	 * gold up to the penalty line, white (with the one-third penalty shown) after it, grey where blocked. */
	private void drawCategoryTotals(GuiGraphics graphics, ResearchTableData data, int mx, int my) {
		var player = minecraft.player;
		List<String> sparkle = new ArrayList<>();
		if (nexCatCheck < player.tickCount) {
			for (String cat : ResearchCategories.researchCategories.keySet()) {
				int t0 = data.categoryTotals.getOrDefault(cat, 0);
				int t2 = tempCatTotals.getOrDefault(cat, 0);
				if (t0 == 0 && t2 == 0) {
					tempCatTotals.remove(cat);
					continue;
				}
				if (t2 > t0) t2--;
				if (t2 < t0) {
					t2++;
					sparkle.add(cat);
				}
				tempCatTotals.put(cat, t2);
			}
			nexCatCheck = player.tickCount + 1;
		}
		Map<String, Integer> sorted = new LinkedHashMap<>();
		tempCatTotals.entrySet().stream().filter(e -> e.getValue() != 0).sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
				.forEachOrdered(e -> sorted.put(e.getKey(), e.getValue()));
		RandomSource random = player.getRandom();
		int xx = leftPos;
		int yy = topPos;
		int i = 0;
		for (Map.Entry<String, Integer> e : sorted.entrySet()) {
			String field = e.getKey();
			ResearchCategory rc = ResearchCategories.getResearchCategory(field);
			if (rc == null) {
				i++;
				continue;
			}
			int rowY = yy + 16 + i * 18 + (i > 0 ? 4 : 0);
			RenderSystem.enableBlend();
			graphics.blit(rc.icon, xx + 253, rowY, 0, 0, 16, 16, 16, 16);
			String s2 = e.getValue() + "%";
			if (i > data.penaltyStart) s2 += " (-" + e.getValue() / 3 + ")";
			int textY = yy + 20 + i * 18 + (i > data.penaltyStart ? 4 : 0);
			graphics.drawString(font, s2, xx + 276, textY, data.categoriesBlocked.contains(field) ? 6316128 : (i <= data.penaltyStart ? 57536 : 16777215), true);
			if (sparkle.contains(field)) {
				for (int q = 0; q < 2; q++) {
					float gg = Mth.nextInt(random, 189, 255) / 255.0f;
					float bb = Mth.nextInt(random, 64, 255) / 255.0f;
					FXGenericGui.spawn(random, xx + 276 + random.nextFloat() * font.width(s2), textY + random.nextFloat() * 8.0f,
							random.nextGaussian() * 0.5, random.nextGaussian() * 0.5, 24.0f, 1.0f, gg, bb, 0, 0.9f, -1.0f);
				}
			}
			int var9 = mx - (xx + 256);
			int var10 = my - (yy + 16 + i * 18 + (i > data.penaltyStart ? 4 : 0));
			if (var9 >= 0 && var10 >= 0 && var9 < 16 && var10 < 16) hoverTip.add(ResearchCategories.getCategoryName(field));
			i++;
		}
	}

	/** One sheet of paper: a random nudge and tilt from the card's seed, flipped at random, gilded for an aid's card; with
	 * a card on it, its category watermark, bold name, text, inspiration cost and required items. */
	private void drawSheet(GuiGraphics graphics, double x, double y, double scale, Random r, float alpha, float tilt, ResearchTableData.CardChoice cardChoice) {
		var pose = graphics.pose();
		pose.pushPose();
		RenderSystem.enableBlend();
		RenderSystem.disableCull();
		graphics.setColor(1f, 1f, 1f, alpha);
		pose.translate(x + r.nextGaussian(), y + r.nextGaussian(), 0);
		pose.scale((float) scale, (float) scale, 1f);
		pose.mulPose(Axis.ZP.rotationDegrees((float) (r.nextGaussian() * tilt)));
		pose.pushPose();
		ResourceLocation tex = cardChoice != null && cardChoice.fromAid ? TX_PAPER_GILDED : TX_PAPER;
		if (r.nextBoolean()) pose.mulPose(Axis.ZP.rotationDegrees(180f));
		if (r.nextBoolean()) pose.mulPose(Axis.YP.rotationDegrees(180f));
		graphics.blit(tex, -8, -8, 0, 0, 16, 16, 16, 16);
		pose.popPose();
		RenderSystem.enableCull();
		if (cardChoice != null && alpha == 1.0f) {
			if (cardChoice.card.getResearchCategory() != null) {
				ResearchCategory rc = ResearchCategories.getResearchCategory(cardChoice.card.getResearchCategory());
				if (rc != null) {
					graphics.setColor(1f, 1f, 1f, alpha / 6.0f);
					pose.pushPose();
					pose.scale(0.5f, 0.5f, 1f);
					graphics.blit(rc.icon, -8, -8, 0, 0, 16, 16, 16, 16);
					pose.popPose();
				}
			}
			graphics.setColor(1f, 1f, 1f, 1f);
			pose.pushPose();
			pose.scale(0.0625f, 0.0625f, 1f);
			String name = ChatFormatting.BOLD + cardChoice.card.getLocalizedName() + ChatFormatting.RESET;
			graphics.drawString(font, name, -font.width(name) / 2, -65, 0, false);
			graphics.drawWordWrap(font, Component.literal(cardChoice.card.getLocalizedText()), -70, -48, 140, 0);
			pose.popPose();
			pose.pushPose();
			pose.scale(0.0625f, 0.0625f, 1f);
			int cc = cardChoice.card.getInspirationCost();
			boolean add = false;
			if (cc < 0) {
				add = true;
				cc = Math.abs(cc) + 1;
			}
			for (int a = 0; a < cc; a++) {
				if (a == 0 && add) graphics.blit(TX_BASE, -10 * cc + 20 * a, -95, 48, 0, 16, 16);
				else graphics.blit(TX_BASE, -10 * cc + 20 * a, -95, 32, 96, 16, 16);
			}
			pose.popPose();
			ItemStack[] items = cardChoice.card.getRequiredItems();
			if (items != null) {
				for (int a2 = 0; a2 < items.length; a2++) {
					pose.pushPose();
					pose.scale(0.125f, 0.125f, 1f);
					if (items[a2] == null || items[a2].isEmpty()) {
						graphics.setColor(0.75f, 0.75f, 0.75f, alpha);
						pose.translate(-9 * items.length + 18 * a2, 35.0, 0);
						graphics.blit(TX_QUESTION, 0, 0, 0, 0, 16, 16, 16, 16);
						graphics.setColor(1f, 1f, 1f, 1f);
					} else {
						graphics.renderItem(items[a2], -9 * items.length + 18 * a2, 35);
					}
					pose.popPose();
					boolean[] consumed = cardChoice.card.getRequiredItemsConsumed();
					if (items[a2] != null && !items[a2].isEmpty() && consumed != null && a2 < consumed.length && consumed[a2]) {
						pose.pushPose();
						pose.scale(0.125f, 0.125f, 1f);
						float s = (float) Math.sin((minecraft.player.tickCount + a2 * 2 + minecraft.getTimer().getGameTimeDeltaPartialTick(false)) / 2.0f) * 0.03f;
						pose.translate(-2 - 9 * items.length + 18 * a2, 45.0f + s * 10.0f, 200);
						pose.scale(0.5f, 0.5f + s, 1f);
						graphics.blit(TX_BASE, 0, 0, 64, 120, 16, 16);
						pose.popPose();
					}
				}
			}
		}
		graphics.setColor(1f, 1f, 1f, 1f);
		pose.popPose();
	}

	/** The required items' tooltips on an open card. */
	private void drawSheetOverlay(double x, double y, ResearchTableData.CardChoice cardChoice, int mx, int my) {
		if (cardChoice == null || cardChoice.card.getRequiredItems() == null) return;
		ItemStack[] items = cardChoice.card.getRequiredItems();
		for (int a = 0; a < items.length; a++) {
			int rx = leftPos + (int) (x - 9 * items.length + 18 * a);
			int ry = topPos + (int) (y + 36.0);
			if (mx >= rx - 1 && my >= ry - 1 && mx < rx + 15 + 1 && my < ry + 15 + 1) {
				if (items[a] == null || items[a].isEmpty()) hoverTip.add(Component.translatable("tc.card.unknown"));
				else hoverTip.addAll(getTooltipFromContainerItem(items[a]));
			}
		}
	}

	// --------------------------------------------------------------------------------------------------------------

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button == 0) {
			if (buttonCreate.visible && buttonCreate.active && buttonCreate.hovered(mouseX, mouseY)) {
				playSound(ThaumcraftSounds.CLACK.get(), 0.4f);
				List<String> keys = ResearchTableMenu.aidKeys();
				int mask = 0;
				for (String key : selectedAids) {
					int i = keys.indexOf(key);
					if (i >= 0 && i < 30) mask |= 1 << i;
				}
				send(ResearchTableMenu.START_WITH_AIDS + mask);
				selectedAids.clear();
				return true;
			}
			if (buttonComplete.visible && buttonComplete.active && buttonComplete.hovered(mouseX, mouseY)) {
				playSound(ThaumcraftSounds.CLACK.get(), 0.4f);
				send(7);
				tempCatTotals.clear();
				lastDraw = null;
				return true;
			}
			if (buttonScrap.visible && buttonScrap.active && buttonScrap.hovered(mouseX, mouseY)) {
				playSound(ThaumcraftSounds.CLACK.get(), 0.4f);
				send(9);
				tempCatTotals.clear();
				lastDraw = null;
				cardChoices.clear();
				return true;
			}
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		int mx = (int) mouseX;
		int my = (int) mouseY;
		int xx = leftPos;
		int yy = topPos;
		ResearchTableData data = data();
		if (data == null) {
			if (!currentAids.isEmpty()) {
				int side = Math.min(currentAids.size(), 6);
				int c = 0;
				int r = 0;
				for (String key : currentAids) {
					if (TheorycraftManager.aids.get(key) == null) continue;
					int x = xx + 128 + 20 * c - side * 10;
					int y = yy + 85 + 35 * r;
					if (mx >= x && my >= y && mx < x + 16 && my < y + 16) {
						if (selectedAids.contains(key)) selectedAids.remove(key);
						else if (selectedAids.size() + 1 < dummyInspirationStart) selectedAids.add(key);
					}
					if (++c >= side) {
						r++;
						c = 0;
					}
				}
			}
		} else {
			int sx = 128;
			int cw = 110;
			if (!cardChoices.isEmpty()) {
				int pressed = -1;
				for (int a = 0; a < cardChoices.size(); a++) {
					int var7 = mx - (5 + sx - 55 * cardChoices.size() + xx + cw * a);
					int var8 = my - (100 + yy - 60);
					if (cardZoomOut[a] >= 0.95 && !cardSelected && var7 >= 0 && var8 >= 0 && var7 < 100 && var8 < 120) {
						pressed = a;
						break;
					}
				}
				if (pressed >= 0 && !inkEmpty()) send(4 + pressed);
			} else {
				int var9 = mx - (25 + xx);
				int var10 = my - (55 + yy);
				if (var9 >= 0 && var10 >= 0 && var9 < 75 && var10 < 90 && !paper().isEmpty()) drawCards(data);
			}
		}
		return handled;
	}

	/** Two cards, three while a bonus draw is left. */
	private void drawCards(ResearchTableData data) {
		cardSelected = false;
		cardHover = new float[] {0f, 0f, 0f};
		cardZoomOut = new float[] {0f, 0f, 0f};
		cardZoomIn = new float[] {0f, 0f, 0f};
		cardActive = new boolean[] {true, true, true};
		int draw = data.bonusDraws > 0 ? 3 : 2;
		send(draw);
		cardChoices.clear();
	}

	@Override
	public void removed() {
		super.removed();
		ClientResearchTableCache.clear();
	}
}
