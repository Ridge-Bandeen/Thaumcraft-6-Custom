package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.LivingEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.projectile.EntityFocusCloud;

/**
 * Replaces the old (1.12.2) {@code FocusMediumCloud}: leaves an {@link EntityFocusCloud} holding the rest of the spell,
 * with a radius (1-3) and a duration (5-30 seconds), as the original.
 */
public class FocusMediumCloud extends FocusMedium {

	@Override
	public String getResearch() {
		return "FOCUSCLOUD";
	}

	@Override
	public String getKey() {
		return "thaumcraft.CLOUD";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.ALCHEMY;
	}

	@Override
	public int getComplexity() {
		return 4 + getSettingValue("radius") * 2 + getSettingValue("duration") / 5;
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TARGET };
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		FocusPackage remaining = getRemainingPackage();
		if (remaining == null) return false;

		LivingEntity caster = getPackage().getCaster();
		if (caster == null) return false;

		EntityFocusCloud cloud = new EntityFocusCloud(caster, remaining, trajectory, getSettingValue("radius"), getSettingValue("duration"));
		return caster.level().addFreshEntity(cloud);
	}

	@Override
	public boolean hasIntermediary() {
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("radius", "focus.common.radius", new NodeSetting.NodeSettingIntRange(1, 3)),
				new NodeSetting("duration", "focus.common.duration", new NodeSetting.NodeSettingIntRange(5, 30))
		};
	}

	@Override
	public float getPowerMultiplier() {
		return 0.5f;
	}
}
