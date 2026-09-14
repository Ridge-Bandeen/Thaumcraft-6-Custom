package thaumcraft.common.lib.research;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ScanningManager;

/**
 * Replaces the old (1.12.2) {@code ScanGeneric} - the true original's own general-purpose catch-all scan
 * handler, and the real heart of "scan things with a Thaumometer to make research progress". Anything with
 * real aspects at all - any entity via {@link AspectHelper#getEntityAspects}, any block or item via
 * {@link AspectHelper#getObjectAspects} - counts, and scanning one for the first time grants real
 * {@link IPlayerKnowledge.EnumKnowledgeType#OBSERVATION} progress toward <b>every</b> real research
 * category, each weighted by that category's own real {@link ResearchCategory#applyFormula} against the
 * thing's own real aspects.
 *
 * <p>Before this, every real scan target in this port was a hand-registered one-off ({@code ScanBlock} for
 * the Runic Matrix, two {@code ScanEntity} examples, {@link ScanSky}) - the thaumometer only recognised a
 * tiny hardcoded set of things and granted no category progress at all. This is the real, general mechanic
 * the original actually shipped.
 *
 * <p>Real modern substitutions, both faithful in effect: the original's own
 * {@code EntityList.getEntityString} research key is the entity type's own real registry id here, and its
 * own {@code registryName + itemDamage} item key is just the item's own real registry id (modern items have
 * no damage-value variants at all - this port's own long-standing "split every metadata variant into its
 * own real item" convention means the id alone is already unique). Item resolution goes through
 * {@link ScanningManager#getItemFromParms}, restored alongside this class.
 */
public class ScanGeneric implements IScanThing {

	@Override
	public boolean checkThing(Player player, Object obj) {
		AspectList aspects = getAspects(player, obj);
		return aspects != null && aspects.size() > 0;
	}

	@Override
	public void onSuccess(Player player, Object obj) {
		AspectList aspects = getAspects(player, obj);
		if (aspects == null) return;
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION,
					category, category.applyFormula(aspects));
		}
	}

	@Override
	public String getResearchKey(Player player, Object obj) {
		if (obj instanceof Entity entity && !(obj instanceof ItemEntity)) {
			return "!" + BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
		}
		ItemStack stack = ScanningManager.getItemFromParms(player, obj);
		if (!stack.isEmpty()) {
			return "!" + BuiltInRegistries.ITEM.getKey(stack.getItem());
		}
		return null;
	}

	/** A real dropped item on the ground is scanned as the item it holds, not as an entity - matching the
	 * original's own {@code !(obj instanceof EntityItem)} split exactly. */
	private AspectList getAspects(Player player, Object obj) {
		if (obj == null) return null;
		if (obj instanceof Entity entity && !(obj instanceof ItemEntity)) {
			return AspectHelper.getEntityAspects(entity);
		}
		ItemStack stack = ScanningManager.getItemFromParms(player, obj);
		return stack.isEmpty() ? null : AspectHelper.getObjectAspects(stack);
	}
}
