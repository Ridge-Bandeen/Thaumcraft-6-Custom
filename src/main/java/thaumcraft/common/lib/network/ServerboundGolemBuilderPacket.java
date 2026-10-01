package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.Thaumcraft;
import thaumcraft.common.container.GolemBuilderMenu;

/**
 * The Golem Press screen's messages to its tile, as the original's NBT messages: the golem the screen currently shows
 * (packed {@code GolemProperties} id), either to ask which components the press can find beside it ({@code craft}
 * false - the original's "check") or to start building it. The server only acts on the press the player has open.
 */
public record ServerboundGolemBuilderPacket(long golem, boolean craft) implements CustomPacketPayload {

	public static final Type<ServerboundGolemBuilderPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "golem_builder"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundGolemBuilderPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, ServerboundGolemBuilderPacket::golem,
			ByteBufCodecs.BOOL, ServerboundGolemBuilderPacket::craft,
			ServerboundGolemBuilderPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		if (!(player.containerMenu instanceof GolemBuilderMenu menu) || !menu.stillValid(player)) return;
		if (craft) {
			menu.craft(player, golem);
		} else {
			menu.check(golem);
		}
	}
}
