package thaumcraft.api.research;
import java.util.Collection;
import java.util.LinkedHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.aspects.AspectList;



public class ResearchCategories {

	//Research
	public static LinkedHashMap <String, ResearchCategory> researchCategories = new LinkedHashMap <String,ResearchCategory>();

	/**
	 * @param key
	 * @return the research item linked to this key
	 */
	public static ResearchCategory getResearchCategory(String key) {
		return researchCategories.get(key);
	}

	/**
	 * @param key
	 * @return the name of the research category linked to this key.
	 * See {@link thaumcraft.api.aspects.Aspect#getLocalizedDescription()} for why this returns a Component now.
	 */
	public static Component getCategoryName(String key) {
		return Component.translatable("tc.research_category."+key);
	}

	/**
	 * @param key the research key
	 * @return the ResearchItem object.
	 */
	public static ResearchEntry getResearch(String key) {
		Collection<ResearchCategory> rc = researchCategories.values();
		for (ResearchCategory cat:rc) {
			Collection<ResearchEntry> rl = cat.research.values();
			for (ResearchEntry ri:rl) {
				if (ri.getKey().equals(key)) return ri;
			}
		}
		return null;
	}

	/**
	 * This should only be done at the PostInit stage
	 * @param key the key used for this category
	 * @param researchkey the research that the player needs to have completed before this category becomes visible. Set as null to always show.
	 * @param aspectsFormula aspects required to gain knowledge in this category
	 * @param icon the icon to be used for the research category tab
	 * @param background the resource location of the background image to use for this category
	 * @return the registered category
	 */
	public static ResearchCategory registerCategory(String key, String researchkey, AspectList formula, ResourceLocation icon, ResourceLocation background) {
		if (getResearchCategory(key)==null) {
			ResearchCategory rl = new ResearchCategory(key,researchkey, formula, icon, background);
			researchCategories.put(key, rl);
			return rl;
		}
		return null;
	}

	/**
	 * This should only be done at the PostInit stage
	 * @param key the key used for this category
	 * @param researchkey the research that the player needs to have completed before this category becomes visible. Set as null to always show.
	 * @param icon the icon to be used for the research category tab
	 * @param background the resource location of the background image to use for this category
	 * @param background2 the resource location of the foreground image that lies between the background and icons
	 * @return the registered category
	 */
	public static ResearchCategory registerCategory(String key, String researchkey, AspectList formula, ResourceLocation icon, ResourceLocation background, ResourceLocation background2) {
		if (getResearchCategory(key)==null) {
			ResearchCategory rl = new ResearchCategory(key, researchkey, formula, icon, background, background2);
			researchCategories.put(key, rl);
			return rl;
		}
		return null;
	}



}
