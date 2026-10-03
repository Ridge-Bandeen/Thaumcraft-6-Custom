package thaumcraft.common.items.casters;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusModSplit;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemFocus}: the three focus tiers ({@code focus_1/2/3}, complexity caps 15/25/50) a
 * player builds a spell onto at the Focal Manipulator, as the original. The built node graph is stored in
 * {@link ThaumcraftDataComponents#ASSEMBLED_FOCUS} (the original's {@code "package"} NBT); a focus without one is blank
 * and casts nothing.
 */
public class ItemFocus extends Item {

	private final int maxComplexity;

	public ItemFocus(Properties properties, int maxComplexity) {
		super(properties.stacksTo(1).rarity(Rarity.RARE));
		this.maxComplexity = maxComplexity;
	}

	/** The total node complexity a Focal Manipulator will build onto this tier. */
	public int getMaxComplexity() {
		return maxComplexity;
	}

	/** The built spell, or an empty package for a blank focus. */
	public FocusPackage getPackage(ItemStack stack) {
		ThaumcraftDataComponents.AssembledFocus assembled = stack.get(ThaumcraftDataComponents.ASSEMBLED_FOCUS.get());
		return assembled != null ? assembled.focusPackage() : new FocusPackage();
	}

	private static boolean isBuilt(ItemStack stack) {
		return stack.has(ThaumcraftDataComponents.ASSEMBLED_FOCUS.get());
	}

	/** The original's own getFocusColor: the average of the effect elements' colours, white when the focus casts
	 * nothing (a blank). Tints the focus item and the caster's focus gem. The original cached the result in the
	 * stack's NBT; recomputed here, since item components can't be written from a render call. */
	public int getFocusColor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0xFFFFFFFF;
		thaumcraft.api.casters.FocusEffect[] effects = getPackage(stack).getFocusEffects();
		if (effects == null || effects.length == 0) return 0xFFFFFFFF;
		int r = 0, g = 0, b = 0;
		for (thaumcraft.api.casters.FocusEffect effect : effects) {
			int c = FocusEngine.getElementColor(effect.getKey());
			r += (c >> 16) & 0xFF;
			g += (c >> 8) & 0xFF;
			b += c & 0xFF;
		}
		return 0xFF000000 | (r / effects.length) << 16 | (g / effects.length) << 8 | (b / effects.length);
	}

	public int getComplexity(ItemStack stack) {
		return isBuilt(stack) ? getPackage(stack).getComplexity() : 0;
	}

	public float getVisCost(ItemStack stack) {
		return getComplexity(stack) / 5.0f;
	}

	public int getActivationTime(ItemStack stack) {
		if (!isBuilt(stack)) return 0;
		int complexity = getComplexity(stack);
		return Math.max(5, complexity / 5 * (complexity / 4));
	}

	/** As the original: on a built focus, the cost, then every node (not the root) with its settings, split branches
	 * indented beneath their split. */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		if (!isBuilt(stack)) return;
		tooltip.add(Component.translatable("item.thaumcraft.focus.cost", String.format("%.1f", getVisCost(stack))));
		for (IFocusElement node : getPackage(stack).nodes) {
			if (node instanceof FocusNode focusNode && !(node instanceof FocusMediumRoot)) {
				buildInfo(tooltip, focusNode, 0);
			}
		}
	}

	private static void buildInfo(List<Component> tooltip, FocusNode node, int depth) {
		StringBuilder line = new StringBuilder("  ".repeat(depth));
		line.append(ChatFormatting.DARK_PURPLE).append(Component.translatable(node.getUnlocalizedName()).getString());
		if (!node.getSettingList().isEmpty()) {
			line.append(ChatFormatting.DARK_AQUA).append(" [");
			boolean first = true;
			for (String key : node.getSettingList()) {
				NodeSetting setting = node.getSetting(key);
				if (!first) line.append(", ");
				line.append(setting.getLocalizedName().getString()).append(' ').append(setting.getValueText().getString());
				first = false;
			}
			line.append(']');
		}
		tooltip.add(Component.literal(line.toString()));
		if (node instanceof FocusModSplit split) {
			for (FocusPackage p : split.getSplitPackages()) {
				for (IFocusElement fe : p.nodes) {
					if (fe instanceof FocusNode child && !(fe instanceof FocusMediumRoot)) {
						buildInfo(tooltip, child, depth + 1);
					}
				}
			}
		}
	}
}
