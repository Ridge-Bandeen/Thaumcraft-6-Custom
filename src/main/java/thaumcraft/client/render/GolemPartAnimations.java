package thaumcraft.client.render;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.GolemPartAnimators;
import thaumcraft.api.golems.parts.PartModel;

/**
 * The original's animated golem parts (PartModelClaws, PartModelBreakers, PartModelWheel, PartModelHauler), as
 * {@link GolemPartAnimators}: claws snap shut on a swing, breaker grinders spin (faster when they hit, slowing again),
 * wheels turn with the golem's travel ({@code GolemLegWheels.ani}), and a hauler shows its second carried stack on its
 * back.
 */
public final class GolemPartAnimations {

	private static ResourceLocation obj(String name) {
		return ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/" + name + ".obj");
	}

	private GolemPartAnimations() {
	}

	public static void register() {
		GolemPartAnimators.register(obj("golem_arms_claws"), new GolemPartAnimators.Animator() {
			@Override
			public void pre(String group, IGolemAPI golem, float pt, PartModel.EnumLimbSide side, PoseStack pose) {
				if (!group.startsWith("claw")) return;
				float f = golem.getGolemEntity().getAttackAnim(pt) * 4.1f;
				f *= f;
				pose.translate(0.0, -0.2, 0.0);
				pose.mulPose((group.endsWith("1") ? Axis.XP : Axis.XN).rotationDegrees(f));
			}
		});
		GolemPartAnimators.register(obj("golem_arms_breakers"), new GolemPartAnimators.Animator() {
			/** Per golem: the grinder's spin speed and angle. */
			private final Map<Integer, float[]> ani = new HashMap<>();

			@Override
			public void pre(String group, IGolemAPI golem, float pt, PartModel.EnumLimbSide side, PoseStack pose) {
				if (!group.equals("grinder")) return;
				var e = golem.getGolemEntity();
				float[] last = ani.getOrDefault(e.getId(), new float[] { 0.0f, 0.0f });
				float f = Math.max(last[0], e.getAttackAnim(pt) * 20.0f);
				float rot = last[1] + f;
				ani.put(e.getId(), new float[] { f * 0.99f, rot });
				pose.translate(0.0, -0.34, 0.0);
				float angle = (e.tickCount + pt) / 2.0f + rot + (side == PartModel.EnumLimbSide.LEFT ? 22 : 0);
				pose.mulPose((side == PartModel.EnumLimbSide.LEFT ? Axis.XN : Axis.XP).rotationDegrees(angle));
			}
		});
		GolemPartAnimators.register(obj("golem_legs_wheel"), new GolemPartAnimators.Animator() {
			@Override
			public void pre(String group, IGolemAPI golem, float pt, PartModel.EnumLimbSide side, PoseStack pose) {
				if (!group.equals("wheel")) return;
				float lastRot = thaumcraft.common.golems.parts.GolemLegWheels.ani.getOrDefault(golem.getGolemEntity().getId(), 0.0f);
				pose.translate(0.0, -0.375, 0.0);
				pose.mulPose(Axis.XN.rotationDegrees(lastRot));
			}
		});
		GolemPartAnimators.register(obj("golem_hauler"), new GolemPartAnimators.Animator() {
			@Override
			public void post(String group, IGolemAPI golem, float pt, PartModel.EnumLimbSide side, PoseStack pose, MultiBufferSource buffers, int light) {
				if (golem.getCarrying().size() <= 1) return;
				ItemStack stack = golem.getCarrying().get(1);
				if (stack == null || stack.isEmpty()) return;
				pose.pushPose();
				pose.scale(0.375f, 0.375f, 0.375f);
				pose.translate(0.0f, 0.33f, 0.825f);
				if (!(stack.getItem() instanceof BlockItem)) pose.translate(0.0f, 0.0f, -0.25f);
				Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.HEAD, light, OverlayTexture.NO_OVERLAY, pose, buffers,
						golem.getGolemEntity().level(), 0);
				pose.popPose();
			}
		});
	}
}
