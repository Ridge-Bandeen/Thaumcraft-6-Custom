package thaumcraft.client.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import net.neoforged.neoforge.client.model.renderable.CompositeRenderable;
import thaumcraft.Thaumcraft;

/**
 * The original's FXShieldRunes: a glowing rune hemisphere ({@code hemis.obj}, 15 animation frames) flashing where a
 * shield took a blow - turned towards the attacker - on a runic-shielded player or a warded/eldritch mob
 * ({@link thaumcraft.common.lib.network.fx.PacketFXShield}), or over a Pedestal whose stabilizer swallowed an
 * instability event (no target: magenta, a hemisphere facing up). Hostile mobs show the ripple frames dimmed; others
 * the rune frames. Drawn additively, unlit, both sides, from the hemisphere's centre scaled by 0.2 x the target's height
 * (2 without a target), fading out over its last third.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ShieldRunesFx {

	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/hemis.obj");
	private static final ResourceLocation MTL_OVERRIDE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/hemis.mtl");
	private static final Map<String, CompositeRenderable> RENDERABLES = new HashMap<>();
	private static final List<Shield> ACTIVE = new ArrayList<>();

	private static final class Shield {
		double x, y, z, xo, yo, zo;
		@Nullable final Entity target;
		final int maxAge;
		int age;
		final float yaw;
		final float pitch;

		Shield(double x, double y, double z, @Nullable Entity target, int maxAge, float yaw, float pitch) {
			this.x = this.xo = x;
			this.y = this.yo = y;
			this.z = this.zo = z;
			this.target = target;
			this.maxAge = maxAge;
			this.yaw = yaw;
			this.pitch = pitch;
		}
	}

	private ShieldRunesFx() {
	}

	public static void spawn(double x, double y, double z, @Nullable Entity target, int age, float yaw, float pitch) {
		var random = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.random : net.minecraft.util.RandomSource.create();
		Shield s = new Shield(x, y, z, target, age + random.nextInt(Math.max(1, age / 2)), yaw, pitch);
		if (target != null) {
			s.x = s.xo = target.getX();
			s.y = s.yo = (target.getBoundingBox().minY + target.getBoundingBox().maxY) / 2.0;
			s.z = s.zo = target.getZ();
		}
		ACTIVE.add(s);
	}

	/** The original's PacketFXShield handler: one shield turned towards the attacker; -1 (no attacker) above and below,
	 * -2 (a fall) below, -3 (a falling block) above. */
	public static void forEntity(Entity source, int targetId) {
		var level = source.level();
		if (targetId >= 0) {
			float yaw = 0.0f;
			float pitch = 90.0f;
			Entity t = level.getEntity(targetId);
			if (t != null) {
				double d0 = source.getX() - t.getX();
				double d2 = (source.getBoundingBox().minY + source.getBoundingBox().maxY) / 2.0 - (t.getBoundingBox().minY + t.getBoundingBox().maxY) / 2.0;
				double d3 = source.getZ() - t.getZ();
				double d4 = Math.sqrt(d0 * d0 + d3 * d3);
				yaw = (float) (Math.atan2(d3, d0) * 180.0 / Math.PI) - 90.0f;
				pitch = (float) (-(Math.atan2(d2, d4) * 180.0 / Math.PI));
			}
			spawn(source.getX(), source.getY(), source.getZ(), source, 8, yaw, pitch);
		} else if (targetId == -1) {
			spawn(source.getX(), source.getY(), source.getZ(), source, 8, 0.0f, 90.0f);
			spawn(source.getX(), source.getY(), source.getZ(), source, 8, 0.0f, 270.0f);
		} else if (targetId == -2) {
			spawn(source.getX(), source.getY(), source.getZ(), source, 8, 0.0f, 270.0f);
		} else if (targetId == -3) {
			spawn(source.getX(), source.getY(), source.getZ(), source, 8, 0.0f, 90.0f);
		}
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		if (Minecraft.getInstance().isPaused()) return;
		for (Iterator<Shield> it = ACTIVE.iterator(); it.hasNext(); ) {
			Shield s = it.next();
			s.xo = s.x;
			s.yo = s.y;
			s.zo = s.z;
			if (s.age++ >= s.maxAge || (s.target != null && s.target.isRemoved())) {
				it.remove();
				continue;
			}
			if (s.target != null) {
				s.x = s.target.getX();
				s.y = (s.target.getBoundingBox().minY + s.target.getBoundingBox().maxY) / 2.0;
				s.z = s.target.getZ();
			}
		}
		if (Minecraft.getInstance().level == null) ACTIVE.clear();
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ACTIVE.isEmpty()) return;
		Minecraft mc = Minecraft.getInstance();
		Vec3 cam = event.getCamera().getPosition();
		float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		PoseStack poseStack = event.getPoseStack();
		MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
		for (Shield s : ACTIVE) {
			float fade = (s.age + pt) / s.maxAge;
			int frame = Math.min(15, (int) (14.0f * fade) + 1);
			boolean hostile = s.target instanceof net.minecraft.world.entity.monster.Monster
					&& !(s.target instanceof thaumcraft.common.entities.monster.cult.EntityCultist);
			float alpha = Math.min(1.0f, (1.0f - fade) * 3.0f);
			float r, g, b;
			if (s.target == null) {
				r = 0.65f;
				g = 0.1f;
				b = 0.5f;
			} else {
				r = g = b = hostile ? 0.5f : 1.0f;
			}
			poseStack.pushPose();
			poseStack.translate(s.xo + (s.x - s.xo) * pt - cam.x, s.yo + (s.y - s.yo) * pt - cam.y, s.zo + (s.z - s.zo) * pt - cam.z);
			poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - s.yaw));
			poseStack.mulPose(Axis.XP.rotationDegrees(-s.pitch));
			float th = s.target == null ? 2.0f : s.target.getBbHeight();
			poseStack.scale(0.2f * th, 0.2f * th, 0.2f * th);
			MultiBufferSource tinted = type -> new Tint(buffers.getBuffer(type), r, g, b, alpha);
			renderable((hostile ? "models/ripple" : "models/hemis") + frame)
					.render(poseStack, tinted, ShieldRunesFx::additive, 0xF000F0, OverlayTexture.NO_OVERLAY, pt, CompositeRenderable.Transforms.EMPTY);
			poseStack.popPose();
		}
		buffers.endBatch();
	}

	private static CompositeRenderable renderable(String texture) {
		return RENDERABLES.computeIfAbsent(texture, t -> {
			ObjModel model = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(MODEL, true, true, true, true, MTL_OVERRIDE.toString()));
			// the short name: ObjModel#bakeRenderable adds "textures/" and ".png" itself
			return model.bakeRenderable(StandaloneGeometryBakingContext.create(Map.of("#Material__25", ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, t))));
		});
	}

	private static RenderType additive(ResourceLocation texture) {
		return AdditiveType.get(texture);
	}

	/** The original's glBlendFunc(770, 1), culling off, no depth writes. */
	private static final class AdditiveType extends RenderType {
		private static final Map<ResourceLocation, RenderType> CACHE = new HashMap<>();

		static RenderType get(ResourceLocation texture) {
			return CACHE.computeIfAbsent(texture, tex -> create("thaumcraft_shield_runes", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
					CompositeState.builder()
							.setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
							.setTextureState(new TextureStateShard(tex, false, false))
							.setTransparencyState(ADDITIVE_TRANSPARENCY)
							.setCullState(NO_CULL)
							.setWriteMaskState(COLOR_WRITE)
							.setOverlayState(OVERLAY)
							.createCompositeState(false)));
		}

		private AdditiveType() {
			super("", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 0, false, false, () -> {}, () -> {});
		}
	}

	/** Multiplies every vertex colour by the shield's tint and fade. */
	record Tint(VertexConsumer inner, float r, float g, float b, float a) implements VertexConsumer {
		@Override
		public VertexConsumer addVertex(float x, float y, float z) {
			inner.addVertex(x, y, z);
			return this;
		}

		@Override
		public VertexConsumer setColor(int red, int green, int blue, int alpha) {
			inner.setColor((int) (red * r), (int) (green * g), (int) (blue * b), (int) (alpha * a));
			return this;
		}

		@Override
		public VertexConsumer setUv(float u, float v) {
			inner.setUv(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv1(int u, int v) {
			inner.setUv1(u, v);
			return this;
		}

		@Override
		public VertexConsumer setUv2(int u, int v) {
			inner.setUv2(u, v);
			return this;
		}

		@Override
		public VertexConsumer setNormal(float x, float y, float z) {
			inner.setNormal(x, y, z);
			return this;
		}
	}
}
