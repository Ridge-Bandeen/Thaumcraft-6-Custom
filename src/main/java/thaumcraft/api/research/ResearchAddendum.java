package thaumcraft.api.research;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;


public class ResearchAddendum {
	String text;
	ResourceLocation[] recipes;
	String[] research;

	/**
	 * @return the text
	 */
	public String getText() {
		return text;
	}

	/** See {@link thaumcraft.api.aspects.Aspect#getLocalizedDescription()} for why this returns a Component now. */
	public Component getTextLocalized() {
		return Component.translatable(getText());
	}
	/**
	 * @param text the text to set
	 */
	public void setText(String text) {
		this.text = text;
	}

	/**
	 * @return the recipes
	 */
	public ResourceLocation[] getRecipes() {
		return recipes;
	}
	/**
	 * @param recipes the recipes to set
	 */
	public void setRecipes(ResourceLocation[] recipes) {
		this.recipes = recipes;
	}

	/**
	 * @return the research
	 */
	public String[] getResearch() {
		return research;
	}
	/**
	 * @param research the research to set
	 */
	public void setResearch(String[] research) {
		this.research = research;
	}

}
