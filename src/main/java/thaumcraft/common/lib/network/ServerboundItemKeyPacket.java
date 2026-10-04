package thaumcraft.common.lib.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import thaumcraft.Thaumcraft;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.items.casters.ItemCaster;

/**
 * The old (1.12.2) {@code PacketItemKeyToServer}: the misc caster key (G) was pressed - {@code key} 1, {@code mod} 1 with
 * Ctrl held, 2 with Shift. As the original it changes a held caster's Plan area ({@link ItemCaster#toggleMisc}) or turns
 * a held Elemental Shovel's placement pattern to the next of its three.
 */
public record ServerboundItemKeyPacket(int key, int mod) implements CustomPacketPayload {

	public static final Type<ServerboundItemKeyPacket> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "item_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundItemKeyPacket> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ServerboundItemKeyPacket::key, ByteBufCodecs.VAR_INT, ServerboundItemKeyPacket::mod, ServerboundItemKeyPacket::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public void handle(ServerPlayer player) {
		if (key != 1) return;
		ItemStack main = player.getMainHandItem();
		if (main.getItem() instanceof ICaster) {
			ItemCaster.toggleMisc(main, mod);
		} else if (player.getOffhandItem().getItem() instanceof ICaster) {
			ItemCaster.toggleMisc(player.getOffhandItem(), mod);
		}
		if (main.getItem() instanceof thaumcraft.common.items.tools.ItemElementalShovel) {
			int o = main.getOrDefault(ThaumcraftDataComponents.SHOVEL_ORIENTATION.get(), 0);
			main.set(ThaumcraftDataComponents.SHOVEL_ORIENTATION.get(), (o + 1) % 3);
		}
	}
}
