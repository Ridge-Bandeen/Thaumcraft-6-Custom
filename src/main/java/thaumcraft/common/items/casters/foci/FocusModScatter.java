package thaumcraft.common.items.casters.foci;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.phys.Vec3;
import thaumcraft.api.casters.FocusMod;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusModScatter}. Fans the parent node's own trajectories out into
 * {@code forks} copies each, randomly jittered within a {@code cone} angle (real translation of the
 * original's own gaussian-jitter-per-axis approach - not a true cone, same real simplification the original
 * itself already had). Real translation of {@code Vec3d} -> modern {@link Vec3} (immutable, so
 * {@code addVector}/{@code normalize} become real {@code add}/{@code normalize} calls returning new
 * instances rather than in-place mutation).
 */
public class FocusModScatter extends FocusMod {

	@Override
	public String getResearch() {
		return "FOCUSSCATTER";
	}

	@Override
	public String getKey() {
		return "thaumcraft.SCATTER";
	}

	@Override
	public int getComplexity() {
		return (int) Math.max(2.0f, 2.0f * (getSettingValue("forks") - getSettingValue("cone") / 45.0f));
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] angles = { 10, 30, 60, 90, 180, 270, 360 };
		String[] anglesDesc = { "10", "30", "60", "90", "180", "270", "360" };
		return new NodeSetting[] {
				new NodeSetting("forks", "focus.scatter.forks", new NodeSetting.NodeSettingIntRange(2, 10)),
				new NodeSetting("cone", "focus.scatter.cone", new NodeSetting.NodeSettingIntList(angles, anglesDesc)),
		};
	}

	@Override
	public EnumSupplyType[] mustBeSupplied() {
		return new EnumSupplyType[] { EnumSupplyType.TRAJECTORY };
	}

	@Override
	public EnumSupplyType[] willSupply() {
		return new EnumSupplyType[] { EnumSupplyType.TRAJECTORY };
	}

	@Override
	public Trajectory[] supplyTrajectories() {
		if (getParent() == null) {
			return new Trajectory[0];
		}
		List<Trajectory> out = new ArrayList<>();
		int forks = getSettingValue("forks");
		int angle = getSettingValue("cone");
		Trajectory[] parentTrajectories = getParent().supplyTrajectories();
		if (parentTrajectories != null) {
			for (Trajectory source : parentTrajectories) {
				for (int a = 0; a < forks; a++) {
					Vec3 dir = source.direction.normalize();
					dir = dir.add(getPackage().world.random.nextGaussian() * 0.0075 * angle,
							getPackage().world.random.nextGaussian() * 0.0075 * angle,
							getPackage().world.random.nextGaussian() * 0.0075 * angle);
					out.add(new Trajectory(source.source, dir.normalize()));
				}
			}
		}
		return out.toArray(new Trajectory[0]);
	}

	@Override
	public float getPowerMultiplier() {
		return 1.0f / (getSettingValue("forks") / 2.0f);
	}

	@Override
	public boolean execute() {
		return true;
	}

	@Override
	public boolean isExclusive() {
		return true;
	}
}
