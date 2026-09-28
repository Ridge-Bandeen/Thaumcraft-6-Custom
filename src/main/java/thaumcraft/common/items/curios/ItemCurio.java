package thaumcraft.common.items.curios;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
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
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * The original's seven curiosities ({@code ItemCurio}'s metadata variants, one item each here): reading one grants
 * Observation and Theory in its own research category, then a second helping in a random category, and is used up.
 * The Eldritch curiosity also leaves warp; the Crimson Rites only open to a reader with more than 20 warp, who learns
 * the Crimson Rites research, Eldritch knowledge and more warp - anyone else gets "crazed ravings" and keeps the book.
 * Studying a curio at the research table picks the table category the same way (see {@code CardCurio}).
 */
public class ItemCurio extends Item {

	public enum Variant {
		ARCANE("AUROMANCY"), PRESERVED("ALCHEMY"), ANCIENT("GOLEMANCY"), ELDRITCH("ELDRITCH"), KNOWLEDGE("INFUSION"),
		TWISTED("ARTIFICE"), RITES("ELDRITCH");

		/** The research category reading this variant grants knowledge in. */
		public final String category;

		Variant(String category) {
			this.category = category;
		}
	}

	private final Variant variant;

	public ItemCurio(Properties properties, Variant variant) {
		super(properties.stacksTo(64).rarity(Rarity.UNCOMMON));
		this.variant = variant;
	}

	public Variant getVariant() {
		return variant;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), ThaumcraftSounds.LEARN.get(), SoundSource.NEUTRAL, 0.5f,
				0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
		if (level.isClientSide) return InteractionResultHolder.success(stack);

		int oProg = IPlayerKnowledge.EnumKnowledgeType.OBSERVATION.getProgression();
		int tProg = IPlayerKnowledge.EnumKnowledgeType.THEORY.getProgression();
		if (variant == Variant.RITES) {
			if (ThaumcraftApi.internalMethods.getActualWarp(player) <= 20) {
				player.sendSystemMessage(Component.translatable("fail.crimsonrites").withStyle(ChatFormatting.DARK_PURPLE));
				return InteractionResultHolder.pass(stack);
			}
			if (!thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("CrimsonRites")) {
				ThaumcraftApi.internalMethods.completeResearch(player, "CrimsonRites");
			}
		}
		grant(player, ResearchCategories.getResearchCategory(variant.category), oProg, tProg);
		if (variant == Variant.ELDRITCH || variant == Variant.RITES) {
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.NORMAL);
			ThaumcraftApi.internalMethods.addWarpToPlayer(player, 5, IPlayerWarp.EnumWarpType.TEMPORARY);
			if (variant == Variant.RITES && player.getRandom().nextBoolean()) {
				ThaumcraftApi.internalMethods.addWarpToPlayer(player, 1, IPlayerWarp.EnumWarpType.PERMANENT);
			}
		}
		ResearchCategory[] all = ResearchCategories.researchCategories.values().toArray(new ResearchCategory[0]);
		if (all.length > 0) {
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, all[player.getRandom().nextInt(all.length)],
					Mth.nextInt(player.getRandom(), oProg / 2, oProg));
			ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, all[player.getRandom().nextInt(all.length)],
					Mth.nextInt(player.getRandom(), tProg / 3, tProg / 2));
		}
		if (!player.getAbilities().instabuild) stack.shrink(1);
		player.sendSystemMessage(Component.translatable("tc.knowledge.gained").withStyle(ChatFormatting.DARK_PURPLE));
		return InteractionResultHolder.success(stack);
	}

	private static void grant(Player player, ResearchCategory category, int oProg, int tProg) {
		if (category == null) return;
		ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, category, Mth.nextInt(player.getRandom(), oProg / 2, oProg));
		ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, category, Mth.nextInt(player.getRandom(), tProg / 3, tProg / 2));
	}

	/** The original listed "What knowledge does this hold?" under the name. */
	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
		tooltip.add(Component.translatable("item.curio.text"));
		super.appendHoverText(stack, context, tooltip, flag);
	}
}
