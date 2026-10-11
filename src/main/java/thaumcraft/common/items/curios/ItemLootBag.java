package thaumcraft.common.items.curios;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Replaces the old (1.12.2) {@code ItemLootBag} - right-click to consume and spill several random items on
 * the ground, three separate tiers (common/uncommon/rare, each its own item rather than the original's
 * damage-value variants). A recently-hit vanilla hostile mob has a chance to drop one, tiered by looting
 * level - see {@code Thaumcraft#onLivingDrops}.
 *
 * The original rolled its own hand-written weighted item pool ({@code Utils.generateLoot}/{@code genGear} -
 * a full random-gear generator picking from the leather-through-diamond progression with random
 * enchantments and durability) referencing a large, mostly still-unbuilt mod item pool. Reproduced here with
 * vanilla's own data-driven loot table system instead - each tier is a real loot table
 * ({@code loot_table/gameplay/loot_bag_<tier>.json}) rolled via {@code LootContextParamSets.GIFT} (the same
 * context vanilla uses for villager gifts - a good semantic match for "open this, get random stuff"). Same
 * net shape (better tier, better stuff) with a much smaller, curated vanilla-only pool rather than porting a
 * generator that mostly targets items this port doesn't have yet.
 */
public class ItemLootBag extends Item {

	private final ResourceKey<LootTable> lootTable;

	public ItemLootBag(Properties properties, ResourceLocation lootTableId) {
		super(properties);
		this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, lootTableId);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer) {
			LootTable table = serverLevel.getServer().reloadableRegistries().getLootTable(lootTable);
			LootParams params = new LootParams.Builder(serverLevel)
					.withParameter(LootContextParams.ORIGIN, player.position())
					.withParameter(LootContextParams.THIS_ENTITY, player)
					.create(LootContextParamSets.GIFT);
			for (ItemStack loot : table.getRandomItems(params)) {
				player.spawnAtLocation(loot);
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
