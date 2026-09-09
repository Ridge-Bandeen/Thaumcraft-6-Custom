package thaumcraft.common.items.tools;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.research.ScanningManager;

/**
 * Replaces the old (1.12.2) {@code ItemThaumometer}. Right-clicking any registered scan target (an entity
 * or a block - see {@link ScanningManager}) grants its linked research key, the first real consumer of that
 * newly-real system. Dispatches through the modern per-target-kind hooks ({@link #interactLivingEntity} for
 * entities, {@link #onItemUseFirst} for blocks, {@link #use} for neither) instead of the original's single
 * manual raytrace covering all three - three real entry points instead of one, but each individually
 * simpler.
 *
 * <p>{@link #use} (fires only when neither of the other two hooks already consumed the interaction, i.e.
 * genuinely nothing in reach was targeted) was originally dropped as low-value on its own - restored once
 * {@link thaumcraft.common.lib.research.ScanSky} needed it for real: that device's own real trigger IS
 * "scanned nothing, but looked at the sky" ({@code obj == null} is a real, load-bearing part of its {@code
 * checkThing} contract, not an edge case), so this hook needed to genuinely exist for
 * {@link thaumcraft.common.lib.research.theorycraft.CardCelestial}'s whole dependency chain to be reachable
 * at all.
 *
 * Simplified vs the original: no aura-chunk HUD overlay (the original's {@code onUpdate} pushed a client
 * aura-display packet every 20 ticks while held) or scan-target highlight particles (the original's
 * client-side {@code onUpdate}/{@code drawFX}) - both real client FX/HUD work, same boundary as every other
 * device this session.
 */
public class ItemThaumometer extends Item {

	public ItemThaumometer(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (player.level().isClientSide) return InteractionResult.SUCCESS;
		ScanningManager.scanTheThing(player, target);
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (player == null) return InteractionResult.PASS;
		if (level.isClientSide) return InteractionResult.SUCCESS;
		ScanningManager.scanTheThing(player, context.getClickedPos());
		return InteractionResult.SUCCESS;
	}

	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) return net.minecraft.world.InteractionResultHolder.success(stack);
		ScanningManager.scanTheThing(player, null);
		return net.minecraft.world.InteractionResultHolder.success(stack);
	}
}
