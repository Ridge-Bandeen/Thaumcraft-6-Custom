package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Temporary stand-in body for the 3 Eldritch bosses ({@code EntityEldritchGuardian}/{@code
 * EntityEldritchWarden}/{@code EntityEldritchGolem}) - a single box sized to the real entity's own hitbox
 * (width/height passed in at construction), NOT a port of the originals' real hand-modeled meshes ({@code
 * ModelEldritchGuardian}/{@code ModelEldritchGolem} - 454 and 300 lines of real, individually-tuned,
 * parented cuboid geometry each, shared between Guardian and Warden in the original).
 *
 * Real, deliberate simplification, same judgment call as {@code BlockThaumatorium}'s own texture-reuse doc
 * comment: transcribing ~54 parented cuboids by hand, with no automated way to verify the result actually
 * looks right (GameTest is server-only - see the port-tracking memory's GameTest-limitations entry - the
 * only check available is a manual runClient screenshot), is real, separate, error-prone work not worth
 * blocking these bosses' actual mechanics on. This keeps the bosses visible and fightable (not literally
 * invisible - a real regression, unlike {@code WISP}/{@code FLUX_RIFT}'s deliberate no-model FX entities)
 * while that real mesh port remains open, tracked work.
 */
public class ModelEldritchPlaceholder<T extends LivingEntity> extends EntityModel<T> {

	private final ModelPart body;

	public ModelEldritchPlaceholder(float width, float height) {
		super(RenderType::entityCutout);
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float w = width * 16.0f;
		float h = height * 16.0f;
		root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-w / 2.0f, -h, -w / 2.0f, w, h, w),
				PartPose.offset(0.0f, h, 0.0f));
		ModelPart baked = LayerDefinition.create(mesh, 16, 16).bakeRoot();
		body = baked.getChild("body");
	}

	@Override
	public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
		// static placeholder - no animation to drive.
	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
		body.render(poseStack, buffer, packedLight, packedOverlay, color);
	}
}
