package thaumcraft.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.mojang.math.Axis;

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
import thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType;
import thaumcraft.api.research.ResearchCategory;

/**
 * Replaces the old (1.12.2) {@code HudHandler#renderKnowledgeGains}/{@code KnowledgeGainTracker} - the real
 * "you gained knowledge" HUD cue (an icon flying in near screen-center, settling, then shrinking away toward
 * the hotbar book icon, with glow-burst flashes at birth/death), restored 2026-09-24. Real, previously-
 * completely-unbuilt feature (not a rendering simplification of something that already existed) - see {@link
 * thaumcraft.common.lib.network.misc.PacketKnowledgeGain}'s own doc comment and {@code
 * ThaumcraftInternalMethodHandler#addKnowledge}'s own doc comment for the real server-side dispatch this
 * class receives from.
 *
 * <p>Real, faithful port of the true original's own real tick/render split: {@link
 * thaumcraft.client.lib.events.RenderEventHandler#clientWorldTick}'s own real {@code kgFade}/{@code progress}
 * countdown logic is ported to {@link #onClientTick}, and {@code HudHandler#renderKnowledgeGains}'s own real
 * multi-phase easing math (fly-in scale/position curve for the back 66% of a tracker's real lifetime, a
 * settle/pulse curve for the front 34%, plus separate birth/death glow-burst overlays) is ported to {@link
 * #onRenderGui} exactly - same real formulas, same real per-tracker seeded {@link RandomSource} jitter.
 *
 * <p>Real, disclosed simplification: the true original's own real Z-depth layering (each tracker offset
 * further back, category icon drawn 1 unit closer than its own type icon) is dropped - paint ORDER alone
 * (book, then each tracker in turn, category icon immediately after its own type icon) already produces the
 * same real visual stacking in this port's own alpha-blended, no-depth-test 2D pass, without needing literal Z
 * offsets.
 *
 * <p>Real, checked-not-assumed finding (2026-09-24, while building {@link FXGenericGui}): the true original's
 * own real {@code KnowledgeGainTracker.sparks} field is declared, defaulted {@code false}, and never once set
 * {@code true} anywhere in the whole pristine tree - real, confirmed dead code in the true original itself,
 * the same shape as {@code FXDigging}/{@code FXSwarmRunes} earlier this session. This class's own {@code
 * Tracker} carries no {@code sparks} field at all and never calls {@link FXGenericGui#spawn} - not a gap, a
 * faithful match of the true original's own real (never-firing) behavior. {@link
 * thaumcraft.client.gui.GuiResearchTable}'s own real percentage-sidebar sparkle tie-in is the true original's
 * only genuinely LIVE {@code drawSimpleSparkleGui} caller.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class KnowledgeGainOverlay {

	private static final ResourceLocation BOOK = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/items/thaumonomicon.png");
	private static final ResourceLocation[] KNOW_TYPE = {
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/research/knowledge_theory.png"),
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/research/knowledge_observation.png"),
	};
	private static final ResourceLocation PARTICLES = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
	private static final int PARTICLE_GRID = 64;

	private static final List<Tracker> TRACKERS = new ArrayList<>();
	private static float kgFade;

	private KnowledgeGainOverlay() {
	}

	/** Real client-bound entry point, called from {@link thaumcraft.client.fx.ClientFxHandlers#handleKnowledgeGain} -
	 * matches the true original's own real {@code progress = 40 + rand.nextInt(20)} lifetime (real +10 bonus
	 * for {@code THEORY}, matching the true original's own real {@code KnowledgeGainTracker} constructor). */
	public static void add(EnumKnowledgeType type, ResearchCategory category, RandomSource random) {
		int progress = 40 + random.nextInt(20);
		if (type == EnumKnowledgeType.THEORY) {
			progress += 10;
		}
		TRACKERS.add(new Tracker(type, category, progress, progress, random.nextLong()));
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		if (TRACKERS.isEmpty()) {
			if (kgFade > 0.0f) {
				kgFade -= 1.0f;
			}
		} else {
			kgFade = Math.min(40.0f, kgFade + 10.0f);
			TRACKERS.removeIf(t -> --t.progress <= 0);
		}
	}

	@SubscribeEvent
	static void onRenderGui(RenderGuiEvent.Post event) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		if (kgFade <= 0.0f && TRACKERS.isEmpty()) return;

		GuiGraphics graphics = event.getGuiGraphics();
		int ww = mc.getWindow().getGuiScaledWidth();
		int hh = mc.getWindow().getGuiScaledHeight();
		float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);

		if (kgFade > 0.0f) {
			graphics.pose().pushPose();
			graphics.pose().translate(ww - 9, hh - 9, 0);
			GuiQuadRenderer.renderQuadCentered(graphics, BOOK, 1, 0, 16.0f, 1.0f, 1.0f, 1.0f, kgFade / 40.0f, false);
			graphics.pose().popPose();
		}

		int a = 0;
		for (Tracker t : TRACKERS) {
			renderTracker(graphics, t, a++, ww, hh, partialTick);
		}
	}

	private static void renderTracker(GuiGraphics graphics, Tracker t, int index, int ww, int hh, float partialTick) {
		RandomSource rand = RandomSource.create(t.seed);
		float s = 16.0f;
		float x = ww / 4.0f + rand.nextInt(32);
		float y = hh / 3.0f + rand.nextInt(32);
		float wot = 0.0f;

		if (t.progress < t.max * 0.66f) {
			float q = (t.progress - partialTick) / (t.max * 0.66f);
			s *= q;
			float m = Mth.sin((float) (q * Math.PI - Math.PI / 2.0)) * 0.5f + 0.5f;
			y *= m;
			float d = Mth.sin((float) (m * Math.PI * 0.5));
			x *= d;
		} else {
			wot = t.max - t.progress + partialTick;
			float wot2 = wot / (t.max * 0.33f);
			float m = Mth.sin((float) (wot2 * Math.PI * 2.0 - Math.PI / 2.0)) * 0.5f + 1.5f;
			if (wot2 < 0.5f) {
				s *= wot2 * 2.0f;
			}
			s *= m;
		}

		float xx = ww - 12 + rand.nextInt(8) - x;
		float yy = hh - 12 + rand.nextInt(8) - y;

		graphics.pose().pushPose();
		graphics.pose().translate(xx, yy, 0);
		// the original rotated about -Z (glRotatef(a, 0, 0, -1)); +Z here turned the flying icon the other way, a quarter-turn-ish off
		graphics.pose().mulPose(Axis.ZP.rotationDegrees(-(84 + rand.nextInt(12))));
		GuiQuadRenderer.renderQuadCentered(graphics, KNOW_TYPE[t.type.ordinal()], 1, 0, s, 1.0f, 1.0f, 1.0f, 1.0f, false);

		if (t.category != null) {
			GuiQuadRenderer.renderQuadCentered(graphics, t.category.icon, 1, 0, s * 0.75f, 1.0f, 1.0f, 1.0f, 1.0f, false);
		}

		if (t.progress > t.max * 0.9f) {
			float wot3 = wot / (t.max * 0.1f);
			float m2 = Mth.sin((float) (wot3 * Math.PI * 2.0 - Math.PI / 2.0)) * 0.25f + 0.25f;
			float size = 64.0f * m2;
			graphics.pose().mulPose(Axis.ZP.rotationDegrees(rand.nextInt(360)));
			float r2 = warm(rand, 0);
			float g2 = warm(rand, 1);
			float b2 = warm(rand, 2);
			GuiQuadRenderer.renderQuadCentered(graphics, PARTICLES, PARTICLE_GRID, 320 + rand.nextInt(16), size, r2, g2, b2, 1.0f, true);
		}
		if (t.progress < t.max * 0.1f) {
			float wot3 = 1.0f - (t.progress - partialTick) / (t.max * 0.1f);
			float m2 = Mth.sin((float) (wot3 * Math.PI * 2.0 - Math.PI / 2.0)) * 0.25f + 0.25f;
			float size = 32.0f * m2;
			graphics.pose().mulPose(Axis.ZP.rotationDegrees(rand.nextInt(360)));
			float r2 = warm(rand, 0);
			float g2 = warm(rand, 1);
			float b2 = warm(rand, 2);
			GuiQuadRenderer.renderQuadCentered(graphics, PARTICLES, PARTICLE_GRID, 320 + rand.nextInt(16), size, r2, g2, b2, 1.0f, true);
		}

		graphics.pose().popPose();
	}

	/** Matches the true original's own real {@code MathHelper.getInt(rand, lo, hi) / 255f} warm gold/yellow
	 * randomization, reused verbatim by every glow-burst site in this whole port's own restored particle FX. */
	private static float warm(RandomSource rand, int channel) {
		return switch (channel) {
			case 0 -> 1.0f;
			case 1 -> (189 + rand.nextInt(255 - 189 + 1)) / 255.0f;
			default -> (64 + rand.nextInt(255 - 64 + 1)) / 255.0f;
		};
	}

	private static final class Tracker {
		final EnumKnowledgeType type;
		final ResearchCategory category;
		int progress;
		final int max;
		final long seed;

		Tracker(EnumKnowledgeType type, ResearchCategory category, int progress, int max, long seed) {
			this.type = type;
			this.category = category;
			this.progress = progress;
			this.max = max;
			this.seed = seed;
		}
	}
}
