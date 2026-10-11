package thaumcraft.common.lib.research;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.IScanThing;

/**
 * Replaces the old (1.12.2) {@code ScanSky} - the real producer behind {@link
 * thaumcraft.common.items.curios.ItemCelestialNotes}: while holding real scribing tools and carrying real
 * paper, look at the real sun/moon (within roughly 10 degrees of yaw and 7 of pitch) or, at night, in one of
 * the four real cardinal directions (a "star quadrant") and use the thaumometer's own "scan nothing" action
 * (see {@link thaumcraft.common.items.tools.ItemThaumometer#use} - restored specifically for this, the true
 * original's own real trigger for this mechanic) to consume the paper and receive a real note matching
 * whatever's actually in the sky right now, gated by the real {@code CELESTIALSCANNING} research (already
 * present in this port's own converted research data) and a real once-per-real-in-game-day dedup per target.
 *
 * <p>Real modern substitutions, faithfully matching the original's own real values: {@code
 * world.provider.getMoonPhase(...)} is {@link Level#dimensionType()}'s own real {@code moonPhase(long)};
 * {@code world.getCelestialAngle(0.0f)} is {@link Level#getTimeOfDay(float)} (confirmed the same real 0-1
 * fraction via {@link Level#getSunAngle} - {@code getSunAngle == getTimeOfDay * 2*PI}, matching the
 * original's own {@code getCelestialAngle*2*PI} javadoc exactly); {@code EntityPlayer#getAdjustedHorizontalFacing}
 * is real vanilla {@link Player#getDirection()} (same real ordinal layout for the 4 horizontal directions,
 * so the original's own {@code face.getIndex() - 2} trick still works unchanged via {@link
 * Direction#get3DDataValue()}); the stale, unported {@code InventoryUtils.isPlayerCarryingAmount}/{@code
 * consumePlayerItem} are real vanilla {@link net.minecraft.world.entity.player.Inventory#clearOrCountMatchingItems}
 * (0 = count only, a real, exact, already-established pattern elsewhere in this port -
 * see {@code ResearchTableMenu}'s own real item-consumption code).
 */
public class ScanSky implements IScanThing {

	@Override
	public boolean checkThing(Player player, Object obj) {
		if (obj != null || !isRealLookingAtOpenSky(player)) return false;
		// The original only matched when you were aimed at the sun / moon, or at any time at night (a star quadrant) - not for any
		// glance at a daytime sky, which had this returning true and doing nothing.
		Level level = player.level();
		int yaw = (int) (player.getYRot() + 90.0f) % 360;
		int pitch = (int) Math.abs(player.getXRot());
		int celestialAngle = (int) ((level.getTimeOfDay(0.0f) + 0.25f) * 360.0f) % 360;
		boolean night = celestialAngle > 180;
		if (night) celestialAngle -= 180;
		boolean inRangeYaw;
		boolean inRangePitch;
		if (celestialAngle > 90) {
			inRangeYaw = Math.abs(Math.abs(yaw) - 180) < 10;
			inRangePitch = Math.abs(180 - celestialAngle - pitch) < 7;
		} else {
			inRangeYaw = Math.abs(yaw) < 10;
			inRangePitch = Math.abs(celestialAngle - pitch) < 7;
		}
		return (inRangeYaw && inRangePitch) || night;
	}

	@Override
	public String getResearchKey(Player player, Object object) {
		// This device does its own real research bookkeeping in onSuccess (a per-real-day dedup key, not a
		// single fixed unlock) - matches the true original's own real empty-string opt-out of
		// ScanningManager's generic single-key dedup.
		return "";
	}

	@Override
	public void onSuccess(Player player, Object object) {
		if (object != null || !isRealLookingAtOpenSky(player)) return;
		Level level = player.level();

		int yaw = (int) (player.getYRot() + 90.0f) % 360;
		int pitch = (int) Math.abs(player.getXRot());
		int celestialAngle = (int) ((level.getTimeOfDay(0.0f) + 0.25f) * 360.0f) % 360;
		boolean night = celestialAngle > 180;
		if (night) celestialAngle -= 180;

		boolean inRangeYaw;
		boolean inRangePitch;
		if (celestialAngle > 90) {
			inRangeYaw = Math.abs(Math.abs(yaw) - 180) < 10;
			inRangePitch = Math.abs(180 - celestialAngle - pitch) < 7;
		} else {
			inRangeYaw = Math.abs(yaw) < 10;
			inRangePitch = Math.abs(celestialAngle - pitch) < 7;
		}

		// as the original: days by total world time (sleeping or /time do not start a new day of scans)
		long worldDay = level.getGameTime() / 24000L;
		String prefix = "CEL_" + worldDay + "_";

		if (inRangeYaw && inRangePitch) {
			int moonPhase = level.dimensionType().moonPhase(level.getDayTime());
			String key = prefix + (night ? ("Moon" + moonPhase) : "Sun");
			grantNote(player, key, prefix, night ? ItemsTC.celestialNotes.get(5 + moonPhase) : ItemsTC.celestialNotes.get(0));
		} else if (night) {
			Direction face = player.getDirection();
			int quadrant = face.get3DDataValue() - 2; // NORTH=0, SOUTH=1, WEST=2, EAST=3 - matches the
			// original's own real EnumFacing.getIndex()-2 trick (same ordinal layout for horizontal faces).
			if (quadrant < 0 || quadrant > 3) return; // face wasn't actually horizontal - shouldn't happen
			String key = prefix + "Star" + quadrant;
			grantNote(player, key, prefix, ItemsTC.celestialNotes.get(1 + quadrant));
		}
	}

	private boolean isRealLookingAtOpenSky(Player player) {
		return player.getXRot() <= 0.0f
				&& player.level().canSeeSky(player.blockPosition().above())
				&& player.level().dimension() == Level.OVERWORLD
				&& ThaumcraftCapabilities.knowsResearchStrict(player, "CELESTIALSCANNING");
	}

	private void grantNote(Player player, String key, String prefix, net.neoforged.neoforge.registries.DeferredItem<thaumcraft.common.items.curios.ItemCelestialNotes> note) {
		if (ThaumcraftCapabilities.getKnowledge(player).isResearchKnown(key)) {
			player.displayClientMessage(Component.translatable("tc.celestial.fail.1"), true);
			cleanOldStudySessions(player, prefix);
			return;
		}
		if (isCarryingScribingTools(player) && consumeOnePaper(player)) {
			ItemStack stack = new ItemStack(note.get());
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			ThaumcraftApi.internalMethods.progressResearch(player, key);
		} else {
			player.displayClientMessage(Component.translatable("tc.celestial.fail.2"), true);
		}
		cleanOldStudySessions(player, prefix);
	}

	private boolean isCarryingScribingTools(Player player) {
		Predicate<ItemStack> matches = s -> s.is(ItemsTC.scribingTools.get());
		return player.getInventory().clearOrCountMatchingItems(matches, 0, player.getInventory()) > 0;
	}

	private boolean consumeOnePaper(Player player) {
		Predicate<ItemStack> matches = s -> s.is(Items.PAPER);
		return player.getInventory().clearOrCountMatchingItems(matches, 1, player.getInventory()) > 0;
	}

	/** Clears out any stale "already studied today" research keys from an earlier real in-game day, matching
	 * the original's own real {@code cleanResearch} - keeps the per-day dedup from permanently accumulating. */
	private void cleanOldStudySessions(Player player, String currentPrefix) {
		List<String> stale = new ArrayList<>();
		for (String key : ThaumcraftCapabilities.getKnowledge(player).getResearchList()) {
			if (key.startsWith("CEL_") && !key.startsWith(currentPrefix)) {
				stale.add(key);
			}
		}
		for (String key : stale) {
			ThaumcraftCapabilities.getKnowledge(player).removeResearch(key);
		}
	}
}
