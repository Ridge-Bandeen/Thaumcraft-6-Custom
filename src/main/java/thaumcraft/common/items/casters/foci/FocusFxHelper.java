package thaumcraft.common.items.casters.foci;

import org.joml.Vector3f;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.common.lib.network.fx.PacketFXFocusPartImpact;

/**
 * Shared real {@code PacketFXFocusPartImpact} send helper for the 7 real {@link
 * thaumcraft.api.casters.FocusEffect} subclasses that self-trigger it directly from their own {@code
 * execute()} (Air/Break/Earth/Fire/Flux/Frost/Heal - see {@link PacketFXFocusPartImpact}'s own doc comment
 * for how this differs from the medium-level {@code PacketFXFocusEffect} broadcast). Package-private, not on
 * the shared {@code FocusEffect} API base class itself - that class lives in {@code thaumcraft.api.casters}
 * and shouldn't depend on {@code common.lib.network} internals; every real caller already lives in this same
 * common-side package, so a small shared helper here avoids both the layering concern and repeating the same
 * real try/catch boilerplate 7 times.
 */
final class FocusFxHelper {

	private FocusFxHelper() {
	}

	static void sendPartImpact(Level level, Vec3 pos, String key) {
		if (!(level instanceof ServerLevel serverLevel)) return;
		try {
			PacketDistributor.sendToPlayersNear(serverLevel, null, pos.x, pos.y, pos.z, 64.0,
					new PacketFXFocusPartImpact(new Vector3f((float) pos.x, (float) pos.y, (float) pos.z), java.util.List.of(key)));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Focus part impact FX packet failed to send (no real client connection?)", e);
		}
	}
}
