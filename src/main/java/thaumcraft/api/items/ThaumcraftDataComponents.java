package thaumcraft.api.items;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.aspects.AspectList;

/**
 * ItemStacks no longer carry freeform NBT (that's an escape hatch now, the {@code CUSTOM_DATA} component -
 * see {@link thaumcraft.api.golems.ProvisionRequest} for a case where that shortcut was reasonable). For
 * something as central and long-lived as an essentia container's held aspects, a proper typed
 * {@link DataComponentType} is the idiomatic replacement for what {@code IEssentiaContainerItem}
 * implementations used to store as raw NBT: it's persisted, synced to the client, and shows up in
 * ItemStack equality/hashing for free.
 */
public final class ThaumcraftDataComponents {

	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "thaumcraft");

	/** The {@link AspectList} held by an essentia container item (crystals, phials, jar labels, etc). */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<AspectList>> ASPECTS = DATA_COMPONENTS.register("aspects",
			() -> DataComponentType.<AspectList>builder()
					.persistent(AspectList.CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(AspectList.CODEC))
					.build());

	/** Was the {@code "tc.charge"} NBT int tag on {@link thaumcraft.api.items.IRechargable} items. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE = DATA_COMPONENTS.register("charge",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	private ThaumcraftDataComponents() {
	}
}
