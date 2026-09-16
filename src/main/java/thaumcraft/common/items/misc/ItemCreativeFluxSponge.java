package thaumcraft.common.items.misc;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code ItemCreativeFluxSponge} - a real, creative-only debug/utility item, a
 * fresh-sweep find (registered in the true original, but genuinely never a real gameplay-progression item -
 * no crafting recipe, {@code Rarity.EPIC} flair, an explicit "Creative only" tooltip line). Right-clicking
 * drains real flux from an 81-chunk area (a real 9x9 grid of real {@code AuraHelper.drainFlux} calls, one
 * per chunk center, matching the original's own real {@code x/z in [-4,4]} loop and its real per-call cap
 * of 500) centered on the player; sneak-right-clicking also removes every real {@link EntityFluxRift} within
 * 32 blocks.
 *
 * <p>Real custom {@code craftstart} activation sound restored 2026-09-15 (was a vanilla {@code
 * EVOKER_CAST_SPELL} stand-in) - see {@link ThaumcraftSounds}'s own doc comment.
 */
public class ItemCreativeFluxSponge extends Item {

	public ItemCreativeFluxSponge(Properties properties) {
		super(properties.stacksTo(1).rarity(Rarity.EPIC));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.literal("Right-click to drain all").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.literal("flux from 9x9 chunk area").withStyle(ChatFormatting.GREEN));
		tooltip.add(Component.literal("Also removes flux rifts").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.literal("if used while sneaking.").withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.literal("Creative only").withStyle(ChatFormatting.DARK_PURPLE));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			player.swing(hand);
			level.playSound(player, player.getX(), player.getY(), player.getZ(), ThaumcraftSounds.CRAFTSTART.get(), SoundSource.PLAYERS, 0.15f, 1.0f);
			return InteractionResultHolder.success(stack);
		}

		int drained = 0;
		net.minecraft.core.BlockPos pos = player.blockPosition();
		for (int x = -4; x <= 4; x++) {
			for (int z = -4; z <= 4; z++) {
				drained += (int) AuraHelper.drainFlux(level, pos.offset(16 * x, 0, 16 * z), 500.0f, false);
			}
		}
		player.displayClientMessage(Component.literal(drained + " flux drained from 81 chunks.").withStyle(ChatFormatting.GREEN), false);

		if (player.isShiftKeyDown()) {
			List<EntityFluxRift> rifts = level.getEntitiesOfClass(EntityFluxRift.class, new AABB(pos).inflate(32.0));
			for (EntityFluxRift rift : rifts) {
				rift.discard();
			}
			player.displayClientMessage(Component.literal(rifts.size() + " flux rifts removed.").withStyle(ChatFormatting.DARK_AQUA), false);
		}

		return InteractionResultHolder.success(stack);
	}
}
