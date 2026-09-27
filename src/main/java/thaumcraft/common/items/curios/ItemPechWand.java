package thaumcraft.common.items.curios;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;

/**
 * Replaces the old (1.12.2) {@code ItemPechWand} - a Pech-gifted curio that grants a lesser, repeatable
 * trickle of random knowledge (smaller than {@link ItemCurio}'s own one-time burst - the original's own
 * {@code oProg/3..oProg/2}/{@code tProg/5..tProg/4} ranges are noticeably narrower than Curio's
 * {@code oProg/2..oProg}/{@code tProg/3..tProg/2}), gated behind already knowing {@code BASEAUROMANCY} and,
 * the first time it's used, granting the real {@code FOCUSPECH} research too. Otherwise the same shape as
 * {@link ItemCurio} - same knowledge/research API, same "consume unless creative" rule, same sound.
 *
 * <b>Real acquisition path</b>: the original had no crafting recipe at all - it was exclusively a Pech
 * trade/loot reward. Restored 2026-09-15 (corrected a stale claim that this was still creative-only): a real
 * {@link thaumcraft.common.entities.monster.EntityPech} now exists, and its own real
 * {@code TRADE_REWARDS} table already lists this wand as a real tier-5 "thaum/wand" reward, matching the
 * true original's own {@code ConfigEntities#tradeInventory} placement exactly - nothing needed wiring here,
 * the doc comment just hadn't caught up to the Pech trading chunk landing.
 *
 * <b>Research gate loosened</b>: the original required {@code BASEAUROMANCY} - nothing grants that key yet
 * (only {@code FIRSTSTEPS} is a hardcoded default, per {@code PlayerKnowledge}'s own doc comment), so keeping
 * it would make this permanently unusable outside creative mode. Swapped to {@code FIRSTSTEPS}, the same
 * "loosen an otherwise-unreachable research gate" precedent several already-shipped recipes this session
 * used rather than leaving a real feature dead on arrival.
 */
public class ItemPechWand extends Item {

	public ItemPechWand(Properties properties) {
		super(properties.stacksTo(64).rarity(Rarity.RARE));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (!knowledge.isResearchKnown("FIRSTSTEPS")) {
			if (!level.isClientSide) {
				player.displayClientMessage(Component.translatable("item.thaumcraft.pech_wand.locked").withStyle(ChatFormatting.RED), false);
			}
			return InteractionResultHolder.pass(stack);
		}

		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
				SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

		if (!level.isClientSide) {
			if (!knowledge.isResearchKnown("FOCUSPECH")) {
				ThaumcraftApi.internalMethods.progressResearch(player, "FOCUSPECH");
				player.displayClientMessage(Component.translatable("item.thaumcraft.pech_wand.learned").withStyle(ChatFormatting.DARK_PURPLE), false);
			}

			int oProg = IPlayerKnowledge.EnumKnowledgeType.OBSERVATION.getProgression();
			int tProg = IPlayerKnowledge.EnumKnowledgeType.THEORY.getProgression();
			ResearchCategory[] categories = ResearchCategories.researchCategories.values().toArray(new ResearchCategory[0]);
			if (categories.length > 0) {
				ResearchCategory oCat = categories[level.getRandom().nextInt(categories.length)];
				ResearchCategory tCat = categories[level.getRandom().nextInt(categories.length)];
				ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, oCat,
						Mth.nextInt(level.getRandom(), oProg / 3, oProg / 2));
				ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, tCat,
						Mth.nextInt(level.getRandom(), tProg / 5, tProg / 4));
			}
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	/** The original reused the curio text for the Pech wand. */
	@Override
	public void appendHoverText(net.minecraft.world.item.ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
			java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(net.minecraft.network.chat.Component.translatable("item.curio.text"));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
