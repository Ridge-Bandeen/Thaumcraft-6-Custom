package thaumcraft.common.golems;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import thaumcraft.api.golems.IGolemAPI;

/**
 * Replaces the old (1.12.2) {@code GolemInteractionHelper} - simulates a golem "using" or "breaking" a block
 * by puppeting a real {@link FakePlayer} through it (same real approach the original used, just onto modern
 * {@link net.minecraft.server.level.ServerPlayerGameMode} entry points): {@link
 * net.minecraft.server.level.ServerPlayerGameMode#useItemOn} for a right-click (matches the original's own
 * {@code processRightClickBlock}), {@link net.minecraft.server.level.ServerPlayerGameMode#destroyBlock} for
 * a left-click - the same "one real action, no progressive-mining simulation" simplification {@link
 * thaumcraft.common.golems.seals.SealBreaker} already established for its own block breaking, reused here
 * for the same reason (still real loot-table drops, tool/hardness rules, and XP via the fake player's own
 * game mode - just not spread across multiple ticks).
 *
 * Real, documented cuts: the original's own pre-placement collision nudge (moving the golem out of the way
 * if it's itself standing where a block would be placed) isn't ported - a golem blocking its own placement
 * simply fails the click, same outcome a real player bumping into their own placement gets. Left-over items
 * are collected via a single loop over {@link Inventory#getContainerSize()} (main array indices 0-35 +
 * armor 36-39 + offhand 40, all through one {@code Container}-interface indexing scheme modern
 * {@link Inventory} already provides) rather than the original's separate main/armor loops - a real
 * simplification of iteration, not of behavior.
 */
public final class GolemInteractionHelper {

	// A real, non-null, stable UUID - unlike the original's own "null UUID" GameProfile (1.12.2's
	// GameProfile tolerated a null id; modern GameProfile requires one), derived the same way vanilla itself
	// derives offline-mode player UUIDs so it stays deterministic across calls.
	private static final GameProfile PROFILE = new GameProfile(
			UUID.nameUUIDFromBytes("OfflinePlayer:FakeThaumcraftGolem".getBytes(StandardCharsets.UTF_8)), "FakeThaumcraftGolem");

	private GolemInteractionHelper() {
	}

	public static void golemClick(Level world, IGolemAPI golem, BlockPos pos, Direction face, ItemStack clickStack, boolean sneaking, boolean rightClick) {
		if (!(world instanceof ServerLevel serverLevel)) return;
		FakePlayer fp = FakePlayerFactory.get(serverLevel, PROFILE);
		var golemEntity = golem.getGolemEntity();
		fp.moveTo(golemEntity.getX(), golemEntity.getY(), golemEntity.getZ(), golemEntity.getYRot(), golemEntity.getXRot());
		fp.setItemInHand(InteractionHand.MAIN_HAND, clickStack);
		fp.setShiftKeyDown(sneaking);

		if (!rightClick) {
			fp.gameMode.destroyBlock(pos);
		} else {
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), face, pos, false);
			fp.gameMode.useItemOn(fp, serverLevel, fp.getItemInHand(InteractionHand.MAIN_HAND), InteractionHand.MAIN_HAND, hit);
		}

		golem.addRankXp(1);
		if (fp.getItemInHand(InteractionHand.MAIN_HAND).getCount() <= 0) {
			fp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
		collectLeftoverItems(world, fp, golem);
		golem.swingArm();
	}

	/** Whatever the fake player ends up holding after the click (leftover placed-item stack, or something it
	 * picked up as a side effect, e.g. a broken block's own drops landing straight in its inventory) goes
	 * back into the golem's own carry slots, or drops at the golem's feet if it can't carry any more. */
	private static void collectLeftoverItems(Level world, FakePlayer fp, IGolemAPI golem) {
		Inventory inv = fp.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.isEmpty()) continue;
			if (golem.canCarry(stack, true)) {
				stack = golem.holdItem(stack);
			}
			if (!stack.isEmpty()) {
				dropAtGolem(world, golem, stack);
			}
			inv.setItem(i, ItemStack.EMPTY);
		}
	}

	private static void dropAtGolem(Level world, IGolemAPI golem, ItemStack stack) {
		var golemEntity = golem.getGolemEntity();
		ItemEntity entity = new ItemEntity(world, golemEntity.getX(), golemEntity.getY() + 0.5, golemEntity.getZ(), stack);
		world.addFreshEntity(entity);
	}
}
