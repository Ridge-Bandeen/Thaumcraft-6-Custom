package thaumcraft.common.golems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.common.golems.seals.SealHandler;

/**
 * Replaces the old (1.12.2) {@code ItemGolemBell}. Real, working piece: sneak-right-click a sealed block
 * face to remove that seal, matching the original's own sneak-to-remove gesture exactly. Uses
 * {@link UseOnContext}'s own real clicked-block-and-face directly, rather than the original's own
 * CodeChicken-raytrace-based {@code getSeal(EntityPlayer)} line-march (a per-pixel-hitbox raytrace this
 * port has never needed anywhere else) - a plain right click already tells us exactly which block face was
 * clicked, which is all a {@link SealPos} ever needs.
 *
 * A non-sneak click on a sealed face now opens a real {@link thaumcraft.common.container.SealConfigMenu} (see
 * that class's own doc comment) - see {@link thaumcraft.common.golems.seals.SealEntity#createMenu}, the same
 * {@code MenuProvider} pattern {@code TileGolemBuilder} already established. Real, documented cut: the
 * original's own separate sneak-click-anywhere golem-overview GUI (gated on the "GOLEMLOGISTICS" research)
 * doesn't exist in this port - a real, separately-scoped screen with no seal-specific state to reuse from
 * this chunk's own work.
 *
 * <p>Restored 2026-09-25: that golem-logistics screen ({@link thaumcraft.common.container.LogisticsMenu}) now opens on a
 * sneak-click - on a block (not a sealed face) to have items delivered there, or in the air to have them delivered to
 * you - once the GOLEMLOGISTICS research is known, as in the original.
 */
public class ItemGolemBell extends Item implements ISealDisplayer {

	public ItemGolemBell(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (level.isClientSide || player == null) {
			return InteractionResult.PASS;
		}
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		SealPos sealPos = new SealPos(pos, face);
		ISealEntity seal = GolemHelper.getSealEntity(level.dimension(), sealPos);
		if (seal == null) {
			// The original's golem-logistics screen: sneak-click a block (once GOLEMLOGISTICS is researched) to request
			// items from your Provide seals be brought to that block face.
			if (player.isShiftKeyDown() && thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearch(player, "GOLEMLOGISTICS")) {
				openLogistics(player, pos, face);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			SealHandler.removeSealEntity(level, sealPos);
			level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		if (seal instanceof net.minecraft.world.MenuProvider provider) {
			player.openMenu(provider);
		}
		return InteractionResult.SUCCESS;
	}

	/** Sneak-right-click in the AIR (GOLEMLOGISTICS researched): the logistics screen, delivering to you. */
	@Override
	public net.minecraft.world.InteractionResultHolder<net.minecraft.world.item.ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
		net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
		if (!level.isClientSide && player.isShiftKeyDown() && thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearch(player, "GOLEMLOGISTICS")
				&& getPlayerPOVHitResult(level, player, net.minecraft.world.level.ClipContext.Fluid.NONE).getType() == net.minecraft.world.phys.HitResult.Type.MISS) {
			openLogistics(player, null, null);
			return net.minecraft.world.InteractionResultHolder.success(held);
		}
		return super.use(level, player, hand);
	}

	private static void openLogistics(Player player, @javax.annotation.Nullable BlockPos target, @javax.annotation.Nullable Direction side) {
		player.openMenu(new net.minecraft.world.SimpleMenuProvider(
				(id, inv, p) -> new thaumcraft.common.container.LogisticsMenu(id, inv, target, side),
				net.minecraft.network.chat.Component.translatable("item.thaumcraft.golem_bell")),
				buf -> thaumcraft.common.container.LogisticsMenu.writeTarget(buf, target, side));
	}
}
