package thaumcraft.client.lib.events;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.WornGearHelper;

/**
 * The Cloud Ring's double jump (the original's {@code ItemCloudRing#onWornTick}): pressing jump again in mid-air (not
 * in water), once per time off the ground, throws the player up with a puff and a whoosh - 0.75 upward (more with
 * Jump Boost), a little forward when sprinting - and clears their fall.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class CloudRingJump {

	/** The original's jumpList: absent - on the ground; true - a double jump is ready; false - spent. */
	private static Boolean ready;

	private CloudRingJump() {
	}

	private static boolean wearing(LocalPlayer player) {
		for (ItemStack stack : WornGearHelper.getWornItems(player)) {
			if (stack.is(ItemsTC.cloudRing.get())) return true;
		}
		return false;
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || !wearing(player)) {
			ready = null;
			return;
		}
		boolean pressed = mc.screen == null && mc.options.keyJump.consumeClick();
		if (pressed && ready == null) ready = true;
		if (pressed && !player.onGround() && !player.isInWater() && player.noJumpDelay == 0 && Boolean.TRUE.equals(ready)) {
			thaumcraft.client.fx.ClientFxHandlers.drawBamf(mc.level, player.getX(), player.getY() + 0.5, player.getZ(), 1.0f, 1.0f, 1.0f, false, false, Direction.UP);
			mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.1f,
					1.0f + (float) mc.level.random.nextGaussian() * 0.05f, false);
			ready = false;
			double my = 0.75;
			if (player.hasEffect(MobEffects.JUMP)) my += (player.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 0.1f;
			double mx = player.getDeltaMovement().x;
			double mz = player.getDeltaMovement().z;
			if (player.isSprinting()) {
				float f = player.getYRot() * Mth.DEG_TO_RAD;
				mx -= Mth.sin(f) * 0.2f;
				mz += Mth.cos(f) * 0.2f;
			}
			player.setDeltaMovement(mx, my, mz);
			player.resetFallDistance();
			PacketDistributor.sendToServer(new thaumcraft.common.lib.network.ServerboundPlayerFlagPacket(1));
			net.neoforged.neoforge.common.CommonHooks.onLivingJump(player);
		}
		if (player.onGround() && player.noJumpDelay == 0) ready = null;
	}
}
