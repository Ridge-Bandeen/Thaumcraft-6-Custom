package thaumcraft.common.lib.network;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.common.container.LogisticsMenu;

/**
 * The two client-to-server messages of the Golem Logistics screen (ports of the old {@code PacketMiscStringToServer}
 * search message and {@code PacketLogisticsRequestToServer}). Both only act when the sender really has a
 * {@link LogisticsMenu} open.
 */
public final class ServerboundLogisticsPackets {

	private ServerboundLogisticsPackets() {
	}

	/** The search box's text changed - the list is rebuilt around it. */
	public record Search(String text) implements CustomPacketPayload {
		public static final Type<Search> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "logistics_search"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Search> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Search::text, Search::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public void handle(ServerPlayer player) {
			if (player.containerMenu instanceof LogisticsMenu menu) {
				menu.setSearchText(text.length() > 32 ? text.substring(0, 32) : text);
			}
		}
	}

	/** "Request": have golems fetch {@code count} of {@code stack} to the menu's target (a block face, or the player
	 * when the bell was used in the air). Sent as stack-sized requests exactly like the original, each with a
	 * distinct request number so identical ones don't collapse into one. */
	public record Request(Optional<BlockPos> pos, Direction side, ItemStack stack, int count) implements CustomPacketPayload {
		public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "logistics_request"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Request> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Request::pos,
				Direction.STREAM_CODEC, Request::side,
				ItemStack.STREAM_CODEC, Request::stack,
				ByteBufCodecs.VAR_INT, Request::count,
				Request::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		public void handle(ServerPlayer player) {
			if (!(player.containerMenu instanceof LogisticsMenu) || stack.isEmpty()) return;
			int remaining = Math.min(count, 4096);
			int ui = 0;
			while (remaining > 0) {
				ItemStack part = stack.copy();
				part.setCount(Math.min(remaining, part.getMaxStackSize()));
				remaining -= part.getCount();
				if (pos.isPresent()) {
					GolemHelper.requestProvisioning(player.level(), pos.get(), side, part, ui);
				} else {
					GolemHelper.requestProvisioning(player.level(), player, part, ui);
				}
				ui++;
			}
		}
	}
}
