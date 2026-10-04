package thaumcraft.api.research;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
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
 * Real inventory-scanning recursion and real {@link #getItemFromParms} block-to-item resolution are both
 * ported now (they were the two bullets this class's own doc comment previously listed as cut). The first
 * was originally cut for needing "an unported {@code ThaumcraftInvHelper}/{@code IItemHandler} helper" -
 * NeoForge's own {@link Capabilities.ItemHandler#BLOCK} covers that directly, and is already this port's
 * own established way to reach an adjacent inventory (see {@code EntityArcaneBore}/{@code SealEmpty}).
 * Together they're what makes real item-shaped scan targets ({@link thaumcraft.common.lib.research.ScanGeneric},
 * {@code ScanEnchantment}, {@code ScanPotion}) actually reachable at all - nothing in this port ever passed
 * a real {@code ItemStack} to a scan handler before.
 *
 * The action-bar feedback is the original's three-way branch again (2026-09-25 - a playtest found it said "You have
 * learned something new" on every scan): a match with an empty research key (the generic aspect scans) suppresses the
 * message entirely, a match that really progressed research says "learned something new" in green italics, and no
 * match - including research already known - says "nothing new can be learned" in purple italics.
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
		boolean suppress = false;
		for (IScanThing thing : things) {
			if (!thing.checkThing(player, object)) continue;
			String key = thing.getResearchKey(player, object);
			if (key == null || key.isEmpty() || ThaumcraftApi.internalMethods.progressResearch(player, key)) {
				if (key == null || key.isEmpty()) suppress = true;
				found = true;
				thing.onSuccess(player, object);
			}
		}
		if (!suppress) {
			player.displayClientMessage(Component.translatable(found ? "tc.knownobject" : "tc.unknownobject")
					.withStyle(found ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
		}

		// Real inventory recursion, matching the original exactly: scanning a real container also scans
		// everything really inside it, capped at 100 real items so a huge modded inventory can't lag the
		// server. Each recursive call sends its own real status line, same as the original - they land on
		// the action bar, so the last one simply wins.
		if (object instanceof BlockPos pos) {
			IItemHandler handler = player.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
			if (handler != null) {
				int scanned = 0;
				for (int slot = 0; slot < handler.getSlots(); slot++) {
					ItemStack stack = handler.getStackInSlot(slot);
					if (!stack.isEmpty()) {
						scanTheThing(player, stack);
						scanned++;
					}
					if (scanned >= 100) {
						player.displayClientMessage(Component.translatable("tc.invtoolarge")
								.withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
						break;
					}
				}
			}
		}
		return found;
	}

	/**
	 * Resolves whatever a scan target really is into the real {@link ItemStack} that represents it, for the
	 * item-shaped scan handlers ({@link thaumcraft.common.lib.research.ScanGeneric} and friends). Real port
	 * of the original's own {@code getItemFromParms}, including its own real water/lava bucket fallbacks
	 * (neither fluid has a real item form of its own to resolve to).
	 *
	 * @return the real stack, or {@link ItemStack#EMPTY} if this object has no real item form.
	 */
	public static ItemStack getItemFromParms(Player player, Object obj) {
		if (obj instanceof ItemStack stack) return stack;
		if (obj instanceof ItemEntity item) return item.getItem();
		if (obj instanceof BlockPos pos) {
			Level level = player.level();
			BlockState state = level.getBlockState(pos);
			ItemStack stack = ItemStack.EMPTY;
			try {
				BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
				stack = state.getCloneItemStack(hit, level, pos, player);
			}
			catch (Exception ignored) {
				// Real, deliberate catch, matching the original's own: a block's own pick-block logic is
				// third-party code that can genuinely throw on an unexpected state, and a failed scan
				// should never take the interaction down with it.
			}
			if (stack.isEmpty()) stack = new ItemStack(state.getBlock());
			if (stack.isEmpty() && state.getFluidState().is(Fluids.WATER)) stack = new ItemStack(Items.WATER_BUCKET);
			if (stack.isEmpty() && state.getFluidState().is(Fluids.LAVA)) stack = new ItemStack(Items.LAVA_BUCKET);
			return stack;
		}
		return ItemStack.EMPTY;
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
