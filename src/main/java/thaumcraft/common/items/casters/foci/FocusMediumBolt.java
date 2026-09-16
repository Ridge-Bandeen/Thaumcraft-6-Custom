package thaumcraft.common.items.casters.foci;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusMediumBolt}. The original subclassed {@code FocusMediumTouch}
 * only to widen its range (5 -> 16 blocks, a real ranged attack rather than melee) and to broadcast a real
 * "zap" network FX packet on execute() - the aiming/targeting logic itself was identical, reused as-is.
 * Our ported {@link FocusMediumTouch#getRange()} was already written as an overridable hook for exactly
 * this, so this class is just that override plus the metadata (research/key/complexity/aspect) plus the
 * real FX broadcast, restored 2026-09-15 now that {@link thaumcraft.client.fx.beams.FXBolt}/{@code
 * PacketFXZap} are real - reuses {@link FocusMediumTouch#trace} (widened to {@code protected} for exactly
 * this) rather than duplicating the raytrace.
 */
public class FocusMediumBolt extends FocusMediumTouch {

	@Override
	public String getResearch() {
		return "FOCUSBOLT";
	}

	@Override
	public String getKey() {
		return "thaumcraft.BOLT";
	}

	@Override
	public int getComplexity() {
		return 5;
	}

	@Override
	public Aspect getAspect() {
		return Aspect.ENERGY;
	}

	@Override
	protected double getRange() {
		return 16.0;
	}

	/** Real {@code PacketFXZap} broadcast, matching the true original's own real shape exactly: the bolt's
	 * own colour is the real average RGB across every real element icon colour in the cast package (not any
	 * single effect's own aspect colour), and its width scales with the real cast power. */
	@Override
	public boolean execute(Trajectory trajectory) {
		HitResult hit = trace(trajectory);
		Vec3 end = hit != null ? hit.getLocation() : trajectory.source.add(trajectory.direction.normalize().scale(getRange()));

		FocusEffect[] effects = getPackage().getFocusEffects();
		int r = 0, g = 0, b = 0;
		for (FocusEffect ef : effects) {
			int color = FocusEngine.getElementColor(ef.getKey());
			r += (color >> 16) & 0xFF;
			g += (color >> 8) & 0xFF;
			b += color & 0xFF;
		}
		if (effects.length > 0) {
			r /= effects.length;
			g /= effects.length;
			b /= effects.length;
		}

		if (getPackage().world instanceof ServerLevel serverLevel) {
			try {
				PacketDistributor.sendToPlayersNear(serverLevel, null,
						trajectory.source.x, trajectory.source.y, trajectory.source.z, 64.0,
						thaumcraft.common.lib.network.fx.PacketFXZap.of(trajectory.source, end,
								(r << 16) | (g << 8) | b, getPackage().getPower() * 0.66f));
			} catch (Exception e) {
				Thaumcraft.LOGGER.debug("Bolt focus zap FX packet failed to send (no real client connection?)", e);
			}
		}
		return true;
	}
}
