package thaumcraft.common.lib.events;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.research.ResearchRequirements;

/**
 * The real crafting half of the true original's own research auto-progression - the exact hook
 * {@code ThaumcraftResearchLoader}'s own doc comment named as missing when it chose to skip parsing
 * {@code required_craft} at all. Replaces the one real fragment of the old (1.12.2) {@code CraftingEvents}
 * that this concerns, following the same "extract the one real mechanic, not the whole stale file" precedent
 * as {@link PassiveResearchEvents} and {@code ChampionModEvents}.
 *
 * <p>Real behaviour, matching the original: whenever a player really crafts something, every registered
 * {@code required_craft} reference it matches grants that reference's real virtual {@code "[#]"} research
 * key, so the knowledge is recorded the moment the item is made rather than being re-derived later. All the
 * matching and granting lives in {@link ResearchRequirements} - this class is only the real event plumbing.
 *
 * <p>Fires for anything that posts the crafted event: crafting tables, the Arcane Workbench's result slot, and the
 * Salis Mundus conversions (workbench, research table, multiblocks) - as in the original.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ResearchCraftingEvents {

	@SubscribeEvent
	private static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
		if (event.getEntity() instanceof ServerPlayer player) {
			ResearchRequirements.onItemCrafted(player, event.getCrafting());
		}
	}
}
