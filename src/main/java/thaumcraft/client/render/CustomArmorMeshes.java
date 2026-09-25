package thaumcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED 2026-09-25 from the true original's own real constructors of {@code ModelFortressArmor},
 * {@code ModelKnightArmor}, {@code ModelLeaderArmor} and {@code ModelRobe} (the 1.12 {@code ModelRenderer}
 * cube lists, one statement per part, converted mechanically - same real texture offsets, box coordinates,
 * rotation points and rotations, nothing hand-copied). Each builds a {@link HumanoidModel}-shaped mesh
 * (head/hat/body/arms/legs, vanilla armor poses) whose six real body parts have their vanilla cubes removed
 * and carry the original's real child parts instead, exactly as the originals' own {@code cubeList.clear()}
 * + {@code addChild} calls did. The {@code legs} flag is the original's "f &lt; 1" (the 0.5-scale leggings
 * instance, which carried belt and leg pieces) versus the 1.0 instance (helm/chest pieces). Texture sheet
 * size is the original's own 128x64 (the shipped PNGs are 2x, sampled proportionally).
 *
 * <p>Runtime behaviour (which parts show for a given slot, set-completion pieces, cloth swing) lives in
 * {@link CustomArmorModel}.
 */
final class CustomArmorMeshes {

	private CustomArmorMeshes() {
	}

	private static PartDefinition emptyPart(PartDefinition root, String name, PartPose pose) {
		return root.addOrReplaceChild(name, CubeListBuilder.create(), pose);
	}

	private static void part(PartDefinition parent, String name, int u, int v, boolean mirror,
			float x, float y, float z, int w, int h, int d,
			float px, float py, float pz, float rx, float ry, float rz) {
		parent.addOrReplaceChild(name, CubeListBuilder.create().texOffs(u, v).mirror(mirror).addBox(x, y, z, w, h, d),
				PartPose.offsetAndRotation(px, py, pz, rx, ry, rz));
	}

	static LayerDefinition fortress(boolean legs) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = emptyPart(root, "head", PartPose.ZERO);
		emptyPart(root, "hat", PartPose.ZERO);
		PartDefinition body = emptyPart(root, "body", PartPose.ZERO);
		PartDefinition rightArm = emptyPart(root, "right_arm", PartPose.offset(-5.0f, 2.0f, 0.0f));
		PartDefinition leftArm = emptyPart(root, "left_arm", PartPose.offset(5.0f, 2.0f, 0.0f));
		PartDefinition rightLeg = emptyPart(root, "right_leg", PartPose.offset(-1.9f, 12.0f, 0.0f));
		PartDefinition leftLeg = emptyPart(root, "left_leg", PartPose.offset(1.9f, 12.0f, 0.0f));
		part(head, "OrnamentL", 78, 8, true, 1.5f, -9.0f, -6.5f, 2, 2, 1, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "OrnamentL2", 78, 8, true, 3.5f, -10.0f, -6.5f, 1, 2, 1, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "OrnamentR", 78, 8, false, -3.5f, -9.0f, -6.5f, 2, 2, 1, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "OrnamentR2", 78, 8, false, -4.5f, -10.0f, -6.5f, 1, 2, 1, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "Helmet", 41, 8, false, -4.5f, -9.0f, -4.5f, 9, 4, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "HelmetR", 21, 13, false, -6.5f, -3.0f, -4.5f, 1, 5, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5235988f);
		part(head, "HelmetL", 21, 13, true, 5.5f, -3.0f, -4.5f, 1, 5, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5235988f);
		part(head, "HelmetB", 41, 21, false, -4.5f, -3.0f, 5.5f, 9, 5, 1, 0.0f, 0.0f, 0.0f, 0.5235988f, 0.0f, 0.0f);
		part(head, "capsthingy", 21, 0, false, -4.5f, -6.0f, -6.5f, 9, 1, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "flapR", 59, 10, false, -10.0f, -2.0f, -1.0f, 3, 3, 1, 0.0f, 0.0f, 0.0f, 0.0f, -0.5235988f, 0.5235988f);
		part(head, "flapL", 59, 10, true, 7.0f, -2.0f, -1.0f, 3, 3, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.5235988f, -0.5235988f);
		part(head, "Gemornament", 68, 11, false, -1.5f, -9.0f, -7.0f, 3, 3, 2, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "Gem", 72, 8, false, -1.0f, -8.5f, -7.5f, 2, 2, 1, 0.0f, 0.0f, 0.0f, -0.1396263f, 0.0f, 0.0f);
		part(head, "Goggles", 100, 18, false, -4.5f, -5.0f, -4.25f, 9, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "Mask0", 52, 2, false, -4.5f, -5.0f, -4.6f, 9, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "Mask1", 76, 2, false, -4.5f, -5.0f, -4.6f, 9, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "Mask2", 100, 2, false, -4.5f, -5.0f, -4.6f, 9, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		if (legs) {
			part(body, "Mbelt", 56, 55, false, -4.0f, 8.0f, -3.0f, 8, 4, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "MbeltL", 76, 44, false, 4.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "MbeltR", 76, 44, false, -5.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "BeltR", 76, 44, false, -5.0f, 4.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "BeltL", 76, 44, false, 4.0f, 4.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Chestplate", 56, 45, false, -4.0f, 1.0f, -4.0f, 8, 7, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Scroll", 34, 27, false, -2.0f, 9.5f, 4.0f, 8, 3, 3, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1919862f);
		}
		if (!legs) {
			part(body, "Backplate", 36, 45, false, -4.0f, 1.0f, 2.0f, 8, 11, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Book", 100, 8, false, 1.0f, -0.3f, 4.0f, 5, 7, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7679449f);
		}
		part(rightArm, "ShoulderR", 56, 35, false, -3.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletR", 100, 26, false, -3.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletstrapR1", 84, 31, false, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletstrapR2", 84, 31, false, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "ShoulderplateRtop", 110, 37, false, -5.5f, -2.5f, -3.5f, 2, 1, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR1", 110, 45, false, -4.5f, -1.5f, -3.5f, 1, 4, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR2", 94, 45, false, -3.5f, 1.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR3", 94, 45, false, -2.5f, 3.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(leftArm, "ShoulderL", 56, 35, true, -1.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletL", 114, 26, false, 1.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "Gauntletstrapl1", 84, 31, true, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletstrapL2", 84, 31, true, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderplateLtop", 110, 37, true, 3.5f, -2.5f, -3.5f, 2, 1, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL1", 110, 45, true, 3.5f, -1.5f, -3.5f, 1, 4, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL2", 94, 45, true, 2.5f, 1.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL3", 94, 45, true, 1.5f, 3.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(rightLeg, "LegpanelR1", 0, 51, false, -1.0f, 0.5f, -3.5f, 3, 4, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR2", 8, 51, false, -1.0f, 3.5f, -2.5f, 3, 4, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR3", 0, 56, false, -1.0f, 6.5f, -1.5f, 3, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR4", 0, 43, false, -3.0f, 0.5f, -3.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR5", 0, 47, false, -3.0f, 2.5f, -2.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR6", 6, 43, false, -3.0f, 4.5f, -1.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "SidepanelR1", 0, 22, false, -2.5f, 0.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightLeg, "SidepanelR2", 0, 31, false, -1.5f, 3.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightLeg, "SidepanelR3", 12, 31, false, -0.5f, 5.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightLeg, "BackpanelR1", 0, 18, false, -3.0f, 0.5f, 2.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "BackpanelR2", 0, 18, false, -3.0f, 2.5f, 1.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "BackpanelR3", 0, 18, false, -3.0f, 4.5f, 0.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "BackpanelL3", 0, 18, true, -2.0f, 4.5f, 0.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL1", 0, 51, true, -2.0f, 0.5f, -3.5f, 3, 4, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL2", 8, 51, true, -2.0f, 3.5f, -2.5f, 3, 4, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL3", 0, 56, true, -2.0f, 6.5f, -1.5f, 3, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL4", 0, 43, true, 1.0f, 0.5f, -3.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL5", 0, 47, true, 1.0f, 2.5f, -2.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL6", 6, 43, true, 1.0f, 4.5f, -1.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "SidepanelL1", 0, 22, true, 1.5f, 0.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftLeg, "SidepanelL2", 0, 31, true, 0.5f, 3.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftLeg, "SidepanelL3", 12, 31, true, -0.5f, 5.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftLeg, "BackpanelL1", 0, 18, true, -2.0f, 0.5f, 2.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "BackpanelL2", 0, 18, true, -2.0f, 2.5f, 1.5f, 5, 3, 1, 0.0f, 0.0f, 0.0f, 0.4363323f, 0.0f, 0.0f);
		return LayerDefinition.create(mesh, 128, 64);
	}

	static LayerDefinition knight(boolean legs) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = emptyPart(root, "head", PartPose.ZERO);
		emptyPart(root, "hat", PartPose.ZERO);
		PartDefinition body = emptyPart(root, "body", PartPose.ZERO);
		PartDefinition rightArm = emptyPart(root, "right_arm", PartPose.offset(-5.0f, 2.0f, 0.0f));
		PartDefinition leftArm = emptyPart(root, "left_arm", PartPose.offset(5.0f, 2.0f, 0.0f));
		PartDefinition rightLeg = emptyPart(root, "right_leg", PartPose.offset(-1.9f, 12.0f, 0.0f));
		PartDefinition leftLeg = emptyPart(root, "left_leg", PartPose.offset(1.9f, 12.0f, 0.0f));
		part(head, "Helmet", 41, 8, false, -4.5f, -9.0f, -4.5f, 9, 9, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "Mbelt", 56, 55, false, -4.0f, 8.0f, -3.0f, 8, 4, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltL", 76, 44, false, 4.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltR", 76, 44, false, -5.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		if (!legs) {
			part(body, "Chestplate", 56, 45, false, -4.0f, 1.0f, -3.0f, 8, 7, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Frontcloth1", 120, 39, false, 0.0f, 0.0f, 0.0f, 6, 8, 1, -3.0f, 11.0f, -3.5f, -0.1047198f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Frontcloth2", 100, 37, false, 0.0f, 7.5f, 1.8f, 6, 3, 1, -3.0f, 11.0f, -3.5f, -0.3316126f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Tabbard", 114, 52, false, -3.0f, 1.2f, -3.5f, 6, 10, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Backplate", 36, 45, false, -4.0f, 1.0f, 2.0f, 8, 11, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak1", 0, 47, false, 0.0f, 0.0f, 0.0f, 9, 12, 1, -4.5f, 1.3f, 4.2f, 0.1396263f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak2", 0, 59, false, 0.0f, 11.7f, -2.0f, 9, 4, 1, -4.5f, 1.3f, 4.2f, 0.3069452f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak3", 0, 59, false, 0.0f, 15.2f, -4.2f, 9, 4, 1, -4.5f, 1.3f, 4.2f, 0.4465716f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CloakAtL", 0, 43, false, 2.5f, 1.0f, 2.0f, 2, 1, 3, 0.0f, 0.0f, 0.0f, 0.1396263f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CloakAtR", 0, 43, false, -4.5f, 1.0f, 2.0f, 2, 1, 3, 0.0f, 0.0f, 0.0f, 0.1396263f, 0.0f, 0.0f);
		}
		part(rightArm, "ShoulderR", 56, 35, true, -3.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "ShoulderR0", 0, 0, true, -4.3f, -1.5f, -3.0f, 3, 5, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR1", 0, 19, false, -3.3f, 3.5f, -2.5f, 1, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR2", 0, 11, true, -2.3f, 3.5f, -3.0f, 1, 2, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "GauntletR", 100, 26, true, -3.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletstrapR1", 84, 31, true, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletstrapR2", 84, 31, true, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderL", 56, 35, false, -1.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderL0", 0, 0, false, 1.3f, -1.5f, -3.0f, 3, 5, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL1", 0, 19, true, 2.3f, 3.5f, -2.5f, 1, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL2", 0, 11, false, 1.3f, 3.5f, -3.0f, 1, 2, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "GauntletL", 114, 26, false, 1.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletstrapL1", 84, 31, true, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletstrapL2", 84, 31, true, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightLeg, "SidepanelR0", 96, 14, false, -3.0f, -0.5f, -2.5f, 5, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(rightLeg, "SidepanelR1", 96, 7, true, 0.0f, 2.5f, -2.5f, 2, 2, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(rightLeg, "SidepanelR2", 114, 5, true, -2.0f, 2.5f, -2.5f, 2, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(rightLeg, "SidepanelR3", 116, 13, false, -3.0f, 2.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(leftLeg, "SidepanelL0", 96, 14, false, -2.0f, -0.5f, -2.5f, 5, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		part(leftLeg, "SidepanelL1", 96, 7, false, -2.0f, 2.5f, -2.5f, 2, 2, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		part(leftLeg, "SidepanelL2", 114, 5, false, 0.0f, 2.5f, -2.5f, 2, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		part(leftLeg, "SidepanelL3", 116, 13, false, 2.0f, 2.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		return LayerDefinition.create(mesh, 128, 64);
	}

	static LayerDefinition leader(boolean legs) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = emptyPart(root, "head", PartPose.ZERO);
		emptyPart(root, "hat", PartPose.ZERO);
		PartDefinition body = emptyPart(root, "body", PartPose.ZERO);
		PartDefinition rightArm = emptyPart(root, "right_arm", PartPose.offset(-5.0f, 2.0f, 0.0f));
		PartDefinition leftArm = emptyPart(root, "left_arm", PartPose.offset(5.0f, 2.0f, 0.0f));
		PartDefinition rightLeg = emptyPart(root, "right_leg", PartPose.offset(-1.9f, 12.0f, 0.0f));
		PartDefinition leftLeg = emptyPart(root, "left_leg", PartPose.offset(1.9f, 12.0f, 0.0f));
		part(head, "Helmet", 41, 8, false, -4.5f, -9.0f, -4.5f, 9, 9, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "Mbelt", 56, 55, false, -4.0f, 8.0f, -3.0f, 8, 4, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltL", 76, 44, false, 4.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltR", 76, 44, false, -5.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		if (!legs) {
			part(body, "BeltL", 76, 44, false, 4.0f, 4.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "BeltR", 76, 44, false, -5.0f, 4.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Chestplate", 56, 45, false, -4.0f, 1.0f, -3.8f, 8, 7, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "ChestOrnament", 76, 53, false, -2.5f, 3.0f, -4.8f, 5, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "ChestClothR", 20, 47, false, -4.5f, 1.2f, -4.5f, 3, 9, 1, 0.0f, 0.0f, 0.0f, 0.0663225f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "ChestClothL", 20, 47, true, 1.5f, 1.2f, -4.5f, 3, 9, 1, 0.0f, 0.0f, 0.0f, 0.0663225f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "LegClothR", 20, 55, false, 0.0f, 0.0f, 0.0f, 3, 8, 1, -4.5f, 10.4f, -3.9f, -0.0349066f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "LegClothL", 20, 55, true, 0.0f, 0.0f, 0.0f, 3, 8, 1, 1.5f, 10.4f, -3.9f, -0.0349066f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Backplate", 36, 45, false, -4.0f, 1.0f, 2.0f, 8, 11, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CollarB", 17, 26, false, -4.5f, -1.5f, 7.0f, 9, 4, 1, 0.0f, 0.0f, -2.5f, 0.2268928f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CollarR", 17, 11, false, -5.5f, -1.5f, -3.0f, 1, 4, 11, 0.0f, 0.0f, -2.5f, 0.2268928f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CollarL", 17, 11, false, 4.5f, -1.5f, -3.0f, 1, 4, 11, 0.0f, 0.0f, -2.5f, 0.2268928f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CollarF", 17, 31, false, -4.5f, -1.5f, -3.0f, 9, 4, 1, 0.0f, 0.0f, -2.5f, 0.2268928f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak1", 0, 47, false, -4.5f, 2.0f, 1.0f, 9, 12, 1, 0.0f, 0.0f, 3.0f, 0.1396263f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak2", 0, 59, false, -4.5f, 14.0f, -1.3f, 9, 4, 1, 0.0f, 0.0f, 3.0f, 0.3069452f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Cloak3", 0, 59, false, -4.5f, 17.0f, -3.7f, 9, 4, 1, 0.0f, 0.0f, 3.0f, 0.4465716f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CloakTL", 0, 43, false, 2.5f, 1.0f, -1.0f, 2, 1, 3, 0.0f, 0.0f, 3.0f, 0.1396263f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "CloakTR", 0, 43, false, -4.5f, 1.0f, -1.0f, 2, 1, 3, 0.0f, 0.0f, 3.0f, 0.1396263f, 0.0f, 0.0f);
		}
		part(rightArm, "ShoulderR", 56, 35, false, -3.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "ShoulderR1", 0, 0, false, -4.3f, -1.5f, -3.0f, 3, 5, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR2", 0, 19, false, -3.3f, 3.5f, -2.5f, 1, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR3", 0, 11, false, -2.3f, 3.5f, -3.0f, 1, 2, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR4", 18, 4, false, -2.3f, -1.5f, -4.0f, 1, 6, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "ShoulderR5", 18, 4, false, -2.3f, -1.5f, 3.0f, 1, 6, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7853982f);
		part(rightArm, "GauntletR", 100, 26, false, -3.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletR2", 102, 37, false, -5.0f, 3.5f, -2.0f, 1, 5, 4, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1675516f);
		part(rightArm, "GauntletstrapR1", 84, 31, false, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "GauntletstrapR2", 84, 31, false, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderL", 56, 35, false, -1.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderL1", 0, 0, false, 1.3f, -1.5f, -3.0f, 3, 5, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL2", 0, 19, true, 2.3f, 3.5f, -2.5f, 1, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL3", 0, 11, false, 1.3f, 3.5f, -3.0f, 1, 2, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL4", 18, 4, false, 1.3f, -1.5f, -4.0f, 1, 6, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "ShoulderL5", 18, 4, false, 1.3f, -1.5f, 3.0f, 1, 6, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.7853982f);
		part(leftArm, "GauntletL", 114, 26, false, 1.5f, 3.5f, -2.5f, 2, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletL2", 102, 37, false, 4.0f, 3.5f, -2.0f, 1, 5, 4, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1675516f);
		part(leftArm, "GauntletstrapL1", 84, 31, true, -1.5f, 3.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "GauntletstrapL2", 84, 31, true, -1.5f, 6.5f, -2.5f, 3, 1, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightLeg, "BackpanelR1", 0, 25, false, -3.0f, -0.5f, 2.5f, 5, 7, 1, 0.0f, 0.0f, 0.0f, 0.0698132f, 0.0f, 0.0f);
		part(rightLeg, "BackpanelR2", 96, 14, false, -3.0f, -0.5f, -2.5f, 5, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(rightLeg, "BackpanelR3", 116, 13, false, -3.0f, 2.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1396263f);
		part(rightLeg, "BackpanelR4", 0, 25, true, -3.0f, -0.5f, -3.5f, 5, 7, 1, 0.0f, 0.0f, 0.0f, -0.0349066f, 0.0f, 0.0f);
		part(leftLeg, "BackpanelL1", 0, 25, false, -2.0f, -0.5f, 2.5f, 5, 7, 1, 0.0f, 0.0f, 0.0f, 0.0698132f, 0.0f, 0.0f);
		part(leftLeg, "BackpanelL2", 96, 14, false, -2.0f, -0.5f, -2.5f, 5, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		part(leftLeg, "BackpanelL3", 116, 13, false, 2.0f, 2.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.1396263f);
		part(leftLeg, "BackpanelL4", 0, 25, false, -2.0f, -0.5f, -3.5f, 5, 7, 1, 0.0f, 0.0f, 0.0f, -0.0349066f, 0.0f, 0.0f);
		return LayerDefinition.create(mesh, 128, 64);
	}

	static LayerDefinition robe(boolean legs) {
		MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f);
		PartDefinition root = mesh.getRoot();
		PartDefinition head = emptyPart(root, "head", PartPose.ZERO);
		emptyPart(root, "hat", PartPose.ZERO);
		PartDefinition body = emptyPart(root, "body", PartPose.ZERO);
		PartDefinition rightArm = emptyPart(root, "right_arm", PartPose.offset(-5.0f, 2.0f, 0.0f));
		PartDefinition leftArm = emptyPart(root, "left_arm", PartPose.offset(5.0f, 2.0f, 0.0f));
		PartDefinition rightLeg = emptyPart(root, "right_leg", PartPose.offset(-1.9f, 12.0f, 0.0f));
		PartDefinition leftLeg = emptyPart(root, "left_leg", PartPose.offset(1.9f, 12.0f, 0.0f));
		part(head, "Hood1", 16, 7, false, -4.5f, -9.0f, -4.6f, 9, 9, 9, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(head, "Hood2", 52, 13, false, -4.0f, -9.7f, 2.0f, 8, 9, 3, 0.0f, 0.0f, 0.0f, -0.2268928f, 0.0f, 0.0f);
		part(head, "Hood3", 52, 14, false, -3.5f, -10.0f, 3.5f, 7, 8, 3, 0.0f, 0.0f, 0.0f, -0.3490659f, 0.0f, 0.0f);
		part(head, "Hood4", 53, 15, false, -3.0f, -10.7f, 3.5f, 6, 7, 3, 0.0f, 0.0f, 0.0f, -0.5759587f, 0.0f, 0.0f);
		part(body, "Mbelt", 16, 55, false, -4.0f, 7.0f, -3.0f, 8, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltB", 16, 55, false, -4.0f, 7.0f, -4.0f, 8, 5, 1, 0.0f, 0.0f, 0.0f, 0.0f, 3.141593f, 0.0f);
		part(body, "MbeltL", 16, 36, false, 4.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(body, "MbeltR", 16, 36, false, -5.0f, 8.0f, -3.0f, 1, 3, 6, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		if (legs) {
			part(leftLeg, "Focipouch", 100, 20, false, 3.5f, 0.5f, -2.5f, 3, 6, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.122173f);
		}
		if (legs) {
			part(body, "FrontclothR1", 108, 38, false, 0.0f, 0.0f, 0.0f, 3, 8, 1, -3.0f, 11.0f, -2.9f, -0.1047198f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "FrontclothR2", 108, 47, false, 0.0f, 7.5f, 1.7f, 3, 3, 1, -3.0f, 11.0f, -2.9f, -0.3316126f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "FrontclothL1", 108, 38, true, 0.0f, 0.0f, 0.0f, 3, 8, 1, 0.0f, 11.0f, -2.9f, -0.1047198f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "FrontclothL2", 108, 47, true, 0.0f, 7.5f, 1.7f, 3, 3, 1, 0.0f, 11.0f, -2.9f, -0.3316126f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackR1", 118, 16, true, 0.0f, 0.0f, 0.0f, 4, 8, 1, -4.0f, 11.5f, 2.9f, 0.1047198f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackR2", 123, 9, false, 0.0f, 7.8f, -0.9f, 1, 2, 1, -4.0f, 11.5f, 2.9f, 0.2268928f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackR3", 120, 12, true, 1.0f, 7.8f, -0.9f, 3, 3, 1, -4.0f, 11.5f, 2.9f, 0.2268928f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackL1", 118, 16, false, 0.0f, 0.0f, 0.0f, 4, 8, 1, 0.0f, 11.5f, 2.9f, 0.1047198f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackL2", 123, 9, true, 3.0f, 7.8f, -0.9f, 1, 2, 1, 0.0f, 11.5f, 2.9f, 0.2268928f, 0.0f, 0.0f);
		}
		if (legs) {
			part(body, "ClothBackL3", 120, 12, false, 0.0f, 7.8f, -0.9f, 3, 3, 1, 0.0f, 11.5f, 2.9f, 0.2268928f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Chestplate", 16, 25, false, -4.0f, 1.0f, -3.0f, 8, 6, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Chestthing", 56, 50, false, -2.5f, 1.0f, -4.0f, 5, 7, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Scroll", 78, 25, false, -2.0f, 9.5f, 4.0f, 8, 3, 3, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.1919862f);
		}
		if (!legs) {
			part(body, "Backplate", 36, 45, false, -4.0f, 1.0f, 1.9f, 8, 11, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "Book", 81, 16, false, 1.0f, 0.0f, 4.0f, 5, 7, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.7679449f);
		}
		if (!legs) {
			part(body, "ClothchestL", 108, 38, true, 2.1f, 0.5f, -3.5f, 2, 8, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		if (!legs) {
			part(body, "ClothchestR", 108, 38, false, -4.1f, 0.5f, -3.5f, 2, 8, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		}
		part(rightArm, "ShoulderR", 16, 45, true, -3.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "RArm1", 88, 39, false, -3.5f, 2.5f, -2.5f, 5, 7, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "RArm2", 76, 32, false, -3.0f, 5.5f, 2.5f, 4, 4, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "RArm3", 88, 32, false, -2.5f, 3.5f, 2.5f, 3, 2, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(rightArm, "ShoulderplateTopR", 56, 25, false, -5.5f, -2.5f, -3.5f, 2, 1, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR1", 56, 33, false, -4.5f, -1.5f, -3.5f, 1, 4, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR2", 40, 33, false, -3.5f, 1.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightArm, "ShoulderplateR3", 40, 33, false, -2.5f, 3.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(leftArm, "ShoulderL", 16, 45, true, -1.5f, -2.5f, -2.5f, 5, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "LArm1", 88, 39, true, -1.5f, 2.5f, -2.5f, 5, 7, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "LArm2", 76, 32, false, -1.0f, 5.5f, 2.5f, 4, 4, 2, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "LArm3", 88, 32, false, -0.5f, 3.5f, 2.5f, 3, 2, 1, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
		part(leftArm, "ShoulderplateTopL", 56, 25, false, 3.5f, -2.5f, -3.5f, 2, 1, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL1", 56, 33, false, 3.5f, -1.5f, -3.5f, 1, 4, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL2", 40, 33, false, 2.5f, 1.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftArm, "ShoulderplateL3", 40, 33, false, 1.5f, 3.5f, -3.5f, 1, 3, 7, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(rightLeg, "LegpanelR4", 76, 38, false, -3.0f, 0.5f, -3.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR5", 76, 42, false, -3.0f, 2.5f, -2.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "LegpanelR6", 82, 38, false, -3.0f, 4.5f, -1.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(rightLeg, "SidepanelR1", 116, 25, true, -2.5f, 0.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.4363323f);
		part(rightLeg, "SideclothR1", 116, 42, false, -2.5f, 0.5f, -2.5f, 1, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.122173f);
		part(rightLeg, "SideclothR2", 116, 34, false, -1.5f, 5.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.296706f);
		part(rightLeg, "SideclothR3", 116, 1, false, 0.4f, 8.4f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5235988f);
		part(leftLeg, "LegpanelL4", 76, 38, true, 1.0f, 0.5f, -3.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL5", 76, 42, true, 1.0f, 2.5f, -2.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "LegpanelL6", 82, 38, true, 1.0f, 4.5f, -1.5f, 2, 3, 1, 0.0f, 0.0f, 0.0f, -0.4363323f, 0.0f, 0.0f);
		part(leftLeg, "SidepanelL1", 116, 25, false, 1.5f, 0.5f, -2.5f, 1, 4, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.4363323f);
		part(leftLeg, "SideclothL1", 116, 42, false, 1.5f, 0.5f, -2.5f, 1, 5, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.122173f);
		part(leftLeg, "SideclothL2", 116, 34, false, 0.5f, 5.5f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.296706f);
		part(leftLeg, "SideclothL3", 116, 1, false, -1.4f, 8.4f, -2.5f, 1, 3, 5, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, -0.5235988f);
		return LayerDefinition.create(mesh, 128, 64);
	}
}
