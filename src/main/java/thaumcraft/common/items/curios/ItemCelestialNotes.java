package thaumcraft.common.items.curios;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Replaces the old (1.12.2) {@code ItemCelestialNotes} - a real 13-variant "what I saw in the sky" flavor
 * item ({@link thaumcraft.common.lib.research.ScanSky} grants one when a player scans the sun, a star
 * quadrant, or the current real moon phase), consumed in pairs by {@link
 * thaumcraft.common.lib.research.theorycraft.CardCelestial}. Despite living under this port's own inherited
 * {@code common.items.curios} package (a loose "small trinket-ish item" grouping this mod uses, not every
 * member of which is Baubles-equippable), this item implements no Baubles/curio interface at all in either
 * the true original or this port - it is never worn, just carried and consumed. See {@link
 * thaumcraft.api.items.ItemsTC#celestialNotes} for why this port splits the original's single 13-metadata-
 * variant field into 13 real separate items instead.
 */
public class ItemCelestialNotes extends Item {

	private final String variant;

	public ItemCelestialNotes(Properties properties, String variant) {
		super(properties);
		this.variant = variant;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.celestial_notes." + variant + ".text").withStyle(ChatFormatting.AQUA));
	}
}
