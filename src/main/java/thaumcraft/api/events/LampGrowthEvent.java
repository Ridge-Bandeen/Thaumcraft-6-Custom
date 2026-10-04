package thaumcraft.api.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Posted on {@code NeoForge.EVENT_BUS} when a Lamp of Growth is about to advance a plant (by giving it a random tick,
 * as the original). Cancel it to skip that random tick - a handler that grows the plant its own way cancels it after
 * doing so. {@link #setCost} sets the lamp charges spent (1 by default; 0 for a plant the handler leaves alone).
 */
public class LampGrowthEvent extends Event implements ICancellableEvent {

	private final ServerLevel level;
	private final BlockPos lampPos;
	private final BlockPos plantPos;
	private final BlockState plant;
	private int cost = 1;

	public LampGrowthEvent(ServerLevel level, BlockPos lampPos, BlockPos plantPos, BlockState plant) {
		this.level = level;
		this.lampPos = lampPos;
		this.plantPos = plantPos;
		this.plant = plant;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public BlockPos getLampPos() {
		return lampPos;
	}

	public BlockPos getPlantPos() {
		return plantPos;
	}

	public BlockState getPlant() {
		return plant;
	}

	public int getCost() {
		return cost;
	}

	public void setCost(int cost) {
		this.cost = cost;
	}
}
