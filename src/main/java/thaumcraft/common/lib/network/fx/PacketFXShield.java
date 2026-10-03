package thaumcraft.common.lib.network.fx;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * The original's PacketFXShield: a shield flashing on entity {@code source} where it took a blow from entity
 * {@code target} (-1 no attacker, -2 a fall, -3 a falling block) - sent when runic shielding or a warded/eldritch
 * mob's absorption soaks up damage. Drawn by {@code thaumcraft.client.render.ShieldRunesFx}.
 */
public record PacketFXShield(int source, int target) implements CustomPacketPayload {

	public static final Type<PacketFXShield> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fx_shield"));

	public static final StreamCodec<RegistryFriendlyByteBuf, PacketFXShield> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PacketFXShield::source,
			ByteBufCodecs.VAR_INT, PacketFXShield::target,
			PacketFXShield::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** The original's target code for a damage source. */
	public static int targetOf(net.minecraft.world.damagesource.DamageSource source) {
		int target = source.getEntity() != null ? source.getEntity().getId() : -1;
		if (source.is(net.minecraft.world.damagesource.DamageTypes.FALL)) target = -2;
		if (source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK) || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL)) target = -3;
		return target;
	}

	public static void send(net.minecraft.world.entity.Entity entity, net.minecraft.world.damagesource.DamageSource source) {
		if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel level)) return;
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(level, null, entity.getX(), entity.getY(), entity.getZ(), 32.0,
					new PacketFXShield(entity.getId(), targetOf(source)));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Shield FX packet failed to send", e);
		}
	}
}
