package thaumcraft.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftAttributes;
import thaumcraft.client.fx.particles.FXGeneric;

/**
 * The champion mods' client side, as the original's ChampionMod*#showFX / #preRender (driven from RenderEventHandler):
 * every client tick each champion creature gives off its mod's particles, the fiery, grim, venomous and vampiric ones are
 * drawn tinted, and a tainted one is drawn again under a scrolling taint-fibre skin (the original LayerTainted). The mod
 * is read from the synced champion attribute (0-13, the order of ChampionModifier.MODS).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ChampionFx {

	private static final ResourceLocation TAINT_FIBRES = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/taint_fibres.png");
	private static boolean rerendering;

	private ChampionFx() {
	}

	/** The champion mod an entity carries, or -1. As the original, only for creatures. */
	static int modOf(LivingEntity entity) {
		if (!(entity instanceof PathfinderMob) || !entity.isAlive()) return -1;
		var attribute = entity.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (attribute == null) return -1;
		int t = (int) attribute.getValue();
		return t >= 0 && t <= 13 ? t : -1;
	}

	@SubscribeEvent
	static void onEntityTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ClientLevel level)) return;
		int t = modOf(mob);
		if (t < 0) return;
		RandomSource r = level.random;
		AABB box = mob.getBoundingBox();
		float w = r.nextFloat() * mob.getBbWidth();
		float d = r.nextFloat() * mob.getBbWidth();
		float h = r.nextFloat() * mob.getBbHeight();
		double x = box.minX + w;
		double y = box.minY + h;
		double z = box.minZ + d;
		switch (t) {
			case 0 -> { // bold
				if (r.nextBoolean()) return;
				ClientFxHandlers.spawnSpark(level, x, box.minY + h / 3.0f, z, 0.2f, 0.3f - r.nextFloat() * 0.1f, 0.0f, 0.8f + r.nextFloat() * 0.2f, 1.0f);
			}
			case 1 -> { // spined
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, 0, 0, 0.5f + r.nextFloat() * 0.2f, 0.1f + r.nextFloat() * 0.2f, 0.1f + r.nextFloat() * 0.2f, 0.7f, false,
						704 + r.nextInt(4) * 3, 3, 1, 3, 1.2f + r.nextFloat() * 0.3f, 0.0f, 0);
			}
			case 2 -> { // armored
				if (r.nextInt(4) != 0) return;
				generic(level, x, y, z, 0, 0, 0, 0.9f, 0.9f, 0.9f + r.nextFloat() * 0.1f, 0.7f, false, 448, 9, 1, 5 + r.nextInt(4), 0.6f + r.nextFloat() * 0.2f, 0.0f, 0);
			}
			case 3 -> { // mighty
				if (r.nextFloat() > 0.3f) return;
				generic(level, x, y, z, 0, 0, 0, 0.8f + r.nextFloat() * 0.2f, 0.8f + r.nextFloat() * 0.2f, 0.8f + r.nextFloat() * 0.2f, 0.7f, false,
						704 + r.nextInt(4) * 3, 3, 1, 4 + r.nextInt(3), 1.0f + r.nextFloat() * 0.3f, 0.0f, 0);
			}
			case 4 -> { // grim
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, -0.02, 0, r.nextFloat() * 0.2f, r.nextFloat() * 0.2f, r.nextFloat() * 0.2f, 0.8f, false, 640, 10, 1, 8 + r.nextInt(4),
						0.6f + r.nextFloat() * 0.4f, 0.0f, 0);
			}
			case 5 -> { // warded
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, 0, 0, 0.5f + r.nextFloat() * 0.1f, 0.5f + r.nextFloat() * 0.1f, 0.5f + r.nextFloat() * 0.1f, 0.6f, true, 69, 4, 1,
						4 + r.nextInt(4), 0.8f + r.nextFloat() * 0.3f, 0.0f, 0);
			}
			case 6 -> { // warp
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, 0, 0, 0.8f + r.nextFloat() * 0.2f, 0.0f, 0.9f + r.nextFloat() * 0.1f, 0.7f, true, 264, 8, 1, 10 + r.nextInt(4),
						0.6f + r.nextFloat() * 0.4f, 0.0f, 0);
			}
			case 7 -> { // undying
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, 0.03, 0, 0.1f + r.nextFloat() * 0.1f, 0.8f + r.nextFloat() * 0.2f, 0.1f + r.nextFloat() * 0.1f, 0.9f, true, 69, 4, 1,
						4 + r.nextInt(4), 0.5f + r.nextFloat() * 0.2f, 0.0f, 0);
			}
			case 8 -> // fiery
				generic(level, x, y, z, 0, 0.03, 0, 0.9f + r.nextFloat() * 0.1f, 1.0f, 1.0f, 0.7f, false, 640, 10, 1, 8 + r.nextInt(4), 0.7f + r.nextFloat() * 0.2f,
						0.0f, 0);
			case 9 -> { // sickly
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, -0.02, 0, 0.2f, 0.6f + r.nextFloat() * 0.1f, 0.2f + r.nextFloat() * 0.1f, 0.5f, false, 1, 4, 2, 5 + r.nextInt(4),
						0.9f + r.nextFloat() * 0.3f, 0.0f, 0);
			}
			case 10 -> { // venomous
				if (r.nextBoolean()) return;
				generic(level, x, y, z, 0, 0.02, 0, 0.2f, 0.6f + r.nextFloat() * 0.1f, 0.2f + r.nextFloat() * 0.1f, 0.7f, false, 579, 4, 1, 8 + r.nextInt(4),
						0.5f + r.nextFloat() * 0.2f, 0.5f, 0);
			}
			case 11 -> { // vampiric
				if (r.nextFloat() > 0.2f) return;
				generic(level, x, y, z, 0, 0, 0, 0.9f + r.nextFloat() * 0.1f, 0.0f, 0.0f, 0.9f, false, 579, 4, 1, 4 + r.nextInt(4), 0.5f + r.nextFloat() * 0.2f,
						0.0f, 0);
			}
			case 12 -> { // infested
				if (r.nextBoolean()) ClientFxHandlers.spawnSlimeJump(level, mob, 0);
			}
			case 13 -> // tainted
				generic(level, x, y, z, 0, -0.01, 0, 0.1f + r.nextFloat() * 0.2f, 0.0f, 0.1f + r.nextFloat() * 0.1f, 0.25f, false, 1, 5, 1, 6 + r.nextInt(6),
						2.0f + r.nextFloat(), 0.5f, 1);
			default -> {
			}
		}
	}

	/** The original FXDispatcher#drawGenericParticles (with no delay, as every champion call used). */
	private static void generic(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, float r, float g, float b, float alpha,
			boolean loop, int start, int num, int inc, int age, float scale, float rot, int layer) {
		FXGeneric fb = new FXGeneric(level, x, y, z, vx, vy, vz);
		fb.setMaxAge(age);
		fb.setRBGColorF(r, g, b);
		fb.setAlphaF(alpha);
		fb.setLoop(loop);
		fb.setParticles(start, num, inc);
		fb.setScale(scale);
		fb.setLayer(layer);
		fb.setRotationSpeed(rot);
		Minecraft.getInstance().particleEngine.add(fb);
	}

	/** The original preRender colours (GL colour before the model drew; fiery's 5.0 blue clamps to 1). */
	private static float[] tint(int t) {
		return switch (t) {
			case 8 -> new float[] { 1.0f, 0.75f, 1.0f };
			case 4 -> new float[] { 0.6f, 0.6f, 0.6f };
			case 10 -> new float[] { 0.6f, 1.0f, 0.7f };
			case 11 -> new float[] { 1.0f, 0.7f, 0.7f };
			default -> null;
		};
	}

	@SubscribeEvent
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
		if (rerendering) return;
		LivingEntity entity = event.getEntity();
		float[] tint = tint(modOf(entity));
		if (tint == null) return;
		LivingEntityRenderer renderer = event.getRenderer();
		RenderType body = renderer.getModel().renderType(renderer.getTextureLocation(entity));
		MultiBufferSource source = event.getMultiBufferSource();
		MultiBufferSource tinted = type -> type == body ? new thaumcraft.client.render.Tinted(source.getBuffer(type), tint[0], tint[1], tint[2], 1.0f)
				: source.getBuffer(type);
		event.setCanceled(true);
		rerendering = true;
		try {
			renderer.render(entity, entity.getYRot(), event.getPartialTick(), event.getPoseStack(), tinted, event.getPackedLight());
		} finally {
			rerendering = false;
		}
	}

	/** Every living renderer gets the tainted-champion layer, as the original added LayerTainted to a tainted champion's. */
	@SubscribeEvent
	@SuppressWarnings({ "unchecked", "rawtypes" })
	static void onAddLayers(EntityRenderersEvent.AddLayers event) {
		for (var type : event.getEntityTypes()) {
			if (event.getRenderer(type) instanceof LivingEntityRenderer renderer) {
				renderer.addLayer(new TaintedLayer(renderer));
			}
		}
	}

	/** The original LayerTainted: the body again, taint fibres at 0.66 alpha, the texture stretched 8 x 4 and offset by the
	 * entity's id so no two drift alike. */
	private static final class TaintedLayer<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> extends RenderLayer<T, M> {
		TaintedLayer(RenderLayerParent<T, M> parent) {
			super(parent);
		}

		@Override
		public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing, float limbSwingAmount,
				float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
			if (modOf(entity) != 13) return;
			float id = entity.getId();
			float du = Mth.cos(id * 2.5e-4f);
			float dv = id * 0.001f;
			VertexConsumer inner = buffer.getBuffer(RenderType.entityTranslucent(TAINT_FIBRES));
			VertexConsumer shifted = new thaumcraft.client.render.Tinted(inner, 1.0f, 1.0f, 1.0f, 1.0f) {
				@Override
				public VertexConsumer setUv(float u, float v) {
					return super.setUv((u + du) * 8.0f, (v + dv) * 4.0f);
				}
			};
			getParentModel().renderToBuffer(poseStack, shifted, packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0f), 0xA8FFFFFF);
		}
	}
}
