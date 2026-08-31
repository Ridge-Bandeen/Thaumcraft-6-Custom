package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;

/**
 * Replaces the old (1.12.2) {@code FocusMediumProjectile} - the wand's first entity-backed medium instead
 * of an instant raytrace (see {@link EntityFocusProjectile}'s doc comment for what's simplified: no bounce/
 * homing "option" setting this pass, straight-flying only). {@code hasIntermediary()} returning true here
 * is what tells {@code FocusEngine#runFocusPackage} to stop walking the node chain immediately after
 * {@code execute()} - the rest of the chain (the effect nodes after this one) only runs later, when the
 * spawned entity actually hits something.
 */
public class FocusMediumProjectile extends FocusMedium {

	@Override
	public String getResearch() {
		return "FOCUSPROJECTILE@2";
	}

	@Override
	public String getKey() {
		return "thaumcraft.PROJECTILE";
	}

	@Override
	public int getComplexity() {
		return 4 + (getSettingValue("speed") - 1) / 2;
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TARGET, EnumSupplyType.TRAJECTORY };
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		float speed = getSettingValue("speed") / 3.0f;
		FocusPackage remaining = getRemainingPackage();
		if (remaining == null) return false;

		LivingEntity caster = getPackage().getCaster();
		if (caster == null) return false;

		EntityFocusProjectile projectile = new EntityFocusProjectile(caster, remaining, trajectory.source, trajectory.direction, speed);
		return caster.level().addFreshEntity(projectile);
	}

	@Override
	public boolean hasIntermediary() {
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] { new NodeSetting("speed", "focus.projectile.speed", new NodeSetting.NodeSettingIntRange(1, 5)) };
	}

	@Override
	public Aspect getAspect() {
		return Aspect.MOTION;
	}
}
