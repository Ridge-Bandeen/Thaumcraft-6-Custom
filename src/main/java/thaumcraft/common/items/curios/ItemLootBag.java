package thaumcraft.common.items.curios;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.common.lib.utils.LootGenerator;

/**
 * Replaces the old (1.12.2) {@code ItemLootBag} - right-click to consume and spill 8 to 12 random items on the ground,
 * three tiers (common/uncommon/rare, each its own item rather than the original's damage-value variants). A recently-hit
 * champion drops one, tiered by looting level - see {@code Thaumcraft#onLivingDrops}. The items come from the original's
 * generator ({@link LootGenerator}), whose pools addons fill through {@code ThaumcraftApi#addLootBagItem}.
 */
public class ItemLootBag extends Item {

	private final int rarity;

	public ItemLootBag(Properties properties, int rarity) {
		super(properties);
		this.rarity = rarity;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			for (int q = 8 + level.random.nextInt(5), a = 0; a < q; ++a) {
				ItemStack loot = LootGenerator.generateLoot(rarity, level.random, level.registryAccess());
				if (!loot.isEmpty()) {
					level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, player.getX(), player.getY(), player.getZ(), loot));
				}
			}
			level.playSound(null, player.blockPosition(), thaumcraft.common.registry.ThaumcraftSounds.COINS.get(), SoundSource.PLAYERS, 0.75f, 1.0f);
		}
		stack.shrink(1);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** The original: "Click to open, or keep to trade." */
	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(net.minecraft.network.chat.Component.translatable("tc.lootbag"));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
