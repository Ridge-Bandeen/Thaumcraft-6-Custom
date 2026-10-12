package thaumcraft.api.casters;

import net.minecraft.world.item.ItemStack;

/**
 * Changes how strong a focus's effects are, judged from the focus stack (its data components, say). Register with
 * {@code ThaumcraftApi#addFocusPowerModifier}. Each is asked once per effect when a caster casts the focus, with the
 * power so far (1 to begin with, then whatever the previous modifier returned); the result multiplies that effect's
 * power on every hit of the cast, after the focus's own mediums and modifiers. Return {@code power} to leave it alone.
 */
@FunctionalInterface
public interface FocusPowerModifier {

	float modify(ItemStack focus, FocusEffect effect, float power);
}
