package thaumcraft.api.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on {@code NeoForge.EVENT_BUS} when a Lamp of Fertility is about to put two animals of a kind in love, as the
 * original. Cancel it to skip that - a handler that breeds them its own way cancels it after doing so.
 * {@link #setCost} sets the lamp charges spent (5 by default; 0 to pass over the pair at no cost).
 */
public class LampBreedEvent extends Event implements ICancellableEvent {

	private final ServerLevel level;
	private final BlockPos lampPos;
	private final Animal first;
	private final Animal second;
	private int cost = 5;

	public LampBreedEvent(ServerLevel level, BlockPos lampPos, Animal first, Animal second) {
		this.level = level;
		this.lampPos = lampPos;
		this.first = first;
		this.second = second;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public BlockPos getLampPos() {
		return lampPos;
	}

	public Animal getFirst() {
		return first;
	}

	public Animal getSecond() {
		return second;
	}

	public int getCost() {
		return cost;
	}

	public void setCost(int cost) {
		this.cost = cost;
	}
}
