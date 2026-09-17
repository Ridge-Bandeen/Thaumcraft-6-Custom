package thaumcraft.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import net.neoforged.neoforge.client.model.renderable.CompositeRenderable;
import thaumcraft.Thaumcraft;

/**
 * Draws the true original's own real "sonic scream" dome effect ({@code FXSonic}/{@code PacketFXSonic}) -
 * shared between {@link RenderEldritchGuardian} and {@link RenderEldritchWarden}, since both entities'
 * {@code performRangedAttack} melee-range fallback triggers the exact same real visual off the exact same
 * real {@code hemis.obj} mesh.
 *
 * <p>Real, genuine architectural fix vs. the true original's own approach: the original's own {@code FXSonic}
 * is a real client {@code Particle} that tracks a real target entity - but that target is always the
 * SCREAMING MOB ITSELF (checked directly: {@code PacketFXSonic}'s own {@code source} field is
 * {@code getEntityId()} called from within the mob's own {@code attackEntityWithRangedAttack}, not the
 * player being screamed at), and {@code Particle#render} has no {@link PoseStack}/{@link MultiBufferSource}
 * available at all, which {@link CompositeRenderable#render} needs. Since the effect only ever tracks its own
 * owner, this draws it directly from within {@link RenderEldritchGuardian}/{@link RenderEldritchWarden}'s own
 * real {@code EntityRenderer#render()} calls instead - that method already has the exact right context for
 * free, and it's a faithful fit since the true original's own effect always followed exactly one entity: its
 * own owner.
 *
 * <p>Real, confirmed texture-frame-swap animation (not a mesh animation): the true original's own real
 * {@code renderParticle} rebinds a DIFFERENT texture each frame ({@code ripple<N>.png}, 15 real frames, all
 * present in this port's resource tree) rather than animating the mesh itself, picked by
 * {@code min(15, (int)(14*fade)+1)} off the effect's own real age/max-age fraction. {@link CompositeRenderable}
 * bakes its own texture binding ONCE at construction time (baked into the mesh itself, not swappable per
 * render call) - so this bakes 15 separate renderables up front (one per {@code rippleN.png}), lazily on
 * first use, and selects which one to draw each frame. {@code hemis.obj} (like every other entity-side OBJ in
 * this port) has no real companion {@code .mtl} - the true original's own bespoke loader never needed one
 * either (confirmed directly against {@code master}) - fixed with the same synthetic-stub technique
 * {@code crossbow_advanced.mtl}/{@code golembuilder_override.mtl} already established.
 *
 * <p>Real, disclosed simplification: the true original's own real 50%-grey vertex dim
 * ({@code glColor4f(0.5,0.5,0.5,1)}) is dropped - {@link CompositeRenderable}'s own real mesh-rendering path
 * hardcodes full white/opaque per real vertex (confirmed directly in its own real {@code Mesh#render}, which
 * always calls {@code putBulkData(..., 1, 1, 1, 1, ...)}), same real "no exposed tint parameter" limitation
 * {@link RenderTurretCrossbowAdvanced}'s own hurt-tint already hit.
 */
final class EldritchSonicFx {

	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/hemis.obj");
	private static final ResourceLocation MTL_OVERRIDE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/hemis.mtl");

	private static final Map<Integer, CompositeRenderable> RENDERABLES = new HashMap<>();

	private EldritchSonicFx() {
	}

	private static CompositeRenderable renderableForFrame(int frame) {
		return RENDERABLES.computeIfAbsent(frame, f -> {
			ObjModel model = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(MODEL, true, true, false, true, MTL_OVERRIDE.toString()));
			ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/ripple" + f + ".png");
			return model.bakeRenderable(StandaloneGeometryBakingContext.create(Map.of("Material__25", texture)));
		});
	}

	/**
	 * @param startTick the entity's own {@code tickCount} when the effect was triggered ({@link
	 * Integer#MIN_VALUE} means "no effect active")
	 * @param duration real total lifetime in ticks (matches the true original's own real {@code age +
	 * rand.nextInt(age/2)}, age=10)
	 * @param yaw/pitch the entity's own real head yaw/body pitch, captured ONCE at trigger time - matches the
	 * true original's own real {@code Particle} constructor capturing {@code target.getRotationYawHead()}/
	 * {@code target.rotationPitch} once rather than tracking them continuously
	 */
	static void render(LivingEntity entity, int startTick, int duration, float yaw, float pitch, float partialTick,
			PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		if (startTick == Integer.MIN_VALUE) return;
		float elapsed = (entity.tickCount - startTick) + partialTick;
		if (elapsed < 0.0f || elapsed >= duration) return;

		float fade = elapsed / duration;
		int frame = Math.min(15, (int) (14.0f * fade) + 1);
		float height = entity.getBbHeight();
		float width = entity.getBbWidth();

		poseStack.pushPose();
		poseStack.translate(0.0, entity.getEyeHeight(), 0.0);
		poseStack.mulPose(Axis.YP.rotationDegrees(-yaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(pitch));
		poseStack.translate(0.0, 0.0, 2.0 * height + width / 2.0);
		poseStack.scale(0.25f * height, 0.25f * height, -1.0f * height);

		renderableForFrame(frame).render(poseStack, bufferSource, texture -> RenderType.entityTranslucent(texture),
				packedLight, OverlayTexture.NO_OVERLAY, partialTick, CompositeRenderable.Transforms.EMPTY);

		poseStack.popPose();
	}
}
