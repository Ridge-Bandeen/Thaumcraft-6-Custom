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
 * Restored 2026-09-25 (a playtest noticed the missing effects): the client throws the original sparkle stream from the
 * player hand toward the target plus block sparkles over the structure, and the dust sound plays.
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

			if (context.getLevel().isClientSide) {
				// the original threw its sparkles from the client copy of the use (see ClientFxHandlers#spawnDustSparkles)
				thaumcraft.client.fx.ClientFxHandlers.spawnDustSparkles(context.getLevel(), context.getPlayer(), context.getHand(),
						context.getClickedPos(), context.getClickLocation(), trigger, place);
			} else {
				ItemStack stack = context.getItemInHand();
				if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
					stack.shrink(1);
				}
				trigger.execute(context.getLevel(), context.getPlayer(), context.getClickedPos(), place, context.getClickedFace());
				context.getLevel().playSound(null, context.getClickedPos(), thaumcraft.common.registry.ThaumcraftSounds.DUST.get(), SoundSource.PLAYERS,
						0.33f, 1.0f + (float) context.getLevel().random.nextGaussian() * 0.05f);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}
}
