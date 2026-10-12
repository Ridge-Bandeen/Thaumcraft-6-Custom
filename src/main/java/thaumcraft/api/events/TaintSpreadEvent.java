package thaumcraft.api.events;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posted on {@code NeoForge.EVENT_BUS} just before taint converts a block: fibres growing, logs, crust, soil and rock
 * turning, a growth sprouting on leaves, flux goo settling into fibres, and taint mobs laying fibres. Cancel it to leave
 * the block as it is. {@link #getFrom} is the taint (or the mob) doing the spreading, {@link #getTo} the block being
 * converted and {@link #getInto} what it would become. Taint dying back to stone, dirt or goo is not spreading and
 * posts nothing.
 */
public class TaintSpreadEvent extends Event implements ICancellableEvent {

	private final Level level;
	private final BlockPos from;
	private final BlockPos to;
	private final BlockState into;

	public TaintSpreadEvent(Level level, BlockPos from, BlockPos to, BlockState into) {
		this.level = level;
		this.from = from;
		this.to = to;
		this.into = into;
	}

	public Level getLevel() {
		return level;
	}

	public BlockPos getFrom() {
		return from;
	}

	public BlockPos getTo() {
		return to;
	}

	public BlockState getInto() {
		return into;
	}

	/** Posts the event; whether the conversion may go ahead. */
	public static boolean allows(Level level, BlockPos from, BlockPos to, BlockState into) {
		return !NeoForge.EVENT_BUS.post(new TaintSpreadEvent(level, from.immutable(), to.immutable(), into)).isCanceled();
	}
}
