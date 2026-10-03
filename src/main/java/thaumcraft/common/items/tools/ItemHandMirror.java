package thaumcraft.common.items.tools;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink;
import thaumcraft.common.blocks.devices.BlockMirror;
import thaumcraft.common.container.HandMirrorMenu;
import thaumcraft.common.tiles.devices.TileMirror;

/**
 * Replaces the old (1.12.2) {@code ItemHandMirror} - a real, portable remote-delivery tool for the Node
 * Transducer network. Distinct from {@link thaumcraft.common.blocks.devices.BlockMirrorItem} (which links two
 * mirrors to each OTHER at placement time): this links itself to any one already-placed real {@link
 * TileMirror} ({@link #useOn}, no validity check on the target - unlike {@code BlockMirrorItem}, the original
 * let a Hand Mirror re-point at any real mirror at any time, even an already-linked one), then lets the
 * holder open a remote delivery menu from anywhere ({@link #use}); what's put in pops out of THAT mirror (via
 * {@link TileMirror#transportDirect}), as the original and its book page say ("they will pop out at the linked
 * mirror").
 *
 * Real link storage reuses {@link ThaumcraftDataComponents#MIRROR_LINK} directly (the same real component
 * {@code BlockMirrorItem}/{@code TileMirror} already use) rather than inventing a second, parallel one -
 * replaces the original's own raw {@code linkX}/{@code linkY}/{@code linkZ}/{@code linkDim} NBT.
 */
public class ItemHandMirror extends Item {

	public ItemHandMirror(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
	}

	/** Real port of the original's own {@code onItemUseFirst} - right-clicking any real placed Mirror block
	 * links this Hand Mirror to it, overwriting whatever it was linked to before. */
	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level.getBlockState(context.getClickedPos()).getBlock() instanceof BlockMirror) || context.getPlayer() == null) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof TileMirror)) {
			return InteractionResult.PASS;
		}

		ItemStack held = context.getItemInHand();
		held.set(ThaumcraftDataComponents.MIRROR_LINK.get(), new MirrorLink(level.dimension(), context.getClickedPos().immutable()));
		level.playSound(null, context.getClickedPos(), thaumcraft.common.registry.ThaumcraftSounds.JAR.get(), SoundSource.BLOCKS, 1.0f, 2.0f);
		player.sendSystemMessage(Component.translatable("tc.handmirrorlinked"));
		return InteractionResult.CONSUME;
	}

	/** Real port of the original's own {@code onItemRightClick} - a real linked Hand Mirror right-clicked in
	 * open air opens the real remote delivery menu, provided the linked mirror still real exists; otherwise
	 * the real link is cleared and the original's own real error message/sound plays. */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			return InteractionResultHolder.pass(stack);
		}
		MirrorLink link = stack.get(ThaumcraftDataComponents.MIRROR_LINK.get());
		if (link == null) {
			return InteractionResultHolder.pass(stack);
		}
		if (!resolveTarget(level, link).isPresent()) {
			breakLink(stack, player);
			return InteractionResultHolder.fail(stack);
		}
		player.openMenu(new net.minecraft.world.MenuProvider() {
			@Override
			public Component getDisplayName() {
				return Component.translatable("item.thaumcraft.hand_mirror");
			}

			@Override
			public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, Player p) {
				return new HandMirrorMenu(id, inv, stack);
			}
		});
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		MirrorLink link = stack.get(ThaumcraftDataComponents.MIRROR_LINK.get());
		if (link != null) {
			tooltip.add(Component.translatable("tc.handmirrorlinkedto")
					.append(" " + link.pos().getX() + "," + link.pos().getY() + "," + link.pos().getZ())
					.withStyle(ChatFormatting.DARK_PURPLE));
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/** The original's broken-link response: the link is forgotten, with a zap and a message. */
	public static void breakLink(ItemStack stack, Player player) {
		stack.remove(ThaumcraftDataComponents.MIRROR_LINK.get());
		player.level().playSound(null, player.blockPosition(), thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), SoundSource.PLAYERS, 1.0f, 0.8f);
		player.sendSystemMessage(Component.translatable("tc.handmirrorerror"));
	}

	/** Real, static resolver shared with {@link HandMirrorMenu} - a real linked target tile only if the real
	 * dimension is loaded and the real block entity at that position still is a {@link TileMirror}. */
	public static java.util.Optional<TileMirror> resolveTarget(Level level, MirrorLink link) {
		if (!(level instanceof ServerLevel serverLevel)) return java.util.Optional.empty();
		ServerLevel targetLevel = serverLevel.getServer().getLevel(link.dimension());
		if (targetLevel == null || !targetLevel.isLoaded(link.pos())) return java.util.Optional.empty();
		return targetLevel.getBlockEntity(link.pos()) instanceof TileMirror tile ? java.util.Optional.of(tile) : java.util.Optional.empty();
	}
}
