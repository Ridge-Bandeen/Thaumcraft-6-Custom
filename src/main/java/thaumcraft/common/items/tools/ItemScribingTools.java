package thaumcraft.common.items.tools;

import net.minecraft.world.item.Item;
import thaumcraft.api.items.IScribeTools;

/**
 * Replaces the old (1.12.2) {@code ItemScribingTools} - a plain damageable "ink" item consumed one point at
 * a time by {@link thaumcraft.common.tiles.crafting.TileResearchTable} each time a card is picked (see that
 * class's own doc comment). No behaviour of its own beyond {@link IScribeTools} marking it as a valid
 * research-table ink slot item and the durability the table drains.
 */
public class ItemScribingTools extends Item implements IScribeTools {

	public ItemScribingTools(Properties properties) {
		super(properties.stacksTo(1).durability(100));
	}
}
