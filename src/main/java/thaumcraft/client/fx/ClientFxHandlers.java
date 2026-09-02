package thaumcraft.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

/**
 * Client-side handlers for {@code thaumcraft.common.lib.network.fx}'s payloads - referenced only by method
 * reference from {@code Thaumcraft#registerPayloads} (common code, runs on both sides), never called
 * directly from there. Safe on a dedicated server because a {@code playToClient}-registered handler is only
 * ever invoked on the receiving (client) side - registering it is a common concern (both sides need the
 * payload's codec to en/decode it), actually running it isn't. See that method's own doc comment.
 *
 * Every effect here is built from plain vanilla particles/sounds, not the original's custom
 * {@code FXDispatcher}/{@code ParticleEngine} renderer pipeline - the same boundary this whole port has kept
 * since Cinderpearl's own client FX, now extended to cover networked effects too.
 */
public final class ClientFxHandlers {

	private ClientFxHandlers() {
	}

	/** {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#ARCING}'s cleave hit - the original drew a textured slash quad between
	 * the two entities ({@code FXDispatcher.drawSlash}); this spawns a small burst of vanilla's own sweep-attack
	 * particle at the target instead, the same particle a vanilla sword sweep itself uses. */
	public static void handleSlash(PacketFXSlash payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Entity target = resolveEntity(mc, level, payload.target());
		if (target == null) {
			return;
		}
		double x = target.getX();
		double y = target.getBoundingBox().minY + target.getBbHeight() / 2.0;
		double z = target.getZ();
		for (int i = 0; i < 4; i++) {
			level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0.0, 0.0, 0.0);
		}
	}

	private static Entity resolveEntity(Minecraft mc, ClientLevel level, int entityId) {
		return mc.player != null && mc.player.getId() == entityId ? mc.player : level.getEntity(entityId);
	}
}
