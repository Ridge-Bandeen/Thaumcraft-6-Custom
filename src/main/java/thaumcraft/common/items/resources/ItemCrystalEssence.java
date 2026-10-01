package thaumcraft.common.items.resources;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code ItemCrystalEssence} - a thin subclass of the already-shipped
 * {@link ItemGenericEssentiaContainer} (which {@code ItemsTC.crystalEssence} used directly, unnamed, until
 * now) adding only the original's one real piece of extra behaviour: a display name naming the aspect it
 * holds ("Crystal Essence (Fire)") instead of the plain generic name every crystal essence stack showed
 * before. The base class's own random-aspect-on-craft/tick behavior and creative-tab variant listing already
 * covered everything else the original class did.
 *
 * {@code getLocalizedDescription()} on {@link Aspect} needed real {@code tc.aspect.*} lang keys to resolve
 * against - those existed only in the old (dead - 1.21.1 never loads {@code .lang} files) {@code en_us.lang},
 * so the 37 real aspect names + 3 category headers were converted into {@code en_us.json} alongside this
 * class. The rest of {@code en_us.lang} still needs the same conversion (a separate, much larger task).
 */
public class ItemCrystalEssence extends ItemGenericEssentiaContainer {

	public ItemCrystalEssence(Properties properties) {
		super(properties, 1);
	}

	/** Real modern equivalent of the original's own {@code ThaumcraftApiHelper#makeCrystal} - that whole
	 * class is still stale/unported, so this is a small, real, purpose-built replacement rather than porting
	 * it wholesale for one method. A real Crystal Essence stack pre-set to the given aspect, matching
	 * {@link ItemGenericEssentiaContainer#setAspects}'s own real base-amount-1 convention. */
	public static ItemStack of(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	@Override
	public Component getName(ItemStack stack) {
		AspectList aspects = getAspects(stack);
		if (aspects != null && aspects.size() > 0) {
			// as the original: "Ignis Vis Crystal" - the aspect's own (untranslated) name, not its description
			return Component.translatable("item.thaumcraft.crystal_essence.named", aspects.getAspects()[0].getName());
		}
		return super.getName(stack);
	}
}
