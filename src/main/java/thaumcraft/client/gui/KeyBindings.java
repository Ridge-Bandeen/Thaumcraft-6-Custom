package thaumcraft.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

/**
 * The original's {@code KeyHandler} keys: F ("Change Caster Focus": hold for {@link FocusRadialOverlay}, or with Shift
 * to take the focus out of the held caster) and G ("Misc Caster Toggle": a held caster's Plan area - Ctrl for the axis -
 * or the Elemental Shovel's pattern; see {@code ServerboundItemKeyPacket}).
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = thaumcraft.Thaumcraft.MODID, value = net.neoforged.api.distmarker.Dist.CLIENT)
public final class KeyBindings {

	public static final KeyMapping CHANGE_CASTER_FOCUS = new KeyMapping(
			"key.thaumcraft.change_focus", InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_F, "key.categories.thaumcraft");

	public static final KeyMapping MISC_CASTER_TOGGLE = new KeyMapping(
			"key.thaumcraft.misc_toggle", InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_G, "key.categories.thaumcraft");

	private static boolean pressedG;
	private static boolean pressedF;

	/** The original's playerTick key handling, sent once per press. */
	@net.neoforged.bus.api.SubscribeEvent
	static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) {
			pressedG = false;
			pressedF = false;
			return;
		}
		if (MISC_CASTER_TOGGLE.isDown()) {
			if (!pressedG) {
				int mod = net.minecraft.client.gui.screens.Screen.hasControlDown() ? 1 : net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 2 : 0;
				net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.common.lib.network.ServerboundItemKeyPacket(1, mod));
			}
			pressedG = true;
		} else {
			pressedG = false;
		}
		if (CHANGE_CASTER_FOCUS.isDown()) {
			boolean caster = mc.player.getMainHandItem().getItem() instanceof thaumcraft.api.casters.ICaster
					|| mc.player.getOffhandItem().getItem() instanceof thaumcraft.api.casters.ICaster;
			if (!pressedF && caster && mc.player.isShiftKeyDown()) {
				net.neoforged.neoforge.network.PacketDistributor.sendToServer(new thaumcraft.common.lib.network.ServerboundChangeCasterFocusPacket(-1));
			}
			pressedF = true;
		} else {
			pressedF = false;
		}
	}

	private KeyBindings() {
	}
}
