package thaumcraft.client.lib;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * The original's warp screen effects (its {@code ShaderHandler} plus the shader, vignette and fog parts of
 * {@code RenderEventHandler}):
 * <ul>
 * <li>a post-shader while the local player has Death Gaze (desaturate), Blurred Vision (blur), Unnatural Hunger
 * (reddened) or Sun Scorned (bloom), processed over the finished frame before the HUD, unless {@code disableShaders};</li>
 * <li>Death Gaze also darkens the screen's edges with a slowly deepening, pulsing vignette;</li>
 * <li>the warp mist (misc event 1, two minutes) and a nearby Eldritch Guardian (event 2, at least ten seconds) thicken
 * the fog. The original set GL_EXP fog of density up to 0.1; modern fog is linear, so the density becomes a far plane
 * of 2.5 / density, the near plane closing in with it.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class WarpScreenEffects {

	private static final ResourceLocation[] SHADERS = {
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "shaders/post/desaturatetc.json"),
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "shaders/post/blurtc.json"),
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "shaders/post/hunger.json"),
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "shaders/post/sunscorned.json") };
	private static final PostChain[] CHAINS = new PostChain[SHADERS.length];
	/** Shaders that failed to load, so a broken one isn't retried every tick. */
	private static final boolean[] FAILED = new boolean[SHADERS.length];
	private static int chainWidth;
	private static int chainHeight;

	static final ResourceLocation VIGNETTE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/vignette.png");
	private static int warpVignette;
	private static float targetBrightness = 1.0f;
	private static float prevVignetteBrightness;

	private static boolean fogFiddled;
	private static float fogTarget;
	private static int fogDuration;

	private WarpScreenEffects() {
	}

	/** The original's PacketMiscEvent codes 1 (the warp mist) and 2 (an Eldritch Guardian nearby). */
	public static void thickenFog(boolean mist) {
		fogFiddled = true;
		if (mist) {
			fogDuration = 2400;
		} else if (fogDuration < 200) {
			fogDuration = 200;
		}
	}

	@SubscribeEvent
	static void onPlayerTick(PlayerTickEvent.Pre event) {
		Player player = event.getEntity();
		Minecraft mc = Minecraft.getInstance();
		if (!player.level().isClientSide || player != mc.player) return;
		checkShader(player, ThaumcraftMobEffects.DEATH_GAZE, 0);
		if (player.hasEffect(ThaumcraftMobEffects.DEATH_GAZE)) warpVignette = 10;
		checkShader(player, ThaumcraftMobEffects.BLURRED_VISION, 1);
		checkShader(player, ThaumcraftMobEffects.UNNATURAL_HUNGER, 2);
		checkShader(player, ThaumcraftMobEffects.SUN_SCORNED, 3);
		if (warpVignette > 0) {
			warpVignette--;
			targetBrightness = 0.0f;
		} else {
			targetBrightness = 1.0f;
		}
		if (fogFiddled) {
			if (fogDuration < 100) {
				fogTarget = 0.1f * (fogDuration / 100.0f);
			} else if (fogTarget < 0.1f) {
				fogTarget += 0.001f;
			}
			if (--fogDuration < 0) fogFiddled = false;
		}
	}

	private static void checkShader(Player player, Holder<MobEffect> effect, int id) {
		if (player.hasEffect(effect)) {
			if (CHAINS[id] == null && !FAILED[id]) {
				Minecraft mc = Minecraft.getInstance();
				try {
					CHAINS[id] = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), SHADERS[id]);
					CHAINS[id].resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
				} catch (Exception e) {
					FAILED[id] = true;
					Thaumcraft.LOGGER.warn("Failed to load shader {}", SHADERS[id], e);
				}
			}
		} else if (CHAINS[id] != null) {
			CHAINS[id].close();
			CHAINS[id] = null;
		}
	}

	/** As the original: over the finished frame (hand included), before any of the HUD. */
	@SubscribeEvent(priority = EventPriority.HIGHEST)
	static void onRenderGui(RenderGuiEvent.Pre event) {
		if (ThaumcraftConfig.client(ThaumcraftConfig.DISABLE_SHADERS)) return;
		Minecraft mc = Minecraft.getInstance();
		boolean any = false;
		int w = mc.getWindow().getWidth();
		int h = mc.getWindow().getHeight();
		for (PostChain chain : CHAINS) {
			if (chain == null) continue;
			if (!any && (w != chainWidth || h != chainHeight)) {
				for (PostChain c : CHAINS) {
					if (c != null) c.resize(w, h);
				}
				chainWidth = w;
				chainHeight = h;
			}
			if (!any) {
				RenderSystem.disableBlend();
				RenderSystem.disableDepthTest();
				RenderSystem.resetTextureMatrix();
			}
			any = true;
			chain.process(event.getPartialTick().getGameTimeDeltaTicks());
		}
		if (any) {
			mc.getMainRenderTarget().bindWrite(true);
			RenderSystem.enableDepthTest();
		}
	}

	/** A GUI layer over the vanilla camera overlays (where the original drew it, at the portal overlay). */
	public static void renderVignette(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		float brightness = 1.0f - targetBrightness;
		prevVignetteBrightness += (brightness - prevVignetteBrightness) * 0.01f;
		if (prevVignetteBrightness <= 0.0f) return;
		float b = prevVignetteBrightness * (1.0f + Mth.sin(mc.player.tickCount / 2.0f) * 0.1f);
		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.enableBlend();
		RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR,
				GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		graphics.setColor(b, b, b, 1.0f);
		int w = graphics.guiWidth();
		int h = graphics.guiHeight();
		graphics.blit(VIGNETTE, 0, 0, -90, 0.0f, 0.0f, w, h, w, h);
		graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableBlend();
	}

	@SubscribeEvent
	static void onRenderFog(ViewportEvent.RenderFog event) {
		if (!fogFiddled || fogTarget <= 0.0f) return;
		float far = event.getFarPlaneDistance();
		float r = Mth.clamp(2.5f / fogTarget / far, 0.0f, 1.0f);
		if (r >= 1.0f) return;
		event.setFarPlaneDistance(far * r);
		event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), event.getNearPlaneDistance() * r * r));
		event.setCanceled(true);
	}
}
