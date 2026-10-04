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
 * Replaces the old (1.12.2) {@code FocusMediumProjectile}: launches an {@link EntityFocusProjectile} carrying the rest of
 * the spell, which runs when it hits. As the original it has a speed (1-5) and an option - none, bouncy, or seeking
 * hostile/friendly (the options need FOCUSPROJECTILE) - which add to its complexity.
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
		int c = 4 + (getSettingValue("speed") - 1) / 2;
		switch (getSettingValue("option")) {
			case 1 -> c += 3;
			case 2, 3 -> c += 5;
			default -> {
			}
		}
		return c;
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

		EntityFocusProjectile projectile = new EntityFocusProjectile(caster, remaining, trajectory.source, trajectory.direction, speed, getSettingValue("option"));
		return caster.level().addFreshEntity(projectile);
	}

	@Override
	public boolean hasIntermediary() {
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		return new NodeSetting[] {
				new NodeSetting("option", "focus.common.options", new NodeSetting.NodeSettingIntList(new int[] { 0, 1, 2, 3 },
						new String[] { "focus.common.none", "focus.projectile.bouncy", "focus.projectile.seeking.hostile", "focus.projectile.seeking.friendly" }), "FOCUSPROJECTILE"),
				new NodeSetting("speed", "focus.projectile.speed", new NodeSetting.NodeSettingIntRange(1, 5)) };
	}

	@Override
	public Aspect getAspect() {
		return Aspect.MOTION;
	}
}
