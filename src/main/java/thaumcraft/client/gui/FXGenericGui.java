package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code FXGenericGui}/{@code FXDispatcher#drawSimpleSparkleGui} - the true
 * original's own real GUI-space sparkle particle, restored 2026-09-24 as the last piece of the real
 * screen-space particle work this port's own {@link KnowledgeGainOverlay}/{@link GuiResearchTable} chunks
 * both deferred. A small standalone POJO ({@link Sparkle}), not a {@link
 * thaumcraft.client.fx.particles.FXGeneric} subclass the way the true original's own class hierarchy worked -
 * modern {@code FXGeneric} is now a real vanilla {@link net.minecraft.client.particle.Particle} subclass tied
 * to a {@link net.minecraft.client.multiplayer.ClientLevel}, and can't be reused as a GUI-space parent at all.
 *
 * <p>Real, faithful port of {@code FXGeneric}'s own already-established real animation formulas (evenly-
 * spaced keyframe interpolation for alpha/scale, {@code startParticle + age % numParticles} frame looping,
 * gravity/slowdown/random-jitter physics) applied to 2D screen coordinates instead of 3D world ones - ticked
 * via {@link #onClientTick} (matching the established {@link FocusRadialOverlay}/{@link KnowledgeGainOverlay}
 * tick-driven-state pattern) and rendered via {@link #onRenderGui} using the shared {@link GuiQuadRenderer}
 * technique. No camera-facing billboard needed (unlike the in-world case) - GUI space is already always
 * screen-facing.
 *
 * <p>Real, checked-not-assumed finding: the true original's own real {@code drawSimpleSparkleGui} has exactly
 * ONE genuinely live real caller in the whole pristine tree - {@code GuiResearchTable}'s own real percentage-
 * sidebar count-up cue. Its other apparent caller, {@code HudHandler}'s own knowledge-gain HUD ({@code
 * current.sparks}), is real, confirmed DEAD code in the true original itself (the field is declared, defaults
 * false, and is never once set true anywhere in the pristine tree) - see {@link KnowledgeGainOverlay}'s own
 * doc comment for the full finding. Only {@link GuiResearchTable} calls {@link #spawn} for real.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class FXGenericGui {

	private static final ResourceLocation PARTICLES = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int GRID = 64;

	/** Matches {@code FXGeneric}'s own real evenly-spaced keyframe interpolation exactly, evaluated per-frame
	 * instead of precomputed into a per-instance array. */
	private static final float[] ALPHA_KEYS = { 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.0f };
	private static final float[] SCALE_KEYS = { 1.0f, 2.0f };

	private static final List<Sparkle> ACTIVE = new ArrayList<>();

	private FXGenericGui() {
	}

	/** Real client-bound entry point, matching the true original's own real {@code
	 * FXDispatcher#drawSimpleSparkleGui} signature and body exactly: a real 1-in-5 chance of the alternate
	 * sprite loop ({@code sp}), a real 32-39 tick lifetime, a real 16-frame loop starting at frame 320 or 512
	 * on the shared {@code particles.png} sheet, a real fade-in/hold/fade-out alpha curve, and a real
	 * scale-doubles-over-lifetime grow curve. */
	public static void spawn(RandomSource random, double x, double y, double xd, double yd,
			float scale, float r, float g, float b, int delay, float decay, float gravity) {
		boolean sp = random.nextFloat() < 0.2f;
		Sparkle s = new Sparkle();
		s.x = x;
		s.y = y;
		s.xd = xd;
		s.yd = yd;
		s.lifetime = 32 + random.nextInt(8);
		s.delay = delay;
		s.r = r;
		s.g = g;
		s.b = b;
		s.startFrame = sp ? 320 : 512;
		s.baseScale = scale;
		s.decay = decay;
		s.gravity = gravity;
		ACTIVE.add(s);
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		if (ACTIVE.isEmpty()) return;
		RandomSource random = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.random : RandomSource.create();
		Iterator<Sparkle> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Sparkle s = it.next();
			if (s.delay > 0) {
				s.delay--;
				continue;
			}
			if (s.age++ >= s.lifetime) {
				it.remove();
				continue;
			}
			s.yd -= 0.04 * s.gravity;
			s.x += s.xd;
			s.y += s.yd;
			s.xd *= s.decay;
			s.yd *= s.decay;
			s.xd += random.nextGaussian() * 0.025;
			s.yd += random.nextGaussian() * 0.025;
		}
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		if (ACTIVE.isEmpty()) return;
		GuiGraphics graphics = event.getGuiGraphics();
		for (Sparkle s : ACTIVE) {
			if (s.delay > 0) continue;
			float fs = Mth.clamp(s.age / (float) s.lifetime, 0.0f, 1.0f);
			float alpha = keyframe(ALPHA_KEYS, s.age, s.lifetime);
			float scale = s.baseScale * keyframe(SCALE_KEYS, s.age, s.lifetime);
			int frame = s.startFrame + s.age % 16;

			graphics.pose().pushPose();
			graphics.pose().translate(s.x, s.y, 0);
			GuiQuadRenderer.renderQuadCentered(graphics, PARTICLES, GRID, frame, scale, s.r, s.g, s.b, alpha, true);
			graphics.pose().popPose();
		}
	}

	/** Matches {@code FXGeneric#calculateFrames}'s own real evenly-spaced linear-interpolation formula
	 * exactly, evaluated for a single tick instead of a precomputed array. */
	private static float keyframe(float[] keys, int t, int lifetime) {
		float inc = (keys.length - 1) / (float) Math.max(1, lifetime);
		float is = inc * t;
		int isF = Mth.clamp(Mth.floor(is), 0, keys.length - 1);
		float diff = isF < keys.length - 1 ? keys[isF + 1] - keys[isF] : 0.0f;
		return keys[isF] + diff * (is - isF);
	}

	private static final class Sparkle {
		double x, y;
		double xd, yd;
		int age;
		int lifetime;
		int delay;
		float r, g, b;
		int startFrame;
		float baseScale;
		float decay;
		float gravity;
	}
}
