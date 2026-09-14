package thaumcraft.common.items.armor;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.IGoggles;
import thaumcraft.api.items.IVisDiscountGear;
import thaumcraft.api.items.IWarpingGear;

/**
 * Replaces the old (1.12.2) {@code ItemVoidRobeArmor} - the Void Robe upgrade tier (crafted at the Infusion
 * Altar from a base {@link ItemVoidArmor} piece - see {@code data/thaumcraft/recipe/infusion_void_robe_*.json}),
 * one shared class for the 3 pieces the original set actually has (helm/chest/legs - no robe boots exist in
 * the original's own recipe list, only the base {@link ItemVoidArmor} boots). Real, ported-as-is effects:
 * the same self-repair tick as {@link ItemVoidArmor}, a real 5% {@link IVisDiscountGear} discount, and 3
 * real {@link IWarpingGear} warp per piece (more than the base set's 1 - a real, meaningful risk/reward step
 * up, matching the original).
 *
 * Real, deliberate cuts from the original, all substantial separate subsystems rather than one-off misses:
 * <ul>
 * <li>The original's custom {@code ISpecialArmor} damage-reduction curve (a bespoke percentage-of-max-health
 * formula, boosted further against magic damage) - 1.12.2's {@code ISpecialArmor} API doesn't exist any
 * more; modern armor always goes through the vanilla {@code Attributes.ARMOR}/{@code ARMOR_TOUGHNESS}
 * formula, which this piece already gets for free from its real {@link ArmorMaterial} stats (a genuinely
 * better tier than {@link ItemVoidArmor}'s own base stats).</li>
 * <li>The original's custom 3D {@code ModelRobe} biped model (a real, hand-modeled alternate armor
 * silhouette) - rendered here with the plain vanilla armor-layer system instead, same as every other armor
 * piece this port has shipped.</li>
 * <li><s>Dye/color support.</s> <b>Restored</b> - these pieces are in the real {@code #minecraft:dyeable}
 * item tag now, so vanilla's own real {@code ArmorDyeRecipe} and its own real cauldron-wash interaction
 * both apply with no mod-side recipe code at all, the color living in vanilla's own
 * {@link net.minecraft.core.component.DataComponents#DYED_COLOR} component. See {@link ItemRobeArmor}'s own
 * doc comment for the full story (both robe families share it, and share
 * {@link ItemRobeArmor#DEFAULT_ROBE_COLOR}, the original's own real undyed default).</li>
 * <li>{@link thaumcraft.api.items.IRevealer} (showing world nodes) is NOT implemented - the interface's own
 * doc comment already marks it {@code @Deprecated} in the original ("nodes do not exist ingame"), so this is
 * a real, already-inert feature in the original too, not a porting gap.</li>
 * </ul>
 * {@link IGoggles} IS implemented (real API completeness, matching the original's head-slot-only behavior),
 * though the real HUD-popup consumer that would actually act on it ({@code EntityUtils}'s own real
 * goggles-detection helper) isn't ported yet either - a separate, pre-existing gap.
 */
public class ItemVoidRobeArmor extends ArmorItem implements IVisDiscountGear, IWarpingGear, IGoggles {

	public ItemVoidRobeArmor(Holder<ArmorMaterial> material, Type type, Properties properties) {
		super(material, type, properties);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
		super.inventoryTick(stack, level, entity, slotId, isSelected);
		if (!level.isClientSide && stack.isDamaged() && entity.tickCount % 20 == 0) {
			stack.setDamageValue(stack.getDamageValue() - 1);
		}
	}

	@Override
	public int getVisDiscount(ItemStack stack, Player player) {
		return 5;
	}

	@Override
	public int getWarp(ItemStack itemstack, Player player) {
		return 3;
	}

	@Override
	public boolean showIngamePopups(ItemStack itemstack, LivingEntity player) {
		return getType() == Type.HELMET;
	}
}
