package thaumcraft.common.items.curios;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;

/**
 * "Curio" - a consumable that grants the player raw research knowledge. Ported as a single item rather
 * than the original's 8 metadata variants (curio/arcane/preserved/ancient/eldritch/knowledge/twisted/rites),
 * each of which picked a different fixed research category (plus a shared "always grant a bonus random
 * category" tail every variant fell through to) - that per-variant flavor text is cosmetic, and the old
 * damage-value/metadata item-variant system it relied on isn't ported (modern items are one registry entry
 * each, see {@code ItemTCBase}'s own doc comment on why it's still the 1.12.2 original, unported). This
 * item keeps the mechanically meaningful part - grant Observation+Theory knowledge in a random category
 * (the original's own always-applies tail block) - and drops the metadata-selected named category on top of
 * it, along with the original's case-6 "Crimson Rites" special branch (needed specific warp-threshold
 * research not built - the same "AURA PRESERVE"-style research-gate omission already used elsewhere).
 */
public class ItemCurio extends Item {

	public ItemCurio(Properties properties) {
		super(properties.stacksTo(64).rarity(Rarity.UNCOMMON));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE,
				SoundSource.NEUTRAL, 0.5f, 0.4f / (level.getRandom().nextFloat() * 0.4f + 0.8f));

		if (!level.isClientSide) {
			int oProg = IPlayerKnowledge.EnumKnowledgeType.OBSERVATION.getProgression();
			int tProg = IPlayerKnowledge.EnumKnowledgeType.THEORY.getProgression();

			ResearchCategory[] categories = ResearchCategories.researchCategories.values().toArray(new ResearchCategory[0]);
			if (categories.length > 0) {
				ResearchCategory oCat = categories[level.getRandom().nextInt(categories.length)];
				ResearchCategory tCat = categories[level.getRandom().nextInt(categories.length)];
				ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, oCat,
						Mth.nextInt(level.getRandom(), oProg / 2, oProg));
				ThaumcraftApi.internalMethods.addKnowledge(player, IPlayerKnowledge.EnumKnowledgeType.THEORY, tCat,
						Mth.nextInt(level.getRandom(), tProg / 3, tProg / 2));
			}

			if (!player.getAbilities().instabuild) {
				stack.shrink(1);
			}
			player.displayClientMessage(Component.translatable("tc.knowledge.gained").withStyle(ChatFormatting.DARK_PURPLE), false);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}
}
