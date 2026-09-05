package thaumcraft.common.items.armor;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.items.IGoggles;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemFortressArmor} - the Fortress armor tier (helm/chest/legs only, no
 * boots, matching the original's own real recipe list), crafted at the Infusion Altar from the matching
 * Thaumium armor piece. Two further real, orthogonal helm-only upgrades from the original, ported as data
 * components instead of raw NBT (see {@link ThaumcraftDataComponents#FORTRESS_MASK}/{@code
 * #FORTRESS_GOGGLES}): the Helm of Revealing mod ({@link IGoggles} popups, no vis discount - matching the
 * original's own in-game research text: "It does not, however, give the vis discount the goggles usually
 * do") and one of 3 real combat masks (Grinning Devil/Angry Ghost/Sipping Fiend - see {@code
 * thaumcraft.common.lib.events.FortressMaskEvents} for the real Wither-on-hit/lifesteal-on-hit effects;
 * Grinning Devil's own real Warp-misfortune-dampening effect lives in {@code
 * thaumcraft.common.lib.events.WarpEvents#checkWarpEvent}, once that whole system existed to check it).
 *
 * Real, deliberate simplification vs the original: the original's real {@code ISpecialArmor} damage curve
 * (gone in modern MC - real vanilla armor/toughness stats cover the "better tier" role instead, same
 * precedent as every other armor piece this session) and its "a helm can wear a mask AND the goggles mod at
 * once, stacking recipes" combinability are both dropped. Each of the 4 real upgrade recipes sets its own
 * component(s) on a FRESH result stack rather than reading-and-preserving whatever the central item already
 * had, so applying a second upgrade recipe to an already-upgraded helm replaces the first rather than
 * stacking with it - a real, minor fidelity loss; a proper fix would need a small custom recipe class
 * (matching {@code InfusionCrystalRecipe}'s own precedent) to carry the central item's existing components
 * forward, not attempted this pass.
 */
public class ItemFortressArmor extends ArmorItem implements IGoggles {

	public ItemFortressArmor(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public boolean showIngamePopups(ItemStack itemstack, LivingEntity player) {
		return getType() == Type.HELMET && Boolean.TRUE.equals(itemstack.get(ThaumcraftDataComponents.FORTRESS_GOGGLES.get()));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (Boolean.TRUE.equals(stack.get(ThaumcraftDataComponents.FORTRESS_GOGGLES.get()))) {
			tooltip.add(Component.translatable("item.fortress_helm.goggles").withStyle(ChatFormatting.DARK_PURPLE));
		}
		Integer mask = stack.get(ThaumcraftDataComponents.FORTRESS_MASK.get());
		if (mask != null) {
			tooltip.add(Component.translatable("item.fortress_helm.mask." + mask).withStyle(ChatFormatting.GOLD));
		}
	}
}
