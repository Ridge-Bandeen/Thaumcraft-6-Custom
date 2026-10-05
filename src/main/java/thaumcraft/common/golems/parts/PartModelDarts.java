package thaumcraft.common.golems.parts;

import net.minecraft.resources.ResourceLocation;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.parts.PartModel;

/**
 * The original's {@code PartModelDarts}: a dart-launcher golem in combat raises its arms to aim along its own pitch
 * (90 degrees minus the pitch, the idle swing reduced to a tenth).
 */
public class PartModelDarts extends PartModel {

	public PartModelDarts(ResourceLocation objModel, ResourceLocation objTexture, EnumAttachPoint attachPoint) {
		super(objModel, objTexture, attachPoint);
	}

	@Override
	public float preRenderArmRotationX(IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) {
		if (golem.isInCombat()) {
			inputRot = 90.0f - golem.getGolemEntity().xRotO + inputRot / 10.0f;
		}
		return inputRot;
	}

	@Override
	public float preRenderArmRotationY(IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) {
		return golem.isInCombat() ? inputRot / 10.0f : inputRot;
	}

	@Override
	public float preRenderArmRotationZ(IGolemAPI golem, float partialTicks, EnumLimbSide side, float inputRot) {
		return golem.isInCombat() ? inputRot / 10.0f : inputRot;
	}
}
