package thaumcraft.client.render;

import java.util.Map;

import org.joml.Matrix4f;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Minecart;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import net.neoforged.neoforge.client.model.renderable.CompositeRenderable;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.construct.EntityTurretCrossbow;

/**
 * Renders {@link EntityTurretCrossbow}'s real advanced-variant OBJ mesh - restored 2026-09-16, the real gap
 * this whole port's own Custom Mob Models/Renderers scope flagged early on (the true original's own real
 * {@code RenderTurretCrossbowAdvanced} loads and draws a real {@code crossbow_advanced.obj} mesh via its own
 * bespoke 1.12.2-era {@code AdvancedModelLoader} - see the TODO's own detailed research notes for the real
 * modern replacement path this uses instead).
 *
 * <p>Real modern OBJ-loading path, verified against the real decompiled 1.21.1 NeoForge sources (not guessed):
 * {@link ObjLoader#loadModel} loads the real {@code .obj} directly (not tied to block/item JSON), {@link
 * ObjModel#bakeRenderable} bakes it into a real {@link CompositeRenderable} via {@link
 * StandaloneGeometryBakingContext} (a real context purpose-built for exactly this non-block/item use), baked
 * ONCE in this renderer's own constructor and rendered every frame via {@link CompositeRenderable#render}. A
 * real, previously-unknown blocker resolved along the way: this {@code .obj} (like every other entity-side
 * one in this port) has no companion real {@code .mtl} file - neither does the true original's own copy, since
 * its bespoke loader never needed material-driven texture resolution (every real group here shares one single
 * material anyway) - fixed with a new, explicitly-synthetic minimal {@code .mtl} stub, not recovered data.
 *
 * <p>Real per-part animation, matching the true original's own real {@code renderTurret} exactly: {@code legs}
 * gets a real {@code 0.66/0.75/0.66} scale while the turret rides an {@link Minecart}; {@code mech}/{@code
 * box}/{@code shield}/{@code brain}/{@code loader}/{@code bow1}/{@code bow2} all share the turret's own real
 * head yaw/pitch aim plus a real small random hurt-jiggle translate while {@code hurtTime > 0}; {@code loader}
 * additionally gets a real breathing Z-slide off {@link EntityTurretCrossbow#getLoadProgress}; {@code bow1}/
 * {@code bow2} each get a real opposite-direction Y-rotation recoil swing off vanilla's own real {@code
 * getAttackAnim} (this port's own real modern substitute for the true original's own parallel custom
 * swing-progress mechanism - see {@link EntityTurretCrossbow}'s own doc comment). {@link CompositeRenderable}
 * addresses each real named part via the true original's own real OBJ group names ({@code g <name>} lines,
 * confirmed present) through its own real per-part {@link CompositeRenderable.Transforms} map, built fresh
 * each frame as real {@link Matrix4f} values rather than via {@link PoseStack} pushes (that API's own real
 * shape - see its own doc comment).
 *
 * <p>Real, disclosed simplification: the true original's own real hurt-red vertex tint (a real momentary
 * flash) is dropped - {@link CompositeRenderable}'s own real mesh-rendering path hardcodes full white/opaque
 * per real vertex with no exposed tint parameter, and reproducing it would need a real custom quad-copying
 * wrapper for a minor, transient cosmetic detail. The real hurt-jiggle translate (the more noticeable half of
 * that same effect) is kept.
 */
public class RenderTurretCrossbowAdvanced extends EntityRenderer<EntityTurretCrossbow> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/entity/crossbow_advanced.png");
	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/crossbow_advanced.obj");
	private static final ResourceLocation MTL_OVERRIDE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/obj/crossbow_advanced.mtl");

	private final CompositeRenderable renderable;

	public RenderTurretCrossbowAdvanced(EntityRendererProvider.Context context) {
		super(context);
		ObjModel model = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(MODEL, true, true, false, true, MTL_OVERRIDE.toString()));
		var bakingContext = StandaloneGeometryBakingContext.create(Map.of("#Material__25", TEXTURE));
		renderable = model.bakeRenderable(bakingContext);
	}

	@Override
	public void render(EntityTurretCrossbow entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
		poseStack.pushPose();
		poseStack.translate(0.0, 0.75, 0.0);

		Matrix4f legsTransform = new Matrix4f();
		if (entity.isPassenger() && entity.getVehicle() instanceof Minecart) {
			legsTransform.scale(0.66f, 0.75f, 0.66f);
		}

		Matrix4f headTransform = new Matrix4f();
		if (entity.hurtTime > 0) {
			float jiggle = entity.hurtTime / 500.0f;
			headTransform.translate((float) (entity.getRandom().nextGaussian() * jiggle),
					(float) (entity.getRandom().nextGaussian() * jiggle), (float) (entity.getRandom().nextGaussian() * jiggle));
		}
		float headYaw = Mth.lerp(partialTick, entity.yHeadRotO, entity.yHeadRot);
		float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
		headTransform.rotateY((float) Math.toRadians(-headYaw)).rotateX((float) Math.toRadians(pitch));

		Matrix4f loaderTransform = new Matrix4f(headTransform)
				.translate(0.0f, 0.0f, Mth.sin(Mth.sqrt(entity.getLoadProgress(partialTick)) * (float) Math.PI * 2.0f) / 12.0f);

		float attackAnim = entity.getAttackAnim(partialTick);
		float swing = Mth.sin(Mth.sqrt(attackAnim) * (float) Math.PI * 2.0f) * 20.0f;
		Matrix4f bow1Transform = new Matrix4f(headTransform).translate(0.0f, 0.0f, 0.375f).rotateY((float) Math.toRadians(swing));
		Matrix4f bow2Transform = new Matrix4f(headTransform).translate(0.0f, 0.0f, 0.375f).rotateY((float) Math.toRadians(-swing));

		CompositeRenderable.Transforms transforms = CompositeRenderable.Transforms.of(ImmutableMap.<String, Matrix4f>builder()
				.put("legs", legsTransform)
				.put("mech", headTransform)
				.put("box", headTransform)
				.put("shield", headTransform)
				.put("brain", headTransform)
				.put("loader", loaderTransform)
				.put("bow1", bow1Transform)
				.put("bow2", bow2Transform)
				.build());

		renderable.render(poseStack, bufferSource, texture -> RenderType.entityCutout(texture), packedLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, partialTick, transforms);

		poseStack.popPose();
	}

	@Override
	public ResourceLocation getTextureLocation(EntityTurretCrossbow entity) {
		return TEXTURE;
	}
}
