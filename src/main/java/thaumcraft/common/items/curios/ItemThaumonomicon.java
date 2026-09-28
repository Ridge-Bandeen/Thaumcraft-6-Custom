package thaumcraft.common.items.curios;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.container.ThaumonomiconMenu;

/**
 * Replaces the old (1.12.2) {@code ItemThaumonomicon}. One registry entry, not the original's two
 * damage-value variants - see {@code ItemsTC#thaumonomicon}'s own doc comment.
 *
 * Real behavior: right-click opens the real {@link ThaumonomiconMenu}/{@link
 * thaumcraft.client.gui.ThaumonomiconScreen}. The original granted its "you've seen the book" virtual research
 * flag ({@code "!gotthaumonomicon"}, which gates {@code FIRSTSTEPS} and everything downstream of it) from an
 * item-pickup event hook (stale, unported {@code PlayerEvents} - a whole separate mechanic covering several
 * other pickup/wake-up triggers, out of scope here); granting it here instead, on first use, is a small, real,
 * deliberate substitution that produces the same practical outcome (every player who ever opens their
 * Thaumonomicon has real access to the beginner research tree) without needing that larger hook.
 */
public class ItemThaumonomicon extends Item {

	private static final String GOT_THAUMONOMICON = "!gotthaumonomicon";

	private final boolean cheat;

	public ItemThaumonomicon(Properties properties) {
		this(properties, false);
	}

	public ItemThaumonomicon(Properties properties, boolean cheat) {
		super(properties);
		this.cheat = cheat;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		super.appendHoverText(stack, context, tooltip, flag);
		if (cheat) tooltip.add(Component.literal("Creative only").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!level.isClientSide) {
			IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
			if (!knowledge.isResearchKnown(GOT_THAUMONOMICON)) {
				knowledge.addResearch(GOT_THAUMONOMICON);
			}
			if (cheat && thaumcraft.common.config.ThaumcraftConfig.ALLOW_CHEAT_SHEET.get() && player instanceof net.minecraft.server.level.ServerPlayer sp) {
				for (thaumcraft.api.research.ResearchCategory cat : thaumcraft.api.research.ResearchCategories.researchCategories.values()) {
					for (thaumcraft.api.research.ResearchEntry entry : new java.util.ArrayList<>(cat.research.values())) {
						thaumcraft.common.lib.ThaumcraftCommands.giveRecursiveResearch(sp, entry.getKey());
					}
				}
			} else
			// the original's onItemRightClick: every sibling of research you already know is completed on opening -
			// how First Steps' own siblings (Knowledge Types, the dream) get filled in
			for (thaumcraft.api.research.ResearchCategory cat : thaumcraft.api.research.ResearchCategories.researchCategories.values()) {
				for (thaumcraft.api.research.ResearchEntry entry : new java.util.ArrayList<>(cat.research.values())) {
					if (entry.getSiblings() == null || !ThaumcraftCapabilities.knowsResearch(player, entry.getKey())) continue;
					for (String sibling : entry.getSiblings()) {
						if (!ThaumcraftCapabilities.knowsResearch(player, sibling)) {
							thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, sibling);
						}
					}
				}
			}
			if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) knowledge.sync(serverPlayer);
			player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ThaumonomiconMenu(id, inv),
					Component.translatable("item.thaumcraft.thaumonomicon")));
		} else {
			level.playSound(player, player.getX(), player.getY(), player.getZ(),
					thaumcraft.common.registry.ThaumcraftSounds.PAGE.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
