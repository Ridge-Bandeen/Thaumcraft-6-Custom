package thaumcraft.api.research;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

/**
 * Replaces the old (1.12.2) {@code ScanningManager} - the registry and dispatch behind "scan this object to
 * learn something," first ported for {@link thaumcraft.common.items.tools.ItemThaumometer}. Grants research
 * through the already-real, already-load-bearing {@link thaumcraft.common.lib.research.PlayerKnowledge}
 * (via {@code ThaumcraftApi.internalMethods.progressResearch}, the same path
 * {@link thaumcraft.common.tiles.crafting.TileInfusionMatrix} already checks against for its own
 * research-gated recipes) - that class's own doc comment explicitly named this exact gap ("no way yet to
 * grant research in game") as the reason every new player starts knowing a hardcoded {@code FIRSTSTEPS}.
 *
 * Simplified vs the original, a deliberately "headless" slice (no research-tree browser/book UI, no
 * Research Table/Theorycraft minigame, no multi-stage {@code ResearchEntry} JSON data loaded) - see the
 * port-tracking memory notes for the full scoping rationale:
 * <ul>
 * <li>No inventory-scanning recursion (scanning a container's contents by pointing at it) - needed an
 * unported {@code ThaumcraftInvHelper}/{@code IItemHandler} helper for no real payoff at this stage.</li>
 * <li>No {@code getItemFromParms}/block-to-pickblock resolution - {@link ScanBlock} matches directly on the
 * block in the world instead.</li>
 * <li>Chat feedback simplified to a single status line per scan attempt instead of the original's
 * suppress/found/unknown three-way branching.</li>
 * </ul>
 */
public class ScanningManager {

	private static final List<IScanThing> things = new ArrayList<>();

	/**
	 * Add things to scan.
	 * @example {@code ScanningManager.addScannableThing(new ScanItem("HIPSTER", new ItemStack(Items.APPLE)));}
	 * This will unlock the <b>HIPSTER</b> research if you scan any kind of apple.
	 */
	public static void addScannableThing(IScanThing obj) {
		things.add(obj);
	}

	/**
	 * @param player
	 * @param object this could in theory be anything, but vanilla tc scanning tools only pass in an Entity,
	 * a BlockPos, or an ItemStack.
	 * @return true if anything matched (whether or not it granted new research - already-known research
	 * still counts as a match, matching the original).
	 */
	public static boolean scanTheThing(Player player, Object object) {
		boolean found = false;
		for (IScanThing thing : things) {
			if (!thing.checkThing(player, object)) continue;
			String key = thing.getResearchKey(player, object);
			if (key == null || key.isEmpty() || ThaumcraftApi.internalMethods.progressResearch(player, key)) {
				found = true;
				thing.onSuccess(player, object);
			}
		}
		player.displayClientMessage(Component.translatable(found ? "tc.knownobject" : "tc.unknownobject"), true);
		return found;
	}

	/**
	 * @return true if the object can be scanned for research the player has not yet discovered.
	 */
	public static boolean isThingStillScannable(Player player, Object object) {
		for (IScanThing thing : things) {
			if (!thing.checkThing(player, object)) continue;
			String key = thing.getResearchKey(player, object);
			if (key != null && !key.isEmpty() && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(key)) {
				return true;
			}
		}
		return false;
	}
}
