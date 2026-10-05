package thaumcraft.api.golems.parts;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.IGolemAPI;

/**
 * CLIENT ONLY. The original {@link PartModel}'s {@code preRenderObjectPart} / {@code postRenderObjectPart} moved the
 * global GL matrix; a 1.21 renderer works on a {@link PoseStack}, which a common class (PartModel is built on the server
 * too) can't mention. So a part's per-group animation lives here, keyed by its {@link PartModel#getObjModel()} - register
 * from client setup. The golem renderer calls {@link Animator#pre} before drawing each OBJ group of the part (inside a
 * pushed pose, so moving the pose moves only that group) and {@link Animator#post} after it.
 */
public final class GolemPartAnimators {

	public interface Animator {
		/** Before an OBJ group is drawn; transform {@code pose} to move it. */
		default void pre(String groupName, IGolemAPI golem, float partialTicks, PartModel.EnumLimbSide side, PoseStack pose) {
		}

		/** After an OBJ group is drawn, still inside its pose - e.g. to draw an item carried on it. */
		default void post(String groupName, IGolemAPI golem, float partialTicks, PartModel.EnumLimbSide side, PoseStack pose,
				MultiBufferSource buffers, int light) {
		}
	}

	private static final Map<ResourceLocation, Animator> ANIMATORS = new HashMap<>();

	private GolemPartAnimators() {
	}

	public static void register(ResourceLocation objModel, Animator animator) {
		ANIMATORS.put(objModel, animator);
	}

	public static Animator get(ResourceLocation objModel) {
		return ANIMATORS.get(objModel);
	}
}
