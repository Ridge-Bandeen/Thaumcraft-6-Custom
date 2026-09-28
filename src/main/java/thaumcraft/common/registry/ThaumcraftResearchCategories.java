package thaumcraft.common.registry;

import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;

/**
 * Registers the 7 top-level research categories - the authentic original values from
 * {@code thaumcraft.common.config.ConfigResearch} (key, unlock-research key, knowledge-gain aspect formula,
 * tab icon, GUI background). {@code ResearchCategories} itself (api.research) has been a complete, working
 * registry since early in this port; what was missing was ever actually calling
 * {@code registerCategory(...)} for real content, which needed nothing more than this - no dependency on
 * the still-unbuilt Thaumonomicon UI or research-entry/page system (that's {@code ResearchEntry}, a
 * per-category child list, left empty here).
 *
 * Only the category *metadata* is registered here - the research entries themselves are loaded separately,
 * for real, by {@link thaumcraft.common.lib.research.ThaumcraftResearchLoader} into each category's own
 * {@code research} map (each {@code registerCategory} call below creates the empty map the loader populates).
 * This is also enough on its own for {@link thaumcraft.common.items.curios.ItemCurio} (and anything else that
 * calls {@code IInternalMethodHandler.addKnowledge} against a category by key) to have real categories to
 * grant knowledge into, rather than a null lookup.
 */
public final class ThaumcraftResearchCategories {

	public static void register() {
		ResearchCategories.registerCategory("BASICS", null,
				new AspectList().add(Aspect.PLANT, 5).add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5).add(Aspect.AIR, 5).add(Aspect.FIRE, 5).add(Aspect.EARTH, 3).add(Aspect.WATER, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/items/thaumonomicon_cheat.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_1.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("AUROMANCY", "UNLOCKAUROMANCY",
				new AspectList().add(Aspect.AURA, 20).add(Aspect.MAGIC, 20).add(Aspect.FLUX, 15).add(Aspect.CRYSTAL, 5).add(Aspect.COLD, 5).add(Aspect.AIR, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_auromancy.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_2.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("ALCHEMY", "UNLOCKALCHEMY",
				new AspectList().add(Aspect.ALCHEMY, 30).add(Aspect.FLUX, 10).add(Aspect.MAGIC, 10).add(Aspect.LIFE, 5).add(Aspect.AVERSION, 5).add(Aspect.DESIRE, 5).add(Aspect.WATER, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_alchemy.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_3.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("ARTIFICE", "UNLOCKARTIFICE",
				new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.CRAFT, 10).add(Aspect.METAL, 10).add(Aspect.TOOL, 10).add(Aspect.ENERGY, 10).add(Aspect.LIGHT, 5).add(Aspect.FLIGHT, 5).add(Aspect.TRAP, 5).add(Aspect.FIRE, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_artifice.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_4.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("INFUSION", "UNLOCKINFUSION",
				new AspectList().add(Aspect.MAGIC, 30).add(Aspect.PROTECT, 10).add(Aspect.TOOL, 10).add(Aspect.FLUX, 5).add(Aspect.CRAFT, 5).add(Aspect.SOUL, 5).add(Aspect.EARTH, 3),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_infusion.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_7.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("GOLEMANCY", "UNLOCKGOLEMANCY",
				new AspectList().add(Aspect.MAN, 20).add(Aspect.MOTION, 10).add(Aspect.MIND, 10).add(Aspect.MECHANISM, 10).add(Aspect.EXCHANGE, 5).add(Aspect.SENSES, 5).add(Aspect.BEAST, 5).add(Aspect.ORDER, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_golemancy.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_5.png"),
				BACK_OVER);
		ResearchCategories.registerCategory("ELDRITCH", "UNLOCKELDRITCH",
				new AspectList().add(Aspect.ELDRITCH, 20).add(Aspect.DARKNESS, 10).add(Aspect.MAGIC, 5).add(Aspect.MIND, 5).add(Aspect.VOID, 5).add(Aspect.DEATH, 5).add(Aspect.UNDEAD, 5).add(Aspect.ENTROPY, 5),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/research/cat_eldritch.png"),
				ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_6.png"),
				BACK_OVER);
	}

	private static final ResourceLocation BACK_OVER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/gui/gui_research_back_over.png");

	private ThaumcraftResearchCategories() {
	}
}
