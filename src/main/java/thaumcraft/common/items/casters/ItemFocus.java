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

/**
 * Replaces the old (1.12.2) {@code ItemFocus}. The original stored an arbitrary, player-assembled node
 * list (built at a Focal Manipulator, not ported) in NBT; this simplified version hardcodes a fixed node
 * list per subclass instead - see {@link ItemFocusFire} - since there's no focus-assembly UI yet. The
 * settings-driven complexity/vis-cost math is unchanged from the original.
 */
public abstract class ItemFocus extends Item {

	public ItemFocus(Properties properties) {
		super(properties.stacksTo(1));
	}

	/** The fixed medium+effect node chain this focus casts. Order matters (medium(s) first, then effect(s)). */
	protected abstract List<IFocusElement> createNodes();

	public FocusPackage getPackage(ItemStack stack) {
		FocusPackage p = new FocusPackage();
		for (IFocusElement node : createNodes()) {
			p.addNode(node);
		}
		return p;
	}

	public int getComplexity(ItemStack stack) {
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
		for (IFocusElement node : createNodes()) {
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
