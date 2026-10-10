package thaumcraft.client.render;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.items.armor.ItemFortressArmor;

/**
 * Real port of the true original's four custom armor models ({@code ModelFortressArmor},
 * {@code ModelKnightArmor}, {@code ModelLeaderArmor}, {@code ModelRobe}) - added 2026-09-25 after the first
 * playtest showed the fortress set drawn on the plain vanilla biped with a texture laid out for these
 * meshes (a scrambled, mis-mapped mess). Before this, every piece of these sets - fortress, crimson plate,
 * crimson praetor, crimson robe, void robe - fell back to the vanilla armor model.
 *
 * <p>The geometry is {@link CustomArmorMeshes} (generated from the originals' own constructors). This class
 * is the runtime half: modern armor rendering copies the wearer's real pose onto the model returned by
 * {@code IClientItemExtensions#getHumanoidArmorModel} via {@link HumanoidModel#copyPropertiesTo}, which
 * replaces the originals' whole {@code ModelCustomArmor#setRotationAngles} pose reimplementation (armor
 * stand / zombie / skeleton / elytra poses all come from the vanilla model for free). What is left:
 * <ul>
 * <li>which of the six real body parts show for the slot being drawn (the original's own
 * {@code setModelSlotVisible}): helm shows the head; chest the body and both arms; legs the body and both
 * legs;</li>
 * <li>the leggings-vs-rest mesh split (the originals' 0.5 vs 1.0 model instances);</li>
 * <li>the cloth swing: the originals' render() derived {@code min(a, b)} of the two leg-swing cosines to
 * drive hanging cloth pieces, which is exactly the two legs' already-applied X rotations here;</li>
 * <li>fortress-only: the set-completion pieces (book/scroll/ornaments/plates appear as more of the 3 pieces
 * are worn) and the helm's mask / goggles upgrades.</li>
 * </ul>
 *
 * <p>As the originals, the head is drawn 1.01x larger about the model origin, so the helm sits just outside the
 * wearer's own head layers. The "child" branch is vanilla's own young-model handling.
 */
public final class CustomArmorModel extends HumanoidModel<LivingEntity> {

	public enum Kind {
		FORTRESS, KNIGHT, LEADER, ROBE
	}

	private static final Map<Kind, CustomArmorModel[]> CACHE = new EnumMap<>(Kind.class);

	private final Kind kind;
	private final Map<String, ModelPart> byName = new HashMap<>();

	private CustomArmorModel(Kind kind, boolean legs) {
		super(switch (kind) {
			case FORTRESS -> CustomArmorMeshes.fortress(legs).bakeRoot();
			case KNIGHT -> CustomArmorMeshes.knight(legs).bakeRoot();
			case LEADER -> CustomArmorMeshes.leader(legs).bakeRoot();
			case ROBE -> CustomArmorMeshes.robe(legs).bakeRoot();
		});
		this.kind = kind;
	}

	/** The model to draw {@code stack} with in {@code slot}, posed like {@code original}. */
	public static HumanoidModel<?> get(Kind kind, LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
		CustomArmorModel[] pair = CACHE.computeIfAbsent(kind, k -> new CustomArmorModel[] { new CustomArmorModel(k, false), new CustomArmorModel(k, true) });
		CustomArmorModel model = pair[slot == EquipmentSlot.LEGS ? 1 : 0];
		model.prepare(entity, stack, slot, original);
		return model;
	}

	@SuppressWarnings("unchecked")
	private void prepare(LivingEntity entity, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
		HumanoidModel<LivingEntity> source = (HumanoidModel<LivingEntity>) original;
		source.copyPropertiesTo(this);

		head.visible = slot == EquipmentSlot.HEAD;
		hat.visible = false;
		body.visible = slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS;
		rightArm.visible = slot == EquipmentSlot.CHEST;
		leftArm.visible = slot == EquipmentSlot.CHEST;
		rightLeg.visible = slot == EquipmentSlot.LEGS;
		leftLeg.visible = slot == EquipmentSlot.LEGS;

		float a = source.rightLeg.xRot;
		float b = source.leftLeg.xRot;
		float c = Math.min(a, b);
		switch (kind) {
			case FORTRESS -> prepareFortress(entity, stack, slot);
			case KNIGHT -> {
				rotX("Frontcloth1", c - 0.1047198f);
				rotX("Frontcloth2", c - 0.3316126f);
				cloak(c);
			}
			case LEADER -> {
				rotX("LegClothR", a - 0.1047198f);
				rotX("LegClothL", b - 0.1047198f);
				cloak(c);
			}
			case ROBE -> {
				rotX("FrontclothR1", c - 0.1047198f);
				rotX("FrontclothL1", c - 0.1047198f);
				rotX("FrontclothR2", c - 0.3316126f);
				rotX("FrontclothL2", c - 0.3316126f);
				rotX("ClothBackR1", -c + 0.1047198f);
				rotX("ClothBackL1", -c + 0.1047198f);
				for (String name : new String[] { "ClothBackR2", "ClothBackL2", "ClothBackR3", "ClothBackL3" }) {
					rotX(name, -c + 0.2268928f);
				}
			}
		}
	}

	@Override
	public void renderToBuffer(com.mojang.blaze3d.vertex.PoseStack poseStack, com.mojang.blaze3d.vertex.VertexConsumer buffer,
			int packedLight, int packedOverlay, int color) {
		if (young) {
			super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay, color);
			return;
		}
		poseStack.pushPose();
		poseStack.scale(1.01f, 1.01f, 1.01f);
		headParts().forEach(part -> part.render(poseStack, buffer, packedLight, packedOverlay, color));
		poseStack.popPose();
		bodyParts().forEach(part -> part.render(poseStack, buffer, packedLight, packedOverlay, color));
	}

	private void cloak(float c) {
		rotX("Cloak1", -c / 2.0f + 0.1396263f);
		rotX("Cloak2", -c / 2.0f + 0.3069452f);
		rotX("Cloak3", -c / 2.0f + 0.4465716f);
	}

	private void prepareFortress(LivingEntity entity, ItemStack stack, EquipmentSlot slot) {
		int set = 0;
		for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD }) {
			if (entity.getItemBySlot(s).getItem() instanceof ItemFortressArmor) {
				set++;
			}
		}

		Integer mask = slot == EquipmentSlot.HEAD ? stack.get(ThaumcraftDataComponents.FORTRESS_MASK.get()) : null;
		boolean goggles = slot == EquipmentSlot.HEAD && Boolean.TRUE.equals(stack.get(ThaumcraftDataComponents.FORTRESS_GOGGLES.get()));
		show("Goggles", goggles);
		for (int i = 0; i < 3; i++) {
			show("Mask" + i, mask != null && mask == i);
		}

		// The original's own set-completion thresholds: 2 pieces adds the book, cloth flaps, first shoulder
		// plates and mid side panels; a full 3-piece set adds the scroll, helm ornaments/gem, the outer
		// shoulder plates and the last side panels.
		boolean two = set >= 2;
		boolean three = set >= 3;
		for (String name : new String[] { "Scroll", "OrnamentL", "OrnamentL2", "OrnamentR", "OrnamentR2", "Gemornament", "Gem",
				"ShoulderplateL2", "ShoulderplateL3", "ShoulderplateR2", "ShoulderplateR3", "SidepanelR3", "SidepanelL3" }) {
			show(name, three);
		}
		for (String name : new String[] { "Book", "flapL", "flapR", "ShoulderplateLtop", "ShoulderplateL1", "ShoulderplateRtop",
				"ShoulderplateR1", "SidepanelR2", "SidepanelL2" }) {
			show(name, two);
		}
	}

	private void rotX(String name, float angle) {
		ModelPart part = find(name);
		if (part != null) {
			part.xRot = angle;
		}
	}

	private void show(String name, boolean visible) {
		ModelPart part = find(name);
		if (part != null) {
			part.visible = visible;
		}
	}

	/** A child part by name, wherever it hangs (the six body parts' direct children); null if this mesh
	 * variant (leggings vs the rest) doesn't have it. */
	@Nullable
	private ModelPart find(String name) {
		if (byName.containsKey(name)) {
			return byName.get(name);
		}
		ModelPart found = null;
		for (ModelPart parent : new ModelPart[] { head, body, rightArm, leftArm, rightLeg, leftLeg }) {
			if (parent.hasChild(name)) {
				found = parent.getChild(name);
				break;
			}
		}
		byName.put(name, found);
		return found;
	}
}
