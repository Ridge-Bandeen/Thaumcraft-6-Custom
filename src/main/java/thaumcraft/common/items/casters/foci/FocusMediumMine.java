package thaumcraft.common.items.casters.foci;

import net.minecraft.world.entity.LivingEntity;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.projectile.EntityFocusMine;

/**
 * Replaces the old (1.12.2) {@code FocusMediumMine} - a "trap" sibling to
 * {@link FocusMediumProjectile}, backed by {@link EntityFocusMine} instead of
 * {@link thaumcraft.common.entities.projectile.EntityFocusProjectile}. See that class's doc comment for
 * what's simplified about the arming/triggering behavior this pass.
 */
public class FocusMediumMine extends FocusMedium {

	@Override
	public String getResearch() {
		return "FOCUSMINE";
	}

	@Override
	public String getKey() {
		return "thaumcraft.MINE";
	}

	@Override
	public int getComplexity() {
		return 4;
	}

	@Override
	public Aspect getAspect() {
		return Aspect.TRAP;
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TARGET, EnumSupplyType.TRAJECTORY };
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		FocusPackage remaining = getRemainingPackage();
		if (remaining == null) return false;

		LivingEntity caster = getPackage().getCaster();
		if (caster == null) return false;

		boolean friendlyTarget = getSettingValue("target") == 1;
		EntityFocusMine mine = new EntityFocusMine(caster, remaining, trajectory, friendlyTarget);
		return caster.level().addFreshEntity(mine);
	}

	@Override
	public boolean hasIntermediary() {
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] friend = { 0, 1 };
		String[] friendDesc = { "focus.common.enemy", "focus.common.friend" };
		return new NodeSetting[] { new NodeSetting("target", "focus.common.target", new NodeSetting.NodeSettingIntList(friend, friendDesc)) };
	}
}
