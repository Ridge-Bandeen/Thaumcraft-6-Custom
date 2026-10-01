package thaumcraft.common.items.casters;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusNode;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemFocus}. The original stored an arbitrary, player-assembled node
 * list in NBT, built at a Focal Manipulator - now real again (see {@code TileFocalManipulator}'s own doc
 * comment), stored in {@link thaumcraft.api.items.ThaumcraftDataComponents#ASSEMBLED_FOCUS}. Subclasses
 * with a fixed {@link #createNodes} chain (see {@link ItemFocusFire} and its siblings) still work exactly
 * as before - those were a real, deliberate simplification standing in for the missing Manipulator, and
 * keeping them alongside the real assemblable {@link ItemFocusBlank} tiers gives players both a set of
 * ready-made foci and the original's own build-your-own option, rather than replacing one with the other.
 */
public abstract class ItemFocus extends Item {

	public ItemFocus(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** The fixed medium+effect node chain this focus casts when no {@code ASSEMBLED_FOCUS} component is
	 * present. {@link ItemFocusBlank} (the only subclass a real player-assembled graph can attach to)
	 * returns an empty chain here - see its own doc comment. */
	protected abstract List<IFocusElement> createNodes();

	/** The total node complexity a Focal Manipulator will accept for this item - see
	 * {@code TileFocalManipulator#startCraft}. Only meaningful for {@link ItemFocusBlank} tiers (the only
	 * subclass ever placed in a Manipulator); the default here is irrelevant for every fixed-chain focus. */
	public int getMaxComplexity() {
		return Integer.MAX_VALUE;
	}

	public FocusPackage getPackage(ItemStack stack) {
		ThaumcraftDataComponents.AssembledFocus assembled = stack.get(ThaumcraftDataComponents.ASSEMBLED_FOCUS.get());
		if (assembled != null) {
			return assembled.focusPackage();
		}
		FocusPackage p = new FocusPackage();
		for (IFocusElement node : createNodes()) {
			p.addNode(node);
		}
		return p;
	}

	/** The original's own getFocusColor: the average of the effect elements' colours, white when the focus casts
	 * nothing (an unassembled blank). Tints the focus item and the caster's focus gem. The original cached the
	 * result in the stack's NBT; recomputed here, since item components can't be written from a render call. */
	public int getFocusColor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0xFFFFFFFF;
		FocusPackage core = getPackage(stack);
		thaumcraft.api.casters.FocusEffect[] effects = core != null ? core.getFocusEffects() : null;
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
		ThaumcraftDataComponents.AssembledFocus assembled = stack.get(ThaumcraftDataComponents.ASSEMBLED_FOCUS.get());
		if (assembled != null) {
			return assembled.focusPackage().getComplexity();
		}
		int total = 0;
		for (IFocusElement node : createNodes()) {
			if (node instanceof FocusNode focusNode) total += focusNode.getComplexity();
		}
		return total;
	}

	public float getVisCost(ItemStack stack) {
		return getComplexity(stack) / 5.0f;
	}

	public int getActivationTime(ItemStack stack) {
		int complexity = getComplexity(stack);
		return Math.max(5, complexity / 5 * (complexity / 4));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.thaumcraft.focus.cost", String.format("%.1f", getVisCost(stack))));
		for (IFocusElement node : getPackage(stack).nodes) {
			if (node instanceof FocusNode focusNode) {
				StringBuilder line = new StringBuilder(Component.translatable(focusNode.getUnlocalizedName()).getString());
				if (!focusNode.getSettingList().isEmpty()) {
					line.append(" [");
					boolean first = true;
					for (String key : focusNode.getSettingList()) {
						NodeSetting setting = focusNode.getSetting(key);
						if (!first) line.append(", ");
						line.append(setting.getLocalizedName().getString()).append(' ').append(setting.getValueText().getString());
						first = false;
					}
					line.append(']');
				}
				tooltip.add(Component.literal(line.toString()));
			}
		}
	}
}
