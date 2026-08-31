package thaumcraft.common.lib.research;

import java.util.EnumMap;
import java.util.Map;

import javax.annotation.Nonnull;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import thaumcraft.api.capabilities.IPlayerWarp;

/**
 * The concrete {@link IPlayerWarp} implementation - see {@link PlayerKnowledge}'s doc for the general
 * story (this class didn't exist in the ported API either). {@link #sync} is a no-op for the same reason.
 */
public class PlayerWarp implements IPlayerWarp {

	private final Map<EnumWarpType, Integer> warp = new EnumMap<>(EnumWarpType.class);
	private int counter;

	@Override
	public void clear() {
		warp.clear();
		counter = 0;
	}

	@Override
	public int get(@Nonnull EnumWarpType type) {
		return warp.getOrDefault(type, 0);
	}

	@Override
	public void set(@Nonnull EnumWarpType type, int amount) {
		warp.put(type, Math.max(0, amount));
	}

	@Override
	public int add(@Nonnull EnumWarpType type, int amount) {
		int now = Math.max(0, get(type) + amount);
		warp.put(type, now);
		return now;
	}

	@Override
	public int reduce(@Nonnull EnumWarpType type, int amount) {
		return add(type, -amount);
	}

	@Override
	public void sync(ServerPlayer player) {
		// TODO: needs the not-yet-ported network layer to push this to the client.
	}

	@Override
	public int getCounter() {
		return counter;
	}

	@Override
	public void setCounter(int amount) {
		counter = amount;
	}

	@Nonnull
	@Override
	public CompoundTag serializeNBT(HolderLookup.Provider provider) {
		CompoundTag tag = new CompoundTag();
		for (Map.Entry<EnumWarpType, Integer> e : warp.entrySet()) {
			tag.putInt(e.getKey().name(), e.getValue());
		}
		tag.putInt("counter", counter);
		return tag;
	}

	@Override
	public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
		clear();
		for (EnumWarpType type : EnumWarpType.values()) {
			if (tag.contains(type.name())) {
				warp.put(type, tag.getInt(type.name()));
			}
		}
		counter = tag.getInt("counter");
	}
}
