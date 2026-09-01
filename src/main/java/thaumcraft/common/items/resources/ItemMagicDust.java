package thaumcraft.common.items.resources;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import thaumcraft.api.crafting.IDustTrigger;

/**
 * Replaces the old (1.12.2) {@code ItemMagicDust} - Salis Mundus, a dust that converts specific blocks
 * into their Thaumcraft counterparts (see {@code thaumcraft.common.lib.crafting.DustTriggerSimple}'s own
 * doc comment). {@link Item#useOn} is the modern equivalent of the original's {@code onItemUseFirst} - it
 * runs before the clicked block's own interaction handler, letting this preempt (e.g.) the crafting
 * table's GUI the same way the original did.
 *
 * Simplified: no sparkle particle trail / custom dust sound on use, same "no client FX" precedent as every
 * other item this session - just a plain sound cue confirming the swap happened.
 */
public class ItemMagicDust extends Item {

	public ItemMagicDust(Properties properties) {
		super(properties.stacksTo(64).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getPlayer() != null && context.isSecondaryUseActive()) {
			return InteractionResult.PASS;
		}
		for (IDustTrigger trigger : IDustTrigger.triggers) {
			IDustTrigger.Placement place = trigger.getValidFace(context.getLevel(), context.getPlayer(), context.getClickedPos(), context.getClickedFace());
			if (place == null) continue;

			if (!context.getLevel().isClientSide) {
				ItemStack stack = context.getItemInHand();
				if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
					stack.shrink(1);
				}
				trigger.execute(context.getLevel(), context.getPlayer(), context.getClickedPos(), place, context.getClickedFace());
				context.getLevel().playSound(null, context.getClickedPos(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.5f, 1.0f);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
