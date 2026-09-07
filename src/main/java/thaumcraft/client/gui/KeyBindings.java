package thaumcraft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/**
 * Real modern {@link KeyMapping} registration - replaces the old (1.12.2) {@code KeyHandler}'s own
 * {@code keyF}/{@code keyG} {@code KeyBinding}s. Only {@code keyF} ("Change Caster Focus", opening
 * {@link FocusRadialOverlay}) is ported; the original's {@code keyG} ("Misc Caster Toggle") drove the Golem
 * Bell's key-toggle interaction, already replaced port-wide by a real sneak-right-click (see
 * {@code ItemGolemBell}'s own doc comment) - there's nothing left for a second keybind to do.
 */
public final class KeyBindings {

	public static final KeyMapping CHANGE_CASTER_FOCUS = new KeyMapping(
			"key.thaumcraft.change_focus", InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F, "key.categories.thaumcraft");

	private KeyBindings() {
	}
}
