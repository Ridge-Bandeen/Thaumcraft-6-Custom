package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.Thaumcraft;

/**
 * Real port of the old (1.12.2) {@code PacketFocusChangeToServer} - sent by the real focus-radial HUD (see
 * {@code FocusRadialOverlay}'s own doc comment) when the player selects a carried focus to bind to their
 * held caster. Carries a real inventory slot index (the original carried a string "sorting helper" key and
 * re-derived the slot from it server-side to guard against a stale/moved item - simplified here to send the
 * slot directly, matching {@code ServerboundSaveFocusGraphPacket}'s own "the server still only ever trusts
 * its own state, never blindly applies whatever the client claims" real safety property: an out-of-range or
 * no-longer-a-focus slot is simply a no-op server-side, see {@code CasterFocusHelper#changeFocus}). {@code
 * slot < 0} is the original's own "REMOVE" case - unbind without binding a replacement.
 */
public record ServerboundChangeCasterFocusPacket(int slot) implements CustomPacketPayload {

	public static final Type<ServerboundChangeCasterFocusPacket> TYPE =
			new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "change_caster_focus"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundChangeCasterFocusPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ServerboundChangeCasterFocusPacket::slot,
			ServerboundChangeCasterFocusPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
