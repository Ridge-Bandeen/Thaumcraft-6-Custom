package thaumcraft.common.items.casters.foci;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusMedium;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.entities.monster.EntitySpellBat;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code FocusMediumSpellBat} - spawns a real {@link EntitySpellBat} to carry the
 * remainder of this focus package to a target, real translation of the original's own
 * {@code new EntitySpellBat(pac, friendly)} construction via the modern {@code EntityType}-based
 * {@link EntitySpellBat#configureFocus} secondary-setup step (see that class's own doc comment for why).
 */
public class FocusMediumSpellBat extends FocusMedium {

	@Override
	public String getResearch() {
		return "FOCUSSPELLBAT";
	}

	@Override
	public String getKey() {
		return "thaumcraft.SPELLBAT";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.BEAST;
	}

	@Override
	public int getComplexity() {
		return 8;
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TARGET };
	}

	@Override
	public boolean execute(Trajectory trajectory) {
		EntitySpellBat bat = ThaumcraftEntities.SPELL_BAT.get().create(getPackage().world);
		if (bat == null) return false;
		bat.moveTo(trajectory.source.x, trajectory.source.y, trajectory.source.z, 0.0f, 0.0f);
		bat.configureFocus(getRemainingPackage(), getSettingValue("target") == 1);
		return getPackage().world.addFreshEntity(bat);
	}

	@Override
	public boolean hasIntermediary() {
		return true;
	}

	@Override
	public float getPowerMultiplier() {
		return 0.33f;
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] friend = { 0, 1 };
		String[] friendDesc = { "focus.common.enemy", "focus.common.friend" };
		return new NodeSetting[] { new NodeSetting("target", "focus.common.target", new NodeSetting.NodeSettingIntList(friend, friendDesc)) };
	}
}
