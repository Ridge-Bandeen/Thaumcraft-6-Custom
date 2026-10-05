package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.crafting.ArcaneRecipe;
import thaumcraft.api.crafting.ArcaneShapedRecipe;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchAddendum;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;
import thaumcraft.common.container.ThaumonomiconMenu;
import thaumcraft.common.lib.crafting.InfusionEnchantmentRecipe;
import thaumcraft.common.lib.research.ResearchRecipeCatalog;
import thaumcraft.common.lib.research.ResearchRequirements;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * The open book: a port of the original's {@code GuiResearchPage}, drawn by {@link ThaumonomiconScreen} while an entry
 * is open. As in the original it shows the text of the stage you're on (and, once the entry is finished, its unlocked
 * addenda), laid out across the two-page spread at the original's line and image budgets; beneath the first page, while
 * the entry is unfinished, the stage's requirement rows (research, items to carry, items to craft, knowledge) each
 * ticked when met, and once all are met the "complete stage" hotspot; the aspect and knowledge tabs on the left edge
 * and their full-page inserts; the recipe bookmarks down the right edge and the recipe pages they open (arcane,
 * crafting, crucible, infusion), with click-through from an ingredient to the recipe that makes it and a back arrow;
 * and the warp warning on a stage that costs warp.
 *
 * <p>The stage text is re-read every two seconds (every quarter second after a "complete stage" click, until the server
 * reports the next stage) the same way the original polled, so the page follows the server's progress by itself.
 *
 * <p>Multiblocks (Infernal Furnace, the infusion altars, Thaumatorium, Golem Press) get the original's "construct" page: a
 * 3D render of what to build ({@link BlueprintRenderer}), the materials underneath, and a toggle that peels the layers
 * off one a second; their bookmark is a small render of the finished structure.
 */
final class ResearchPageView {

	private static final ResourceLocation TEX1 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook.png");
	private static final ResourceLocation TEX2 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_researchbook_overlay.png");
	private static final ResourceLocation TEX3 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_back.png");
	private static final ResourceLocation TEX4 = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/paper.png");
	private static final ResourceLocation DUMMY_RESEARCH = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_unknown.png");
	private static final ResourceLocation DUMMY_MAP = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/rd_map.png");
	private static final ResourceLocation DUMMY_FLASK = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/rd_flask.png");
	private static final ResourceLocation DUMMY_CHEST = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/rd_chest.png");
	private static final ResourceLocation[] KNOW_TYPE = {
			ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/knowledge_theory.png"),
			ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/knowledge_observation.png")};

	private static final int PANE_W = 256;
	private static final int PANE_H = 181;
	private static final int PAGEWIDTH = 140;
	private static final PageImage PILINE = PageImage.parse("thaumcraft:textures/gui/gui_researchbook.png:24:184:95:6:1");
	private static final PageImage PIDIV = PageImage.parse("thaumcraft:textures/gui/gui_researchbook.png:28:192:140:6:1");

	// the original's statics
	private static final LinkedList<ResourceLocation> history = new LinkedList<>();
	private static ResourceLocation shownRecipe;
	private static int aspectsPage;
	private static boolean cycleMultiblockLines;

	private final ThaumonomiconScreen screen;
	private final ResearchEntry research;
	private int currentStage;
	private int lastStage;
	private boolean hold;
	private int page;
	private int maxPages;
	private int maxAspectPages;
	private AspectList knownPlayerAspects;
	private int rhash;
	private long lastCheck;
	private int hrx;
	private int hry;
	private int recipePage;
	private int recipePageMax;
	private long lastCycle;
	private int cycle = -1;
	private boolean showingAspects;
	/** Not in the original: clicking an aspect in the aspects insert lists the things you have scanned that contain it
	 * (the kind of lookup 1.12 players got from the Thaumic JEI add-on). Null while the insert shows the aspect list. */
	private Aspect shownAspect;
	private final List<ItemStack> aspectItems = new ArrayList<>();
	private final List<Integer> aspectAmounts = new ArrayList<>();
	private int aspectItemsPage;
	private static final int ASPECT_ITEM_COLS = 8;
	private static final int ASPECT_ITEM_ROWS = 9;
	private boolean showingKnowledge;
	private boolean hasRecipePages;
	private boolean allowWithPagePopup;
	private List<?> tipText;
	/** Recipe keys to what they show: {@link RecipeHolder}s, or a multiblock {@link thaumcraft.common.registry.ThaumcraftDustTriggers.Blueprint}. */
	private final Map<ResourceLocation, List<Object>> recipeLists = new LinkedHashMap<>();
	/** The bookmark icon for each entry of {@link #recipeLists}: an item, or a blueprint drawn small. */
	private final Map<ResourceLocation, List<Object>> recipeOutputs = new LinkedHashMap<>();
	private final Map<ResourceLocation, List<Object>> drilldownLists = new LinkedHashMap<>();
	private final Map<ResourceLocation, BlueprintRenderer> blueprintIcons = new HashMap<>();
	/** The open construct page's structure (the original's blockAccess), with its slice counter. */
	private BlueprintRenderer blockAccess;
	private int sliceLine;
	private boolean renderingCompound;
	/** Click-through targets registered while drawing: x, y, recipe key, page. */
	private final List<Object[]> reference = new ArrayList<>();
	private final Map<String, String> keyCache = new HashMap<>();
	private final List<Page> pages = new ArrayList<>();
	private boolean isComplete;
	private boolean hasAllRequisites;
	private boolean[] hasItem;
	private boolean[] hasCraft;
	private boolean[] hasResearch;
	private boolean[] hasKnow;

	private static final class Page {
		final List<Object> contents = new ArrayList<>();
	}

	/** An inline image, "domain:path:u:v:w:h:scale" in the original's 256x256 UV convention. */
	private record PageImage(ResourceLocation loc, int x, int y, int w, int h, float scale, int aw, int ah) {
		static PageImage parse(String text) {
			String[] s = text.split(":");
			if (s.length != 7) return null;
			try {
				int w = Integer.parseInt(s[4]);
				int h = Integer.parseInt(s[5]);
				float scale = Float.parseFloat(s[6]);
				int aw = (int) (w * scale);
				int ah = (int) (h * scale);
				if (ah > 208 || aw > 140) return null;
				return new PageImage(ResourceLocation.fromNamespaceAndPath(s[0], s[1]), Integer.parseInt(s[2]), Integer.parseInt(s[3]), w, h, scale, aw, ah);
			} catch (Exception e) {
				return null;
			}
		}
	}

	ResearchPageView(ThaumonomiconScreen screen, ResearchEntry research, ResourceLocation recipe) {
		this.screen = screen;
		this.research = research;
		parsePages();
		knownPlayerAspects = new AspectList();
		for (Aspect a : Aspect.aspects.values()) {
			if (knowledge().isResearchKnown("!" + a.getTag().toLowerCase())) knownPlayerAspects.add(a, 1);
		}
		maxAspectPages = Mth.ceil(knownPlayerAspects.size() / 5.0f);
		page = 0;
		if (recipe != null) shownRecipe = recipe;
	}

	void init() {
	}

	private Minecraft mc() {
		return Minecraft.getInstance();
	}

	private Font font() {
		return mc().font;
	}

	private IPlayerKnowledge knowledge() {
		return screen.knowledge();
	}

	private int width() {
		return screen.width;
	}

	private int height() {
		return screen.height;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Stage text and requirements
	// ------------------------------------------------------------------------------------------------------------

	void tick() {
		long nano = System.nanoTime();
		if (nano > lastCheck) {
			parsePages();
			lastCheck = nano + (hold ? 250000000L : 2000000000L);
			if (currentStage > lastStage) hold = false;
		}
	}

	private void checkRequisites() {
		if (research.getStages() == null) return;
		isComplete = knowledge().isResearchComplete(research.getKey());
		while (currentStage >= research.getStages().length) currentStage--;
		if (currentStage < 0) return;
		hasAllRequisites = true;
		hasItem = null;
		hasCraft = null;
		hasResearch = null;
		hasKnow = null;
		ResearchStage stage = research.getStages()[currentStage];
		Object[] o = stage.getObtain();
		if (o != null) {
			hasItem = new boolean[o.length];
			for (int a = 0; a < o.length; a++) {
				hasItem[a] = mc().player != null && ResearchRequirements.isCarrying(mc().player, o[a]);
				if (!hasItem[a]) hasAllRequisites = false;
			}
		}
		Object[] c = stage.getCraft();
		if (c != null) {
			hasCraft = new boolean[c.length];
			int[] refs = stage.getCraftReference();
			for (int a = 0; a < c.length; a++) {
				hasCraft[a] = refs != null && a < refs.length && knowledge().isResearchKnown(ResearchRequirements.researchKeyFor(refs[a]));
				if (!hasCraft[a]) hasAllRequisites = false;
			}
		}
		String[] r = stage.getResearch();
		if (r != null) {
			hasResearch = new boolean[r.length];
			for (int a = 0; a < r.length; a++) {
				hasResearch[a] = screen.knowsStrict(r[a]);
				if (!hasResearch[a]) hasAllRequisites = false;
			}
		}
		ResearchStage.Knowledge[] k = stage.getKnow();
		if (k != null) {
			hasKnow = new boolean[k.length];
			for (int a = 0; a < k.length; a++) {
				hasKnow[a] = knowledge().getKnowledge(k[a].type, k[a].category) >= k[a].amount;
				if (!hasKnow[a]) hasAllRequisites = false;
			}
		}
	}

	private void parsePages() {
		checkRequisites();
		pages.clear();
		if (research.getStages() == null) return;
		boolean complete = false;
		currentStage = knowledge().getResearchStage(research.getKey()) - 1;
		while (currentStage >= research.getStages().length) {
			currentStage--;
			complete = true;
		}
		if (currentStage < 0) currentStage = 0;
		ResearchStage stage = research.getStages()[currentStage];
		ResearchAddendum[] addenda = research.getAddenda() != null && complete ? research.getAddenda() : null;
		generateRecipesLists(stage, addenda);
		String rawText = stage.getTextLocalized().getString();
		if (addenda != null) {
			int ac = 0;
			for (ResearchAddendum addendum : addenda) {
				if (screen.knowsStrict(addendum.getResearch())) {
					ac++;
					rawText = rawText + "<PAGE>" + Component.translatable("tc.addendumtext", ac).getString() + "<BR>" + addendum.getTextLocalized().getString();
				}
			}
		}
		rawText = rawText.replace("<BR>", "~B\n\n").replace("<BR/>", "~B\n\n").replace("<LINE>", "~L").replace("<LINE/>", "~L")
				.replace("<DIV>", "~D").replace("<DIV/>", "~D").replace("<PAGE>", "~P").replace("<PAGE/>", "~P");
		List<PageImage> images = new ArrayList<>();
		for (String s : rawText.split("<IMG>")) {
			int i = s.indexOf("</IMG>");
			if (i < 0) continue;
			String clean = s.substring(0, i);
			PageImage pi = PageImage.parse(clean);
			if (pi == null) {
				rawText = rawText.replaceFirst(java.util.regex.Pattern.quote(clean), "\n");
			} else {
				images.add(pi);
				rawText = rawText.replaceFirst(java.util.regex.Pattern.quote(clean), "~I");
			}
		}
		rawText = rawText.replace("<IMG>", "").replace("</IMG>", "");

		List<String> firstPassText = new ArrayList<>();
		String[] temp = rawText.split("~P", -1);
		for (int a = 0; a < temp.length; a++) {
			String[] temp2 = temp[a].split("~D", -1);
			for (int x = 0; x < temp2.length; x++) {
				String[] temp3 = temp2[x].split("~L", -1);
				for (int b = 0; b < temp3.length; b++) {
					String[] temp4 = temp3[b].split("~I", -1);
					for (int c = 0; c < temp4.length; c++) {
						firstPassText.add(temp4[c]);
						if (c != temp4.length - 1) firstPassText.add("~I");
					}
					if (b != temp3.length - 1) firstPassText.add("~L");
				}
				if (x != temp2.length - 1) firstPassText.add("~D");
			}
			if (a != temp.length - 1) firstPassText.add("~P");
		}
		List<String> parsedText = new ArrayList<>();
		for (String s : firstPassText) parsedText.addAll(wrap(s, PAGEWIDTH));

		int lineHeight = font().lineHeight;
		int heightRemaining = 182;
		int dividerSpace = 0;
		if (research.getKey().equals("KNOWLEDGETYPES")) {
			heightRemaining -= 2;
			int tc = 0;
			for (IPlayerKnowledge.EnumKnowledgeType type : IPlayerKnowledge.EnumKnowledgeType.values()) {
				for (ResearchCategory category : knowledgeCategories()) {
					if (!type.hasFields() && category != null) continue;
					if (knowledge().getKnowledgeRaw(type, category) > 0) {
						tc++;
						break;
					}
				}
			}
			heightRemaining -= 20 * tc;
			dividerSpace = 12;
		}
		if (!isComplete) {
			if (stage.getCraft() != null) {
				heightRemaining -= 18;
				dividerSpace = 15;
			}
			if (stage.getObtain() != null) {
				heightRemaining -= 18;
				dividerSpace = 15;
			}
			if (stage.getKnow() != null) {
				heightRemaining -= 18;
				dividerSpace = 15;
			}
			if (stage.getResearch() != null) {
				heightRemaining -= 18;
				dividerSpace = 15;
			}
		}
		heightRemaining -= dividerSpace;
		Page page1 = new Page();
		List<PageImage> tempImages = new ArrayList<>();
		for (String line : parsedText) {
			if (line.contains("~I")) {
				if (!images.isEmpty()) tempImages.add(images.remove(0));
				line = "";
			}
			if (line.contains("~L")) {
				tempImages.add(PILINE);
				line = "";
			}
			if (line.contains("~D")) {
				tempImages.add(PIDIV);
				line = "";
			}
			if (line.contains("~P")) {
				heightRemaining = 210;
				pages.add(page1);
				page1 = new Page();
				line = "";
			}
			if (!line.isEmpty()) {
				line = line.trim();
				page1.contents.add(line);
				heightRemaining -= lineHeight;
				if (line.endsWith("~B")) heightRemaining -= (int) (lineHeight * 0.66);
			}
			while (!tempImages.isEmpty() && heightRemaining >= tempImages.get(0).ah() + 2) {
				heightRemaining -= tempImages.get(0).ah() + 2;
				page1.contents.add(tempImages.remove(0));
			}
			if (heightRemaining < lineHeight && !page1.contents.isEmpty()) {
				heightRemaining = 210;
				pages.add(page1);
				page1 = new Page();
			}
		}
		if (!page1.contents.isEmpty()) pages.add(page1);
		page1 = new Page();
		heightRemaining = 210;
		while (!tempImages.isEmpty()) {
			if (heightRemaining < tempImages.get(0).ah() + 2) {
				heightRemaining = 210;
				pages.add(page1);
				page1 = new Page();
			} else {
				heightRemaining -= tempImages.get(0).ah() + 2;
				page1.contents.add(tempImages.remove(0));
			}
		}
		if (!page1.contents.isEmpty()) pages.add(page1);
		rhash = research.getKey().hashCode() + currentStage * 50;
		maxPages = pages.size();
	}

	/** Every research category and the "no category" slot, the set the original iterated knowledge over (its category
	 * map has no null key, so a field-less type is only ever counted through... none - same here). */
	private List<ResearchCategory> knowledgeCategories() {
		return new ArrayList<>(ResearchCategories.researchCategories.values());
	}

	/** FontRenderer#listFormattedStringToWidth: hard breaks on newlines, greedy word wrap, and formatting codes carried
	 * onto the continuation line. */
	private List<String> wrap(String text, int maxWidth) {
		List<String> out = new ArrayList<>();
		String[] paragraphs = text.split("\n", -1);
		String carried = "";
		for (String paragraph : paragraphs) {
			String remaining = carried + paragraph;
			while (true) {
				if (font().width(remaining) <= maxWidth) {
					out.add(remaining);
					carried = formatCodes(remaining);
					break;
				}
				int cut = remaining.length();
				int lastSpace = -1;
				for (int i = 1; i <= remaining.length(); i++) {
					if (font().width(remaining.substring(0, i)) > maxWidth) {
						cut = i - 1;
						break;
					}
					if (i < remaining.length() && remaining.charAt(i) == ' ') lastSpace = i;
				}
				int breakAt = lastSpace > 0 ? lastSpace : Math.max(cut, 1);
				String line = remaining.substring(0, breakAt);
				out.add(line);
				carried = formatCodes(line);
				String rest = remaining.substring(breakAt);
				if (rest.startsWith(" ")) rest = rest.substring(1);
				remaining = carried + rest;
			}
		}
		return out;
	}

	/** FontRenderer#getFormatFromString: the colour and style codes still in force at the end of a line. */
	private static String formatCodes(String s) {
		StringBuilder codes = new StringBuilder();
		for (int i = s.indexOf('§'); i >= 0 && i + 1 < s.length(); i = s.indexOf('§', i + 1)) {
			char c = Character.toLowerCase(s.charAt(i + 1));
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				codes.setLength(0);
				codes.append('§').append(c);
			} else if (c >= 'k' && c <= 'o') {
				codes.append('§').append(c);
			} else if (c == 'r') {
				codes.setLength(0);
			}
		}
		return codes.toString();
	}

	// ------------------------------------------------------------------------------------------------------------
	// Recipes
	// ------------------------------------------------------------------------------------------------------------

	private void generateRecipesLists(ResearchStage stage, ResearchAddendum[] addenda) {
		recipeLists.clear();
		recipeOutputs.clear();
		if (stage == null || stage.getRecipes() == null) return;
		for (ResourceLocation rk : stage.getRecipes()) addRecipesToList(rk, recipeLists, recipeOutputs);
		if (addenda == null) return;
		for (ResearchAddendum addendum : addenda) {
			if (addendum.getRecipes() != null && screen.knowsStrict(addendum.getResearch())) {
				for (ResourceLocation rk : addendum.getRecipes()) addRecipesToList(rk, recipeLists, recipeOutputs);
			}
		}
	}

	private static String researchOf(Recipe<?> recipe) {
		if (recipe instanceof ArcaneRecipe r) return r.getResearch();
		if (recipe instanceof CrucibleRecipe r) return r.getResearch();
		if (recipe instanceof InfusionRecipe r) return r.getResearch();
		return null;
	}

	/** One bookmark: every recipe the key stands for that the player may see (a Thaumcraft recipe whose research they
	 * know strictly; any plain crafting recipe), with the item each shows on its bookmark. */
	private void addRecipesToList(ResourceLocation rk, Map<ResourceLocation, List<Object>> lists, Map<ResourceLocation, List<Object>> outputs) {
		if (mc().level == null) return;
		thaumcraft.common.registry.ThaumcraftDustTriggers.Blueprint bp = thaumcraft.common.registry.ThaumcraftDustTriggers.BLUEPRINTS.get(rk.toString());
		if (bp != null) {
			if (screen.knowsStrict(bp.research())) {
				lists.computeIfAbsent(rk, k -> new ArrayList<>()).add(bp);
				outputs.computeIfAbsent(rk, k -> new ArrayList<>()).add(bp.display() != null ? bp.display() : bp);
			}
			return;
		}
		for (RecipeHolder<?> holder : ResearchRecipeCatalog.resolve(mc().level.getRecipeManager(), rk)) {
			Recipe<?> recipe = holder.value();
			String req = researchOf(recipe);
			if (req != null && !req.isEmpty() && !screen.knowsStrict(req.split(",")[0].trim())) continue;
			ItemStack out = displayOutput(recipe);
			lists.computeIfAbsent(rk, k -> new ArrayList<>()).add(holder);
			outputs.computeIfAbsent(rk, k -> new ArrayList<>()).add(out);
		}
	}

	private ItemStack displayOutput(Recipe<?> recipe) {
		if (recipe instanceof InfusionEnchantmentRecipe ie && mc().player != null) {
			ItemStack in = firstStack(ie.getRecipeInput());
			if (!in.isEmpty()) return ie.getRecipeOutput(mc().player, in.copy(), new ArrayList<>());
		}
		try {
			return recipe.getResultItem(mc().level.registryAccess());
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}

	private static ItemStack firstStack(Ingredient ingredient) {
		if (ingredient == null) return ItemStack.EMPTY;
		ItemStack[] items = ingredient.getItems();
		return items.length == 0 ? ItemStack.EMPTY : items[0];
	}

	/** InventoryUtils#cycleItemStack: the ingredient's matching stacks shown one a second, offset per slot. */
	private static ItemStack cycle(Ingredient ingredient, int index) {
		if (ingredient == null) return ItemStack.EMPTY;
		ItemStack[] items = ingredient.getItems();
		if (items.length == 0) return ItemStack.EMPTY;
		// skipping what recipe viewers hide (#c:hidden_from_recipe_viewers), unless that is everything
		List<ItemStack> shown = ResearchRequirements.visibleCandidates(java.util.Arrays.asList(items));
		return shown.get((int) ((System.currentTimeMillis() / 1000L + index) % shown.size()));
	}

	// ------------------------------------------------------------------------------------------------------------
	// Rendering
	// ------------------------------------------------------------------------------------------------------------

	void render(GuiGraphics graphics, int mx, int my, float partialTick) {
		hasRecipePages = false;
		renderingCompound = false;
		if (shownRecipe != lastShownRecipe) {
			blockAccess = null;
			lastShownRecipe = shownRecipe;
		}
		int sw = (width() - PANE_W) / 2;
		int sh = (height() - PANE_H) / 2;
		float var10 = (width() - PANE_W * 1.3f) / 2.0f;
		float var11 = (height() - PANE_H * 1.3f) / 2.0f;
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.pose().pushPose();
		graphics.pose().translate(var10, var11, 0);
		graphics.pose().scale(1.3f, 1.3f, 1f);
		graphics.blit(TEX1, 0, 0, 0, 0, PANE_W, PANE_H);
		graphics.pose().popPose();
		reference.clear();

		if (lastCycle < System.currentTimeMillis()) {
			cycle++;
			lastCycle = System.currentTimeMillis() + 1000L;
			if (cycleMultiblockLines && blockAccess != null) sliceLine = (sliceLine + 1) % (blockAccess.height() + 1);
		}
		int current = 0;
		boolean drewPage = false;
		for (Page p : pages) {
			if ((current == page || current == page + 1) && current < maxPages) {
				drawPage(graphics, p, current % 2, sw, sh - 10);
				drewPage = true;
			}
			if (++current > page + 1) break;
		}
		if (drewPage || pages.isEmpty()) drawPageExtras(graphics, sw, sh - 10 + lastPageY, mx, my);

		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		float bob = Mth.sin((mc().player != null ? mc().player.tickCount : 0) / 3.0f) * 0.2f + 0.1f;
		if (!history.isEmpty()) drawTexturedModalRectScaled(graphics, sw + 118, sh + 190, 38, 202, 20, 12, bob);
		if (page > 0 && shownRecipe == null) drawTexturedModalRectScaled(graphics, sw - 16, sh + 190, 0, 184, 12, 8, bob);
		if (page < maxPages - 2 && shownRecipe == null) drawTexturedModalRectScaled(graphics, sw + 262, sh + 190, 12, 184, 12, 8, bob);
		if (tipText != null) {
			ResearchTooltip.draw(graphics, font(), tipText, mx, my + 12, 11, width(), height());
			tipText = null;
		}
		if (!history.isEmpty()) {
			int hx = mx - (sw + 118);
			int hy = my - (sh + 190);
			if (hx >= 0 && hy >= 0 && hx < 20 && hy < 12) {
				graphics.pose().pushPose();
				graphics.pose().translate(0, 0, 450);
				graphics.drawString(font(), Component.translatable("recipe.return"), mx, my, 0xFFFFFF, true);
				graphics.pose().popPose();
			}
		}
	}

	private ResourceLocation lastShownRecipe;

	/** Where the last drawn page's text ended (relative to its top), for the warp warning that sits under it. */
	private int lastPageY;

	private void drawPage(GuiGraphics graphics, Page p, int side, int x, int y) {
		int y0 = y;
		RenderSystem.enableBlend();
		if (page == 0 && side == 0) {
			graphics.blit(TEX1, x + 4, y - 7, 24, 184, 96, 4);
			graphics.blit(TEX1, x + 4, y + 10, 24, 184, 96, 4);
			String name = research.getLocalizedName().getString();
			int offset = font().width(name);
			if (offset <= 140) {
				graphics.drawString(font(), name, x - 15 + 140 / 2 - offset / 2, y, 2105376, false);
			} else {
				float vv = 140.0f / offset;
				graphics.pose().pushPose();
				graphics.pose().translate(x - 15 + 140 / 2 - offset / 2 * vv, y + 1.0f * vv, 0);
				graphics.pose().scale(vv, vv, vv);
				graphics.drawString(font(), name, 0, 0, 2105376, false);
				graphics.pose().popPose();
			}
			y += 28;
		}
		for (Object content : p.contents) {
			if (content instanceof String s) {
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.drawString(font(), s.replace("~B", ""), x - 15 + side * 152, y - 6, 0, false);
				y += font().lineHeight;
				if (s.endsWith("~B")) y += (int) (font().lineHeight * 0.66);
			} else if (content instanceof PageImage pi) {
				graphics.setColor(1f, 1f, 1f, 1f);
				int pad = (140 - pi.aw()) / 2;
				graphics.pose().pushPose();
				graphics.pose().translate(x - 15 + side * 152 + pad, y - 5, 0);
				graphics.pose().scale(pi.scale(), pi.scale(), pi.scale());
				graphics.blit(pi.loc(), 0, 0, pi.x(), pi.y(), pi.w(), pi.h());
				graphics.pose().popPose();
				y += pi.ah() + 2;
			}
		}
		if (side == 0) lastPageY = y - y0;
	}

	/** The rest of the original's drawPage, which it ran for each visible page: side tabs, bookmarks, requirement rows,
	 * the knowledge list, and whichever insert or recipe page is open (else the warp warning). */
	private void drawPageExtras(GuiGraphics graphics, int x, int y, int mx, int my) {
		x = (width() - PANE_W) / 2;
		RenderSystem.enableBlend();
		if (knowledge().isResearchComplete("FIRSTSTEPS")) {
			int ty = (height() - PANE_H) / 2 + 9;
			int le = mouseInside(x - 48, ty, 25, 16, mx, my) ? 0 : 3;
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 48, ty, 25, 16, mx, my, "tc.aspect.name");
			graphics.blit(TEX1, x - 48 + le, ty, 76, 232, 24 - le, 16);
			graphics.blit(TEX1, x - 28, ty, 100, 232, 4, 16);
		}
		if (knowledge().isResearchComplete("KNOWLEDGETYPES") && !research.getKey().equals("KNOWLEDGETYPES")) {
			int ty = (height() - PANE_H) / 2 + 32;
			int le = mouseInside(x - 48, ty, 25, 16, mx, my) ? 0 : 3;
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 48, ty, 25, 16, mx, my, "tc.knowledge.name");
			graphics.blit(TEX1, x - 49 + le, ty, 44, 232, 24 - le, 16);
			graphics.blit(TEX1, x - 29, ty, 100, 232, 4, 16);
		}
		if (research.getStages() == null) return;
		ResearchStage stage = research.getStages()[currentStage];
		if (stage.getRecipes() != null) drawRecipeBookmarks(graphics, x, mx, my);
		if (page == 0 && !isComplete) drawRequirements(graphics, x, mx, my, stage);
		if (knowledge().isResearchComplete("KNOWLEDGETYPES") && research.getKey().equals("KNOWLEDGETYPES")) {
			drawKnowledges(graphics, x, (height() - PANE_H) / 2 - 16 + 210, mx, my, true);
		}
		if (showingAspects) {
			drawAspectsInsert(graphics, mx, my);
		} else if (showingKnowledge) {
			drawKnowledgesInsert(graphics, mx, my);
		} else if (shownRecipe != null) {
			drawRecipe(graphics, mx, my);
		} else if (stage.getWarp() > 0 && !isComplete) {
			int warp = Math.min(5, stage.getWarp());
			ThaumonomiconScreen.drawForbidden(graphics, x - 57, y - 40);
			String s = Component.translatable("tc.forbidden.level." + warp).getString();
			graphics.drawString(font(), s, x - 56 - font().width(s) / 2, y - 43, 11180543, false);
			String text = Component.translatable("tc.warp.warn").getString();
			drawPopupAtRaw(x - 67, y - 50, 20, 20, mx, my, text.replaceAll("%n", s));
		}
	}

	private void drawKnowledgesInsert(GuiGraphics graphics, int mx, int my) {
		allowWithPagePopup = true;
		graphics.setColor(1f, 1f, 1f, 1f);
		int x = (width() - 256) / 2;
		int y = (height() - 256) / 2;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.blit(TEX4, x, y, 0, 0, 255, 255);
		drawKnowledges(graphics, x + 60, (height() - PANE_H) / 2 + 75, mx, my, false);
		graphics.pose().popPose();
		allowWithPagePopup = false;
	}

	private void drawKnowledges(GuiGraphics graphics, int x, int y, int mx, int my, boolean inpage) {
		y -= 18;
		boolean drewSomething = false;
		int tc = 0;
		int ka = ResearchCategories.researchCategories.size();
		for (IPlayerKnowledge.EnumKnowledgeType type : IPlayerKnowledge.EnumKnowledgeType.values()) {
			int fc = 0;
			int hs = (int) (164.0f / ka);
			boolean b = false;
			for (ResearchCategory category : knowledgeCategories()) {
				if (!type.hasFields() && category != null) continue;
				int amt = knowledge().getKnowledge(type, category);
				int par = knowledge().getKnowledgeRaw(type, category) % type.getProgression();
				if (amt <= 0 && par <= 0) continue;
				drewSomething = true;
				int ix = x - 10 + (inpage ? 18 : hs) * fc;
				int iy = y - tc * (inpage ? 20 : 28);
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.pose().pushPose();
				graphics.pose().translate(ix, iy, 0);
				graphics.pose().scale(0.0625f, 0.0625f, 0.0625f);
				graphics.blit(KNOW_TYPE[type.ordinal()], 0, 0, 0, 0, 255, 255, 256, 256);
				if (type.hasFields() && category != null) {
					graphics.setColor(1f, 1f, 1f, 0.75f);
					graphics.pose().translate(0, 0, 1);
					graphics.pose().scale(0.66f, 0.66f, 0.66f);
					graphics.blit(category.icon, 66, 66, 0, 0, 255, 255, 256, 256);
				}
				graphics.pose().popPose();
				graphics.setColor(1f, 1f, 1f, 1f);
				graphics.pose().pushPose();
				graphics.pose().translate(0, 0, 5);
				String s = "" + amt;
				graphics.drawString(font(), s, ix + 16 - font().width(s), iy + 8, 0xFFFFFF, true);
				graphics.pose().popPose();
				s = Component.translatable("tc.type." + type.toString().toLowerCase()).getString();
				if (type.hasFields() && category != null) s = s + ": " + ResearchCategories.getCategoryName(category.key).getString();
				drawPopupAtRaw(ix, iy, 16, 16, mx, my, s);
				if (par > 0) {
					graphics.setColor(1f, 1f, 1f, 0.75f);
					int l = (int) (par / (float) type.getProgression() * 16.0f);
					graphics.blit(TEX1, ix, y + 17 - tc * (inpage ? 20 : 28), 0, 232, l, 2);
					graphics.blit(TEX1, ix + l, y + 17 - tc * (inpage ? 20 : 28), l, 234, 16 - l, 2);
					graphics.setColor(1f, 1f, 1f, 1f);
				}
				fc++;
				b = true;
			}
			if (b) tc++;
		}
		if (inpage && drewSomething) {
			graphics.setColor(1f, 1f, 1f, 1f);
			graphics.blit(TEX1, x + 4, y - tc * 20 + 12, 24, 184, 96, 8);
		}
	}

	private void drawRequirements(GuiGraphics graphics, int x, int mx, int my, ResearchStage stage) {
		int y = (height() - PANE_H) / 2 - 16 + 210;
		boolean b = false;
		if (stage.getResearch() != null) {
			y -= 18;
			b = true;
			int shift = 24;
			RenderSystem.enableBlend(); // the 25% alpha only shows with blending on - without it these labels drew solid black
			graphics.setColor(1f, 1f, 1f, 0.25f);
			graphics.blit(TEX1, x - 12, y - 1, 200, 232, 56, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 15, y, 16, 16, mx, my, "tc.need.research");
			if (hasResearch != null) {
				if (hasResearch.length != stage.getResearch().length) hasResearch = new boolean[stage.getResearch().length];
				int ss = stage.getResearch().length > 6 ? 110 / stage.getResearch().length : 18;
				for (int a = 0; a < stage.getResearch().length; a++) {
					String key = stage.getResearch()[a];
					String[] icons = stage.getResearchIcon();
					Object loc = icons != null && a < icons.length && icons[a] != null ? ResourceLocation.tryParse(icons[a]) : DUMMY_RESEARCH;
					if (loc == null) loc = DUMMY_RESEARCH;
					String text = Component.translatable("research." + key + ".text").getString();
					if (key.startsWith("!")) {
						Aspect as = Aspect.getAspect(key.replace("!", ""));
						if (as != null) {
							loc = as;
							text = as.getName();
						}
					}
					ResearchEntry re = ResearchCategories.getResearch(key);
					float cr = 1f, cg = 1f, cb = 1f;
					if (re != null && re.getIcons() != null) {
						loc = re.getIcons()[(int) (System.currentTimeMillis() / 1000L % re.getIcons().length)];
						text = re.getLocalizedName().getString();
					} else if (key.startsWith("m_")) {
						loc = DUMMY_MAP;
					} else if (key.startsWith("c_")) {
						loc = DUMMY_CHEST;
					} else if (key.startsWith("f_")) {
						loc = DUMMY_FLASK;
					} else {
						cr = 0.5f;
						cg = 0.75f;
					}
					int ix = x - 15 + shift;
					RenderSystem.enableBlend();
					if (loc instanceof Aspect as) {
						int c = as.getColor();
						graphics.setColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1f);
						graphics.blit(as.getImage(), ix, y, 0, 0, 16, 16, 16, 16);
					} else if (loc instanceof ResourceLocation rl) {
						graphics.setColor(cr, cg, cb, 1f);
						graphics.blit(rl, ix, y, 0, 0, 16, 16, 16, 16);
					} else if (loc instanceof ItemStack stack) {
						graphics.setColor(1f, 1f, 1f, 1f);
						graphics.renderItem(stack, ix, y);
					}
					graphics.setColor(1f, 1f, 1f, 1f);
					if (hasResearch[a]) drawTick(graphics, ix + 8, y);
					drawPopupAtRaw(ix, y, 16, 16, mx, my, text);
					shift += ss;
				}
			}
		}
		if (stage.getObtain() != null) {
			y -= 18;
			b = true;
			int shift = 24;
			RenderSystem.enableBlend(); // the 25% alpha only shows with blending on - without it these labels drew solid black
			graphics.setColor(1f, 1f, 1f, 0.25f);
			graphics.blit(TEX1, x - 12, y - 1, 200, 216, 56, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 15, y, 16, 16, mx, my, "tc.need.obtain");
			if (hasItem != null) {
				if (hasItem.length != stage.getObtain().length) hasItem = new boolean[stage.getObtain().length];
				int ss = stage.getObtain().length > 6 ? 110 / stage.getObtain().length : 18;
				for (int a = 0; a < stage.getObtain().length; a++) {
					ItemStack stack = ResearchRequirements.displayStack(stage.getObtain()[a], a, mc().level != null ? mc().level.registryAccess() : null);
					drawStackAt(graphics, stack, x - 15 + shift, y, mx, my, true);
					if (hasItem[a]) drawTick(graphics, x - 15 + shift + 8, y);
					shift += ss;
				}
			}
		}
		if (stage.getCraft() != null) {
			y -= 18;
			b = true;
			int shift = 24;
			RenderSystem.enableBlend(); // the 25% alpha only shows with blending on - without it these labels drew solid black
			graphics.setColor(1f, 1f, 1f, 0.25f);
			graphics.blit(TEX1, x - 12, y - 1, 200, 200, 56, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 15, y, 16, 16, mx, my, "tc.need.craft");
			if (hasCraft != null) {
				if (hasCraft.length != stage.getCraft().length) hasCraft = new boolean[stage.getCraft().length];
				int ss = stage.getCraft().length > 6 ? 110 / stage.getCraft().length : 18;
				for (int a = 0; a < stage.getCraft().length; a++) {
					ItemStack stack = ResearchRequirements.displayStack(stage.getCraft()[a], a, mc().level != null ? mc().level.registryAccess() : null);
					drawStackAt(graphics, stack, x - 15 + shift, y, mx, my, true);
					if (hasCraft[a]) drawTick(graphics, x - 15 + shift + 8, y);
					shift += ss;
				}
			}
		}
		if (stage.getKnow() != null) {
			y -= 18;
			b = true;
			int shift = 24;
			RenderSystem.enableBlend(); // the 25% alpha only shows with blending on - without it these labels drew solid black
			graphics.setColor(1f, 1f, 1f, 0.25f);
			graphics.blit(TEX1, x - 12, y - 1, 200, 184, 56, 16);
			graphics.setColor(1f, 1f, 1f, 1f);
			drawPopupAt(x - 15, y, 16, 16, mx, my, "tc.need.know");
			if (hasKnow != null) {
				if (hasKnow.length != stage.getKnow().length) hasKnow = new boolean[stage.getKnow().length];
				int ss = stage.getKnow().length > 6 ? 110 / stage.getKnow().length : 18;
				for (int a = 0; a < stage.getKnow().length; a++) {
					ResearchStage.Knowledge kn = stage.getKnow()[a];
					int ix = x - 15 + shift;
					graphics.setColor(1f, 1f, 1f, 1f);
					graphics.pose().pushPose();
					graphics.pose().translate(ix, y, 0);
					graphics.pose().scale(0.0625f, 0.0625f, 0.0625f);
					graphics.blit(KNOW_TYPE[kn.type.ordinal()], 0, 0, 0, 0, 255, 255, 256, 256);
					if (kn.type.hasFields() && kn.category != null) {
						graphics.pose().translate(32, 32, 1);
						graphics.pose().scale(0.75f, 0.75f, 0.75f);
						graphics.blit(kn.category.icon, 0, 0, 0, 0, 255, 255, 256, 256);
					}
					graphics.pose().popPose();
					String am = (hasKnow[a] ? "" : ChatFormatting.RED.toString()) + kn.amount;
					graphics.pose().pushPose();
					graphics.pose().translate(ix + 16 - font().width(am) / 2f, y + 12, 5);
					graphics.pose().scale(0.5f, 0.5f, 0.5f);
					graphics.drawString(font(), am, 0, 0, 0xFFFFFF, true);
					graphics.pose().popPose();
					if (hasKnow[a]) drawTick(graphics, ix + 8, y);
					String s = Component.translatable("tc.type." + kn.type.toString().toLowerCase()).getString();
					if (kn.type.hasFields() && kn.category != null) s = s + ": " + ResearchCategories.getCategoryName(kn.category.key).getString();
					drawPopupAtRaw(ix, y, 16, 16, mx, my, s);
					shift += ss;
				}
			}
		}
		if (b) {
			y -= 12;
			graphics.setColor(1f, 1f, 1f, 1f);
			graphics.blit(TEX1, x + 4, y - 2, 24, 184, 96, 8);
			if (hasAllRequisites) {
				hrx = x + 20;
				hry = y - 6;
				if (hold) {
					String s2 = Component.translatable("tc.stage.hold").getString();
					graphics.drawString(font(), s2, (int) (x + 52 - font().width(s2) / 2.0f), y - 4, 0xFFFFFF, true);
				} else {
					if (mouseInside(hrx, hry, 64, 12, mx, my)) graphics.setColor(1f, 1f, 1f, 1f);
					else graphics.setColor(0.8f, 0.8f, 0.9f, 1f);
					graphics.blit(TEX1, hrx, hry, 84, 216, 64, 12);
					graphics.setColor(1f, 1f, 1f, 1f);
					String s2 = Component.translatable("tc.stage.complete").getString();
					graphics.drawString(font(), s2, (int) (x + 52 - font().width(s2) / 2.0f), y - 4, 0xFFFFFF, true);
				}
			}
		}
	}

	/** The small green tick over a met requirement, drawn above any item. */
	private void drawTick(GuiGraphics graphics, int x, int y) {
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 250);
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.blit(TEX1, x, y, 159, 207, 10, 10);
		graphics.pose().popPose();
	}

	private void drawRecipeBookmarks(GuiGraphics graphics, int x, int mx, int my) {
		Random rng = new Random(rhash);
		int y = (height() - PANE_H) / 2 - 8;
		allowWithPagePopup = true;
		if (!recipeOutputs.isEmpty()) {
			int space = Math.min(25, 200 / recipeOutputs.size());
			for (Map.Entry<ResourceLocation, List<Object>> e : recipeOutputs.entrySet()) {
				List<Object> list = e.getValue();
				if (list == null || list.isEmpty()) continue;
				int i = Math.max(0, cycle) % list.size();
				int sh = rng.nextInt(3);
				int le = rng.nextInt(3) + (mouseInside(x + 280, y - 1, 30, 16, mx, my) ? 0 : 3);
				RenderSystem.enableBlend();
				if (e.getKey().equals(shownRecipe)) graphics.setColor(1f, 0.5f, 0.5f, 1f);
				else graphics.setColor(1f, 1f, 1f, 1f);
				graphics.blit(TEX1, x + 280 + sh, y - 1, 120 + le, 232, 28, 16);
				graphics.blit(TEX1, x + 280 + sh, y - 1, 116, 232, 4, 16);
				graphics.setColor(1f, 1f, 1f, 1f);
				Object icon = list.get(i);
				if (icon instanceof ItemStack stack) {
					drawStackAt(graphics, stack, x + 287 + sh - le, y - 1, mx, my, false, false);
				} else if (icon instanceof thaumcraft.common.registry.ThaumcraftDustTriggers.Blueprint bp) {
					BlueprintRenderer r = blueprintIcons.computeIfAbsent(e.getKey(), k -> new BlueprintRenderer(bp.parts(), true));
					r.render(graphics, x + 295 + sh - le, y + 6 + r.height(), 150, 4.0f, 25.0f, -45.0f, 0);
				}
				y += space;
			}
		}
		allowWithPagePopup = false;
	}

	private void drawRecipe(GuiGraphics graphics, int mx, int my) {
		allowWithPagePopup = true;
		graphics.setColor(1f, 1f, 1f, 1f);
		int x = (width() - 256) / 2;
		int y = (height() - 256) / 2;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.blit(TEX4, x, y, 0, 0, 255, 255);
		List<Object> list = recipeLists.get(shownRecipe);
		if (list == null || list.isEmpty()) list = drilldownLists.get(shownRecipe);
		if (list != null && !list.isEmpty()) {
			hasRecipePages = list.size() > 1;
			recipePageMax = list.size() - 1;
			if (recipePage > recipePageMax) recipePage = recipePageMax;
			Object entry = list.get(recipePage % list.size());
			Recipe<?> recipe = entry instanceof RecipeHolder<?> holder ? holder.value() : null;
			if (entry instanceof thaumcraft.common.registry.ThaumcraftDustTriggers.Blueprint bp) {
				drawCompoundCraftingPage(graphics, x + 128, y + 128, mx, my, bp);
				renderingCompound = true;
			} else if (recipe instanceof ArcaneRecipe ar) drawArcaneCraftingPage(graphics, x + 128, y + 128, mx, my, ar);
			else if (recipe instanceof CraftingRecipe cr) drawCraftingPage(graphics, x + 128, y + 128, mx, my, cr);
			else if (recipe instanceof CrucibleRecipe cr) drawCruciblePage(graphics, x + 128, y + 128, mx, my, cr);
			else if (recipe instanceof InfusionRecipe ir) drawInfusionPage(graphics, x + 128, y + 128, mx, my, ir);
			if (hasRecipePages) {
				float bob = Mth.sin((mc().player != null ? mc().player.tickCount : 0) / 3.0f) * 0.2f + 0.1f;
				if (recipePage > 0) drawTexturedModalRectScaled(graphics, x + 40, y + 232, 0, 184, 12, 8, bob);
				if (recipePage < recipePageMax) drawTexturedModalRectScaled(graphics, x + 204, y + 232, 12, 184, 12, 8, bob);
			}
		}
		graphics.pose().popPose();
		allowWithPagePopup = false;
	}

	/** The original's drawCompoundCraftingPage: the structure to build, the materials along the bottom, and the layer toggle. */
	private void drawCompoundCraftingPage(GuiGraphics graphics, int x, int y, int mx, int my, thaumcraft.common.registry.ThaumcraftDustTriggers.Blueprint bp) {
		if (blockAccess == null) {
			blockAccess = new BlueprintRenderer(bp.parts(), false);
			sliceLine = 0;
		}
		drawTitle(graphics, "recipe.type.construct", x, y);
		int s = Math.max(Math.max(bp.parts()[0].length, bp.parts()[0][0].length), bp.parts().length) * 2;
		blockAccess.render(graphics, x, y, 300, 38 - s, 25.0f, -45.0f, sliceLine);
		for (int a = 0; a < bp.ingredients().size(); a++) {
			drawStackAt(graphics, bp.ingredients().get(a), x - 85 + a * 17, y + 90, mx, my, true);
		}
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, mouseInside(x + 80, y + 100, 8, 8, mx, my) ? 1.0f : 0.75f);
		graphics.blit(TEX1, x + 80, y + 100, cycleMultiblockLines ? 168 : 160, 224, 8, 8);
		graphics.setColor(1f, 1f, 1f, 1f);
	}

	private void drawTitle(GuiGraphics graphics, String key, int x, int y) {
		String text = Component.translatable(key).getString();
		graphics.drawString(font(), text, x - font().width(text) / 2, y - 104, 5263440, false);
	}

	private void drawArcaneCraftingPage(GuiGraphics graphics, int x, int y, int mx, int my, ArcaneRecipe recipe) {
		RenderSystem.enableBlend();
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(2f, 2f, 1f);
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.blit(TEX2, -26, -26, 112, 15, 52, 52);
		graphics.blit(TEX2, -8, -46, 20, 3, 16, 16);
		graphics.setColor(1f, 1f, 1f, 0.4f);
		graphics.blit(TEX2, -6, 40, 68, 76, 12, 12);
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.pose().popPose();
		String text = "" + recipe.getVis();
		int offset = font().width(text);
		graphics.drawString(font(), text, x - offset / 2, y + 90, 5263440, false);
		drawPopupAt(x - offset / 2 - 15, y + 75, 30, 30, mx, my, "wandtable.text1");
		drawStackAt(graphics, recipe.getResultItem(mc().level.registryAccess()), x - 8, y - 84, mx, my, false);
		AspectList crystals = recipe.getCrystals();
		if (crystals != null) {
			int a = 0;
			int sz = crystals.size();
			for (Aspect aspect : crystals.getAspects()) {
				drawStackAt(graphics, crystalStack(aspect, crystals.getAmount(aspect)), x + 4 - sz * 10 + a * 20, y + 59, mx, my, true);
				a++;
			}
		}
		if (recipe instanceof ArcaneShapedRecipe shaped) {
			drawTitle(graphics, "recipe.type.arcane", x, y);
			int rw = shaped.getWidth();
			int rh = shaped.getHeight();
			List<Ingredient> items = shaped.getIngredients();
			for (int i = 0; i < rw && i < 3; i++) {
				for (int j = 0; j < rh && j < 3; j++) {
					drawStackAt(graphics, cycle(items.get(i + j * rw), i + j * rw), x - 40 + i * 32, y - 40 + j * 32, mx, my, true);
				}
			}
		} else {
			drawTitle(graphics, "recipe.type.arcane.shapeless", x, y);
			List<Ingredient> items = recipe.getIngredients();
			for (int k = 0; k < items.size() && k < 9; k++) {
				drawStackAt(graphics, cycle(items.get(k), k), x - 40 + k % 3 * 32, y - 40 + k / 3 * 32, mx, my, true);
			}
		}
	}

	private static ItemStack crystalStack(Aspect aspect, int count) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get(), Math.max(1, count));
		if (stack.getItem() instanceof IEssentiaContainerItem container) container.setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	private void drawCraftingPage(GuiGraphics graphics, int x, int y, int mx, int my, CraftingRecipe recipe) {
		RenderSystem.enableBlend();
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(2f, 2f, 1f);
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.blit(TEX2, -26, -26, 60, 15, 51, 52);
		graphics.blit(TEX2, -8, -46, 20, 3, 16, 16);
		graphics.pose().popPose();
		drawStackAt(graphics, recipe.getResultItem(mc().level.registryAccess()), x - 8, y - 84, mx, my, false);
		if (recipe instanceof ShapedRecipe shaped) {
			drawTitle(graphics, "recipe.type.workbench", x, y);
			int rw = shaped.getWidth();
			int rh = shaped.getHeight();
			List<Ingredient> items = shaped.getIngredients();
			for (int i = 0; i < rw && i < 3; i++) {
				for (int j = 0; j < rh && j < 3; j++) {
					drawStackAt(graphics, cycle(items.get(i + j * rw), i + j * rw), x - 40 + i * 32, y - 40 + j * 32, mx, my, true);
				}
			}
		} else {
			drawTitle(graphics, "recipe.type.workbenchshapeless", x, y);
			List<Ingredient> items = recipe.getIngredients();
			for (int k = 0; k < items.size() && k < 9; k++) {
				drawStackAt(graphics, cycle(items.get(k), k), x - 40 + k % 3 * 32, y - 40 + k / 3 * 32, mx, my, true);
			}
		}
	}

	private void drawCruciblePage(GuiGraphics graphics, int x, int y, int mx, int my, CrucibleRecipe rc) {
		drawTitle(graphics, "recipe.type.crucible", x, y);
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(2f, 2f, 1f);
		graphics.blit(TEX2, -28, -29, 0, 3, 56, 17);
		graphics.pose().translate(0, 32, 0);
		graphics.blit(TEX2, -28, -44, 0, 20, 56, 48);
		graphics.pose().translate(0, -8, 0);
		graphics.blit(TEX2, -25, -50, 100, 84, 11, 13);
		graphics.pose().popPose();
		AspectList aspects = rc.getAspects();
		int total = 0;
		int rows = (aspects.size() - 1) / 3;
		int shift = (3 - aspects.size() % 3) * 10;
		int sx = x - 28;
		int sy = y + 8 - 10 * rows;
		for (Aspect tag : aspects.getAspectsSortedByName()) {
			int m = total / 3 >= rows && (rows > 1 || aspects.size() < 3) ? 1 : 0;
			int vx = sx + total % 3 * 20 + shift * m;
			int vy = sy + total / 3 * 20;
			drawTag(graphics, vx, vy, tag, aspects.getAmount(tag));
			if (mx >= vx && my >= vy && mx < vx + 16 && my < vy + 16) tipText = List.of(tag.getName(), tag.getLocalizedDescription().getString());
			total++;
		}
		drawStackAt(graphics, rc.getRecipeOutput(), x - 8, y - 50, mx, my, false);
		drawStackAt(graphics, cycle(rc.getCatalyst(), 0), x - 64, y - 56, mx, my, true);
	}

	private void drawInfusionPage(GuiGraphics graphics, int x, int y, int mx, int my, InfusionRecipe ri) {
		List<Ingredient> components = ri.getComponents();
		AspectList aspects = ri.getAspects();
		ItemStack output = ri.getRecipeOutput();
		ItemStack input = firstStack(ri.getRecipeInput());
		if (ri instanceof thaumcraft.common.lib.crafting.RunicArmorRecipe runic && !runic.getDisplay().isEmpty() && mc().player != null) {
			input = runic.getDisplay();
			components = runic.getComponents(input);
			List<ItemStack> com = new ArrayList<>();
			for (Ingredient s : components) com.add(firstStack(s));
			aspects = ri.getAspects(mc().player, input, com);
			output = ri.getRecipeOutput(mc().player, input.copy(), com);
		} else if (ri instanceof InfusionEnchantmentRecipe && mc().player != null && !input.isEmpty()) {
			List<ItemStack> com = new ArrayList<>();
			for (Ingredient s : components) com.add(firstStack(s));
			aspects = ri.getAspects(mc().player, input, com);
			output = ri.getRecipeOutput(mc().player, input.copy(), com);
		}
		drawTitle(graphics, "recipe.type.infusion", x, y);
		RenderSystem.enableBlend();
		graphics.setColor(1f, 1f, 1f, 1f);
		graphics.pose().pushPose();
		graphics.pose().translate(x, y + 20, 0);
		graphics.pose().scale(2f, 2f, 1f);
		graphics.blit(TEX2, -28, -56, 0, 3, 56, 17);
		graphics.pose().translate(0, 19, 0);
		graphics.blit(TEX2, -28, -55, 200, 77, 60, 44);
		graphics.pose().popPose();
		int total = 0;
		int rows = (aspects.size() - 1) / 5;
		int shift = (5 - aspects.size() % 5) * 10;
		int sx = x - 48;
		int sy = y + 50 - 10 * rows;
		for (Aspect tag : aspects.getAspectsSortedByName()) {
			int m = total / 5 >= rows && (rows > 1 || aspects.size() < 5) ? 1 : 0;
			int vx = sx + total % 5 * 20 + shift * m;
			int vy = sy + total / 5 * 20;
			drawTag(graphics, vx, vy, tag, aspects.getAmount(tag));
			if (mx >= vx && my >= vy && mx < vx + 16 && my < vy + 16) tipText = List.of(tag.getName(), tag.getLocalizedDescription().getString());
			total++;
		}
		drawStackAt(graphics, output, x - 8, y - 85, mx, my, false);
		ItemStack rinp = ri instanceof thaumcraft.common.lib.crafting.RunicArmorRecipe runic2 && !runic2.getDisplay().isEmpty()
				? runic2.getDisplay().copy() : cycle(ri.getRecipeInput(), 0).copy();
		drawStackAt(graphics, rinp, x - 8, y - 16, mx, my, true);
		int le = components.size();
		List<ItemStack> cmps = new ArrayList<>();
		if (le > 0) {
			float pieSlice = 360 / le;
			float currentRot = -90.0f;
			for (int a = 0; a < le; a++) {
				int xx = (int) (Mth.cos(currentRot / 180.0f * (float) Math.PI) * 40.0f) - 8;
				int yy = (int) (Mth.sin(currentRot / 180.0f * (float) Math.PI) * 40.0f) - 8;
				currentRot += pieSlice;
				ItemStack is = cycle(components.get(a), 0);
				drawStackAt(graphics, is.copyWithCount(1), x + xx, y - 8 + yy, mx, my, true);
				cmps.add(is.copy());
			}
		}
		int inst = Math.min(5, (mc().player != null ? ri.getInstability(mc().player, rinp, cmps) : ri.getInstability()) / 2);
		String text = Component.translatable("tc.inst").getString() + " " + Component.translatable("tc.inst." + inst).getString();
		graphics.drawString(font(), text, x - font().width(text) / 2, y + 94, 5263440, false);
	}

	/** UtilsFX#drawTag: the aspect icon in its colour with the amount in small outlined figures at its corner. */
	private void drawTag(GuiGraphics graphics, int x, int y, Aspect aspect, int amount) {
		if (aspect == null) return;
		int c = aspect.getColor();
		RenderSystem.enableBlend();
		graphics.setColor((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1f);
		graphics.blit(aspect.getImage(), x, y, 0, 0, 16, 16, 16, 16);
		graphics.setColor(1f, 1f, 1f, 1f);
		if (amount > 0) {
			String am = String.valueOf(amount);
			int sw = font().width(am);
			graphics.pose().pushPose();
			graphics.pose().translate(0, 0, 10);
			// the original's largeTagText: figures at full size instead of half
			float q = 0.5f;
			if (!thaumcraft.common.config.ThaumcraftConfig.client(thaumcraft.common.config.ThaumcraftConfig.LARGE_TAG_TEXT)) {
				graphics.pose().scale(0.5f, 0.5f, 0.5f);
				q = 1.0f;
			}
			int tx = (int) ((32 - sw + x * 2) * q);
			int ty = (int) ((32 - font().lineHeight + y * 2) * q);
			graphics.drawString(font(), am, tx + 1, ty, 0, false);
			graphics.drawString(font(), am, tx - 1, ty, 0, false);
			graphics.drawString(font(), am, tx, ty + 1, 0, false);
			graphics.drawString(font(), am, tx, ty - 1, 0, false);
			graphics.pose().translate(0, 0, 1);
			graphics.drawString(font(), am, tx, ty, 0xFFFFFF, false);
			graphics.pose().popPose();
		}
	}

	private void drawAspectsInsert(GuiGraphics graphics, int mx, int my) {
		allowWithPagePopup = true;
		graphics.setColor(1f, 1f, 1f, 1f);
		int x = (width() - 256) / 2;
		int y = (height() - 256) / 2;
		graphics.pose().pushPose();
		graphics.pose().translate(0, 0, 200);
		graphics.blit(TEX4, x, y, 0, 0, 255, 255);
		if (shownAspect != null) {
			drawAspectItems(graphics, x + 60, y + 24, mx, my);
		} else {
			drawAspectPage(graphics, x + 60, y + 24, mx, my);
		}
		graphics.pose().popPose();
		allowWithPagePopup = false;
	}

	private void drawAspectPage(GuiGraphics graphics, int x, int y, int mx, int my) {
		if (knownPlayerAspects == null || knownPlayerAspects.size() == 0) return;
		int count = -1;
		int start = aspectsPage * 5;
		for (Aspect aspect : knownPlayerAspects.getAspectsSortedByName()) {
			if (++count < start) continue;
			if (count > start + 4) break;
			if (aspect.getImage() == null) continue;
			int ty = y + count % 5 * 40;
			if (mx >= x && my >= ty && mx < x + 40 && my < ty + 40) {
				RenderSystem.enableBlend();
				graphics.setColor(1f, 1f, 1f, 0.5f);
				graphics.pose().pushPose();
				graphics.pose().translate(x - 2, ty - 2, 0);
				graphics.pose().scale(2f, 2f, 1f);
				graphics.blit(TEX3, 0, 0, 0, 0, 16, 16, 16, 16);
				graphics.pose().popPose();
				graphics.setColor(1f, 1f, 1f, 1f);
				tipText = List.of(Component.translatable("tc.aspect.showitems").getString());
			}
			graphics.pose().pushPose();
			graphics.pose().translate(x + 2, y + 2 + count % 5 * 40, 0);
			graphics.pose().scale(1.5f, 1.5f, 1.5f);
			drawTag(graphics, 0, 0, aspect, 0);
			graphics.pose().popPose();
			drawSmallCentered(graphics, aspect.getName(), x + 16, y + 29 + count % 5 * 40, 5263440);
			Aspect[] comps = aspect.getComponents();
			if (comps != null && comps.length >= 2) {
				for (int i = 0; i < 2; i++) {
					graphics.pose().pushPose();
					graphics.pose().translate(x + (i == 0 ? 60 : 102), y + 4 + count % 5 * 40, 0);
					graphics.pose().scale(1.25f, 1.25f, 1.25f);
					if (knowledge().isResearchKnown("!" + comps[i].getTag().toLowerCase())) {
						drawTag(graphics, 0, 0, comps[i], 0);
					} else {
						graphics.setColor(0.8f, 0.8f, 0.8f, 1f);
						graphics.blit(DUMMY_RESEARCH, 0, 0, 0, 0, 16, 16, 16, 16);
						graphics.setColor(1f, 1f, 1f, 1f);
					}
					graphics.pose().popPose();
					if (knowledge().isResearchKnown("!" + comps[i].getTag().toLowerCase())) {
						drawSmallCentered(graphics, comps[i].getName(), x + 22 + (i == 0 ? 50 : 92), y + 29 + count % 5 * 40, 5263440);
					}
				}
				graphics.drawString(font(), "=", x + 9 + 32, y + 12 + count % 5 * 40, 10066329, false);
				graphics.drawString(font(), "+", x + 10 + 79, y + 12 + count % 5 * 40, 10066329, false);
			} else {
				graphics.drawString(font(), Component.translatable("tc.aspect.primal"), x + 54, y + 12 + count % 5 * 40, 7829367, false);
			}
		}
		float bob = Mth.sin((mc().player != null ? mc().player.tickCount : 0) / 3.0f) * 0.2f + 0.1f;
		if (aspectsPage > 0) drawTexturedModalRectScaled(graphics, x - 20, y + 208, 0, 184, 12, 8, bob);
		if (aspectsPage < maxAspectPages - 1) drawTexturedModalRectScaled(graphics, x + 144, y + 208, 12, 184, 12, 8, bob);
	}

	/** Which aspect's icon in the aspect list is at (px, py), if any - the same rows {@link #drawAspectPage} draws. */
	private Aspect aspectAt(int x, int y, int px, int py) {
		if (knownPlayerAspects == null) return null;
		int count = -1;
		int start = aspectsPage * 5;
		for (Aspect aspect : knownPlayerAspects.getAspectsSortedByName()) {
			if (++count < start) continue;
			if (count > start + 4) break;
			int ty = y + count % 5 * 40;
			if (aspect.getImage() != null && px >= x && py >= ty && px < x + 40 && py < ty + 40) return aspect;
		}
		return null;
	}

	/** Every item you have scanned (research key "!" + its id, as ScanGeneric grants) whose aspects include this one,
	 * most of it first. */
	private void showAspectItems(Aspect aspect) {
		record Hit(ItemStack stack, int amount, String name) {}
		List<Hit> hits = new ArrayList<>();
		for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
			if (item == net.minecraft.world.item.Items.AIR) continue;
			if (!knowledge().isResearchKnown("!" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item))) continue;
			ItemStack stack = new ItemStack(item);
			AspectList aspects = thaumcraft.api.aspects.AspectHelper.getObjectAspects(stack);
			int amount = aspects == null ? 0 : aspects.getAmount(aspect);
			if (amount > 0) hits.add(new Hit(stack, amount, stack.getHoverName().getString()));
		}
		hits.sort(java.util.Comparator.comparingInt(Hit::amount).reversed().thenComparing(Hit::name));
		shownAspect = aspect;
		aspectItemsPage = 0;
		aspectItems.clear();
		aspectAmounts.clear();
		for (Hit hit : hits) {
			aspectItems.add(hit.stack());
			aspectAmounts.add(hit.amount());
		}
	}

	private int maxAspectItemPages() {
		return Math.max(1, Mth.ceil(aspectItems.size() / (float) (ASPECT_ITEM_COLS * ASPECT_ITEM_ROWS)));
	}

	private void drawAspectItems(GuiGraphics graphics, int x, int y, int mx, int my) {
		// header: the aspect (clicking it goes back to the list) and how many scanned things hold it
		if (mx >= x && my >= y && mx < x + 144 && my < y + 26) {
			tipText = List.of(Component.translatable("tc.aspect.back").getString());
		}
		graphics.pose().pushPose();
		graphics.pose().translate(x + 2, y + 2, 0);
		graphics.pose().scale(1.5f, 1.5f, 1.5f);
		drawTag(graphics, 0, 0, shownAspect, 0);
		graphics.pose().popPose();
		graphics.drawString(font(), shownAspect.getName(), x + 32, y + 4, 5263440, false);
		String held = Component.translatable("tc.aspect.scanned", aspectItems.size()).getString();
		graphics.pose().pushPose();
		graphics.pose().translate(x + 32, y + 15, 0);
		graphics.pose().scale(0.5f, 0.5f, 0.5f);
		graphics.drawString(font(), held, 0, 0, 7829367, false);
		graphics.pose().popPose();

		int gy = y + 30;
		if (aspectItems.isEmpty()) {
			graphics.drawWordWrap(font(), Component.translatable("tc.aspect.noitems"), x, gy + 4, 144, 7829367);
			return;
		}
		int per = ASPECT_ITEM_COLS * ASPECT_ITEM_ROWS;
		int start = aspectItemsPage * per;
		for (int i = start; i < Math.min(aspectItems.size(), start + per); i++) {
			int ix = x + (i - start) % ASPECT_ITEM_COLS * 18;
			int iy = gy + (i - start) / ASPECT_ITEM_COLS * 18;
			ItemStack stack = aspectItems.get(i);
			drawStackAt(graphics, stack, ix, iy, mx, my, false, false);
			// the aspect amount where a stack count goes
			graphics.renderItemDecorations(font(), stack, ix, iy, String.valueOf(aspectAmounts.get(i)));
		}
		float bob = Mth.sin((mc().player != null ? mc().player.tickCount : 0) / 3.0f) * 0.2f + 0.1f;
		if (aspectItemsPage > 0) drawTexturedModalRectScaled(graphics, x - 20, y + 208, 0, 184, 12, 8, bob);
		if (aspectItemsPage < maxAspectItemPages() - 1) drawTexturedModalRectScaled(graphics, x + 144, y + 208, 12, 184, 12, 8, bob);
	}

	private void drawSmallCentered(GuiGraphics graphics, String text, int x, int y, int color) {
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0);
		graphics.pose().scale(0.5f, 0.5f, 0.5f);
		graphics.drawString(font(), text, -font().width(text) / 2, 0, color, false);
		graphics.pose().popPose();
	}

	private void drawTexturedModalRectScaled(GuiGraphics graphics, int x, int y, int u, int v, int w, int h, float scale) {
		graphics.pose().pushPose();
		graphics.pose().translate(x + w / 2.0f, y + h / 2.0f, 0);
		graphics.pose().scale(1.0f + scale, 1.0f + scale, 1.0f);
		graphics.pose().translate(-w / 2.0f, -h / 2.0f, 0);
		RenderSystem.enableBlend();
		graphics.blit(TEX1, 0, 0, u, v, w, h);
		graphics.pose().popPose();
	}

	private void drawPopupAt(int x, int y, int w, int h, int mx, int my, String key) {
		drawPopupAtRaw(x, y, w, h, mx, my, Component.translatable(key).getString());
	}

	private void drawPopupAtRaw(int x, int y, int w, int h, int mx, int my, String text) {
		if ((shownRecipe == null || allowWithPagePopup) && mx >= x && my >= y && mx < x + w && my < y + h) tipText = List.of(text);
	}

	private static boolean mouseInside(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}

	private void drawStackAt(GuiGraphics graphics, ItemStack stack, int x, int y, int mx, int my, boolean clickthrough) {
		drawStackAt(graphics, stack, x, y, mx, my, clickthrough, true);
	}

	private void drawStackAt(GuiGraphics graphics, ItemStack stack, int x, int y, int mx, int my, boolean clickthrough, boolean overlay) {
		if (stack == null || stack.isEmpty()) return;
		graphics.renderItem(stack, x, y);
		if (overlay) graphics.renderItemDecorations(font(), stack, x, y);
		if ((shownRecipe == null || allowWithPagePopup) && mx >= x && my >= y && mx < x + 16 && my < y + 16) {
			List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(mc().level), mc().player,
					mc().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL));
			if (clickthrough) {
				String ref = getCraftingRecipeKey(stack);
				if (ref != null) {
					String[] sr = ref.split(";", 2);
					if (sr.length > 1) {
						lines.add(Component.translatable("recipe.clickthrough").withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC));
						reference.add(new Object[] {mx, my, ResourceLocation.parse(sr[0]), sr[1]});
					} else {
						lines.add(Component.translatable("recipe.unknown").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
					}
				}
			}
			tipText = lines;
		}
	}

	/** Which research recipe key (and page within it) makes this stack: "key;page", "UNKNOWN" if you haven't the research
	 * for it, or null if none does. Searched across every stage of every entry, as the original did. */
	private String getCraftingRecipeKey(ItemStack stack) {
		String cacheKey = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getComponentsPatch();
		if (keyCache.containsKey(cacheKey)) return keyCache.get(cacheKey);
		String result = null;
		search:
		for (ResearchCategory rcl : ResearchCategories.researchCategories.values()) {
			for (ResearchEntry ri : rcl.research.values()) {
				if (ri.getStages() == null) continue;
				for (ResearchStage stage : ri.getStages()) {
					if (stage.getRecipes() == null) continue;
					for (ResourceLocation rec : stage.getRecipes()) {
						int found = findRecipePage(rec, stack);
						if (found == -1) continue;
						result = found == -99 ? "UNKNOWN" : rec + ";" + found;
						break search;
					}
				}
			}
		}
		keyCache.put(cacheKey, result);
		return result;
	}

	private int findRecipePage(ResourceLocation rk, ItemStack stack) {
		if (mc().level == null) return -1;
		List<RecipeHolder<?>> holders = ResearchRecipeCatalog.resolve(mc().level.getRecipeManager(), rk);
		for (int g = 0; g < holders.size(); g++) {
			Recipe<?> recipe = holders.get(g).value();
			ItemStack out;
			try {
				out = recipe.getResultItem(mc().level.registryAccess());
			} catch (Exception e) {
				continue;
			}
			if (out.isEmpty() || !ItemStack.isSameItem(out, stack)) continue;
			String req = researchOf(recipe);
			if (req != null && !req.isEmpty() && !screen.knowsStrict(req.split(",")[0].trim())) return -99;
			return g;
		}
		return -1;
	}

	// ------------------------------------------------------------------------------------------------------------
	// Input
	// ------------------------------------------------------------------------------------------------------------

	boolean keyPressed(int keyCode, int scanCode) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE || mc().options.keyInventory.matches(keyCode, scanCode)) {
			history.clear();
			if (shownRecipe != null || showingAspects || showingKnowledge) {
				shownRecipe = null;
				showingAspects = false;
				shownAspect = null;
				showingKnowledge = false;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.4f, 1.1f);
			} else {
				screen.closePage();
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_PAGE_UP) {
			prevPage();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_RIGHT || keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
			nextPage();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (showingAspects && shownAspect != null) {
				shownAspect = null;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
				return true;
			}
			goBack();
			return true;
		}
		return false;
	}

	boolean mouseScrolled(double scrollY) {
		return true;
	}

	private void nextPage() {
		if (page < maxPages - 2) {
			page += 2;
			lastCycle = 0L;
			cycle = -1;
			screen.playSound(ThaumcraftSounds.PAGE.get(), 0.66f, 1.0f);
		}
	}

	private void prevPage() {
		if (page >= 2) {
			page -= 2;
			lastCycle = 0L;
			cycle = -1;
			screen.playSound(ThaumcraftSounds.PAGE.get(), 0.66f, 1.0f);
		}
	}

	private void goBack() {
		if (!history.isEmpty()) {
			screen.playSound(ThaumcraftSounds.PAGE.get(), 0.66f, 1.0f);
			shownRecipe = history.pop();
		} else {
			shownRecipe = null;
		}
	}

	boolean mouseClicked(double mouseX, double mouseY, int button) {
		int par1 = (int) mouseX;
		int par2 = (int) mouseY;
		checkRequisites();
		int var4 = (width() - PANE_W) / 2;
		int var5 = (height() - PANE_H) / 2;
		int mx = par1 - hrx;
		int my = par2 - hry;
		if (shownRecipe == null && !hold && hasAllRequisites && !isComplete && mx >= 0 && my >= 0 && mx < 64 && my < 12) {
			screen.sendAction(ThaumonomiconMenu.ACTION_COMPLETE_STAGE, research.getKey());
			screen.playSound(ThaumcraftSounds.WRITE.get(), 0.66f, 1.0f);
			lastCheck = 0L;
			lastStage = currentStage;
			hold = true;
			keyCache.clear();
			drilldownLists.clear();
		}
		if (knownPlayerAspects != null && knowledge().isResearchComplete("FIRSTSTEPS")) {
			mx = par1 - (var4 - 48);
			my = par2 - (var5 + 8);
			if (mx >= 0 && my >= 0 && mx < 25 && my < 16) {
				shownRecipe = null;
				showingKnowledge = false;
				showingAspects = !showingAspects;
				shownAspect = null;
				history.clear();
				if (aspectsPage > maxAspectPages) aspectsPage = 0;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			}
		}
		if (knowledge().isResearchComplete("KNOWLEDGETYPES") && !research.getKey().equals("KNOWLEDGETYPES")) {
			mx = par1 - (var4 - 48);
			my = par2 - (var5 + 31);
			if (mx >= 0 && my >= 0 && mx < 25 && my < 16) {
				shownRecipe = null;
				showingAspects = false;
				showingKnowledge = !showingKnowledge;
				history.clear();
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			}
		}
		if (showingAspects) {
			int ax = (width() - 256) / 2 + 60;
			int ay = (height() - 256) / 2 + 24;
			if (shownAspect == null) {
				Aspect clicked = aspectAt(ax, ay, par1, par2);
				if (clicked != null) {
					showAspectItems(clicked);
					screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
				}
			} else if (par1 >= ax && par2 >= ay && par1 < ax + 144 && par2 < ay + 26) {
				shownAspect = null;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			}
		}
		mx = par1 - (var4 + 205);
		my = par2 - (var5 + 192);
		if (mx >= 0 && my >= 0 && mx < 14 && my < 14) {
			if (showingAspects && shownAspect == null && aspectsPage < maxAspectPages - 1) {
				aspectsPage++;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			} else if (showingAspects && shownAspect != null && aspectItemsPage < maxAspectItemPages() - 1) {
				aspectItemsPage++;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			}
		}
		mx = par1 - (var4 + 38);
		my = par2 - (var5 + 192);
		if (mx >= 0 && my >= 0 && mx < 14 && my < 14) {
			if (showingAspects && shownAspect == null && aspectsPage > 0) {
				aspectsPage--;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			} else if (showingAspects && shownAspect != null && aspectItemsPage > 0) {
				aspectItemsPage--;
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
			}
		}
		if (!recipeLists.isEmpty()) {
			int aa = 0;
			int space = Math.min(25, 200 / recipeLists.size());
			for (ResourceLocation rk : recipeLists.keySet()) {
				mx = par1 - (var4 + 280);
				my = par2 - (var5 - 8 + aa * space);
				if (mx >= 0 && my >= 0 && mx < 30 && my < 16) {
					shownRecipe = rk.equals(shownRecipe) ? null : rk;
					showingAspects = false;
					showingKnowledge = false;
					history.clear();
					screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
					break;
				}
				aa++;
			}
		}
		mx = par1 - (var4 + 205);
		my = par2 - (var5 + 192);
		if (hasRecipePages && recipePage < recipePageMax && mx >= 0 && my >= 0 && mx < 14 && my < 14) {
			recipePage++;
			screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
		}
		mx = par1 - (var4 + 38);
		my = par2 - (var5 + 192);
		if (hasRecipePages && recipePage > 0 && mx >= 0 && my >= 0 && mx < 14 && my < 14) {
			recipePage--;
			screen.playSound(ThaumcraftSounds.PAGE.get(), 0.7f, 0.9f);
		}
		mx = par1 - (var4 + 261);
		my = par2 - (var5 + 189);
		if (shownRecipe == null && mx >= 0 && my >= 0 && mx < 14 && my < 10) nextPage();
		mx = par1 - (var4 - 17);
		my = par2 - (var5 + 189);
		if (shownRecipe == null && mx >= 0 && my >= 0 && mx < 14 && my < 10) prevPage();
		mx = par1 - (var4 + 118);
		my = par2 - (var5 + 190);
		if (mx >= 0 && my >= 0 && mx < 20 && my < 12) goBack();
		mx = par1 - (var4 + 210);
		my = par2 - (var5 + 190);
		if (renderingCompound && mx >= 0 && my >= 0 && mx < 10 && my < 10) {
			screen.playSound(ThaumcraftSounds.CLACK.get(), 0.66f, 1.0f);
			cycleMultiblockLines = !cycleMultiblockLines;
		}
		for (Object[] coords : reference) {
			int rx = (int) coords[0];
			int ry = (int) coords[1];
			if (par1 >= rx && par2 >= ry && par1 < rx + 16 && par2 < ry + 16) {
				screen.playSound(ThaumcraftSounds.PAGE.get(), 0.66f, 1.0f);
				if (shownRecipe != null) history.push(shownRecipe);
				shownRecipe = (ResourceLocation) coords[2];
				recipePage = Integer.parseInt((String) coords[3]);
				if (!drilldownLists.containsKey(shownRecipe)) {
					addRecipesToList(shownRecipe, drilldownLists, new LinkedHashMap<>());
				}
				break;
			}
		}
		return true;
	}
}
