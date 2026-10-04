package thaumcraft.api.events;

import javax.annotation.Nullable;

import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISealEntity;

/**
 * Posted on {@code NeoForge.EVENT_BUS} for an animal a Butcher seal would cull (an adult, untamed, non-hostile animal of
 * a kind there are more than two of, as the original). Cancel it to spare the animal. Posted when the seal picks its
 * targets ({@link #getGolem} is null then) and again when a golem takes the job.
 */
public class GolemButcherTargetEvent extends Event implements ICancellableEvent {

	private final ISealEntity seal;
	@Nullable
	private final IGolemAPI golem;
	private final LivingEntity target;

	public GolemButcherTargetEvent(ISealEntity seal, @Nullable IGolemAPI golem, LivingEntity target) {
		this.seal = seal;
		this.golem = golem;
		this.target = target;
	}

	public ISealEntity getSeal() {
		return seal;
	}

	@Nullable
	public IGolemAPI getGolem() {
		return golem;
	}

	public LivingEntity getTarget() {
		return target;
	}
}
