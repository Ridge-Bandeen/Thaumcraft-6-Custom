package thaumcraft.common.golems;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.GolemPlacerData;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code ItemGolemPlacer} - places a real, assembled {@link EntityThaumcraftGolem}
 * against a solid block, tamed to the placing player. Its assembled {@link IGolemProperties} + accumulated
 * rank XP (was raw {@code "props"}/{@code "xp"} NBT tags on the stack) now live in a real
 * {@link ThaumcraftDataComponents#GOLEM_PLACER_DATA} component - see that field's own doc comment.
 *
 * Ownership is handed off via vanilla {@link net.minecraft.world.entity.TamableAnimal#tame}, not the
 * original's own {@code setOwned}/{@code setOwnerId}/{@code setValidSpawn} trio - see
 * {@code EntityThaumcraftGolem}'s own doc comment for why it extends {@code TamableAnimal} directly.
 *
 * Real, documented cuts: {@code getSubItems}'s 4 hardcoded creative-tab starter variants (basic golem, a
 * SMART-headed one, an iron CLAWS fighter, a brass DARTS ranger) aren't wired up - this port hasn't built
 * creative-tab content population for any item yet, see {@link thaumcraft.api.items.ItemGenericEssentiaContainer}'s
 * own {@code createAspectVariants} doc comment for the same still-open gap. A bare placer (default/all-zero
 * properties, i.e. a WOOD/BASIC/BASIC/WALKER/NONE golem) is still real and obtainable via
 * {@code /give}/creative search. Implements the real (if currently inert - no seal-rendering hook exists
 * yet) {@link ISealDisplayer} marker, matching the original.
 */
public class ItemGolemPlacer extends Item implements ISealDisplayer {

	public ItemGolemPlacer(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		GolemPlacerData data = stack.get(ThaumcraftDataComponents.GOLEM_PLACER_DATA.get());
		if (data != null) {
			IGolemProperties props = GolemProperties.fromLong(data.props());
			if (props.hasTrait(EnumGolemTrait.SMART)) {
				if (props.getRank() >= 10) {
					tooltip.add(Component.translatable("golem.rank").append(" " + props.getRank()).withStyle(ChatFormatting.GOLD));
				} else {
					int xn = (props.getRank() + 1) * (props.getRank() + 1) * 1000;
					tooltip.add(Component.translatable("golem.rank").append(" " + props.getRank() + " (" + data.xp() + "/" + xn + ")").withStyle(ChatFormatting.GOLD));
				}
			}
			tooltip.add(props.getMaterial().getLocalizedName().copy().withStyle(ChatFormatting.GREEN));
			for (EnumGolemTrait trait : props.getTraits()) {
				tooltip.add(Component.literal("-").append(trait.getLocalizedName()).withStyle(ChatFormatting.BLUE));
			}
		}
		super.appendHoverText(stack, context, tooltip, flag);
	}

	/** The original did this in {@code onItemUseFirst}, before the clicked block's own use - so a golem can be placed against a chest or other usable block. (Fixed 2026-09-29: it was only on {@link #useOn}, which runs after the block.) */
	@Override
	public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack, UseOnContext context) {
		return useOn(context);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clicked);
		if (!clickedState.isSolid()) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}
		Player player = context.getPlayer();
		BlockPos pos = clicked.relative(context.getClickedFace());
		if (player == null || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand())) {
			return InteractionResult.FAIL;
		}

		EntityThaumcraftGolem golem = ThaumcraftEntities.GOLEM.get().create((ServerLevel) level);
		if (golem == null) {
			return InteractionResult.FAIL;
		}
		golem.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		if (!level.addFreshEntity(golem)) {
			return InteractionResult.FAIL;
		}
		golem.tame(player);

		ItemStack stack = context.getItemInHand();
		GolemPlacerData data = stack.get(ThaumcraftDataComponents.GOLEM_PLACER_DATA.get());
		IGolemProperties props = data != null ? GolemProperties.fromLong(data.props()) : new GolemProperties();
		int xp = data != null ? data.xp() : 0;
		golem.initializeGolem(props, xp);

		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
