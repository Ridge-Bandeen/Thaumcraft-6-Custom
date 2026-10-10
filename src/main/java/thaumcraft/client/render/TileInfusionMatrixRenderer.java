package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import org.joml.Vector3f;

import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.tiles.crafting.TileInfusionMatrix;

/**
 * Draws the Infusion Matrix as the original {@code TileInfusionMatrixRenderer} (the block itself is invisible, as the
 * original's was; its JSON model is only the item's):
 * <ul>
 * <li>eight cubes ({@code ModelCube}: a mirrored 16-pixel box, scaled 0.45) at the corners a quarter-block out from the
 * centre, each turned a quarter-turn about the axes it sits on the positive side of. While active the whole cluster
 * spins about Y and tilts 35 degrees on X and 45 on Z, all scaled by the tile's eased {@link TileInfusionMatrix#startUp},
 * and each cube wobbles by up to 0.01 x startUp x instability (instability grows with the craft count, more when the
 * stability is negative);</li>
 * <li>while active, a second pass of the same cubes on the texture's lower half, additive and full-bright in purple
 * (0.8, 0.1, 1) with a flickering alpha of up to 0.3 x startUp;</li>
 * <li>while crafting, the halo: 20 spikes (10 on fast graphics), each turned further from the last, growing over the
 * first 50 ticks of the craft count and fading as it climbs to 500.</li>
 * </ul>
 * The texture follows the altar's tier: {@code infuser_ancient} or {@code infuser_eldritch} when the pillar below the
 * matrix's corner ({@code -1, -2, -1}) is an ancient or eldritch pillar. Block breaking shows no crack overlay (the
 * original drew one on the cubes).
 */
public class TileInfusionMatrixRenderer implements BlockEntityRenderer<TileInfusionMatrix> {

	private static final ResourceLocation TEX_NORMAL = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/infuser_normal.png");
	private static final ResourceLocation TEX_ANCIENT = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/infuser_ancient.png");
	private static final ResourceLocation TEX_ELDRITCH = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/infuser_eldritch.png");

	private final ModelPart cube;
	private final ModelPart cubeOver;

	public TileInfusionMatrixRenderer(BlockEntityRendererProvider.Context context) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		root.addOrReplaceChild("cube", CubeListBuilder.create().mirror().texOffs(0, 0).addBox(-8.0f, -8.0f, -8.0f, 16, 16, 16), PartPose.ZERO);
		root.addOrReplaceChild("over", CubeListBuilder.create().mirror().texOffs(0, 32).addBox(-8.0f, -8.0f, -8.0f, 16, 16, 16), PartPose.ZERO);
		ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
		cube = baked.getChild("cube");
		cubeOver = baked.getChild("over");
	}

	@Override
	public void render(TileInfusionMatrix tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		Minecraft mc = Minecraft.getInstance();
		float ticks = (mc.player != null ? mc.player.tickCount : 0) + partialTick;
		float startup = tile.startUp;
		int craftCount = tile.getClientCraftCount();

		ResourceLocation texture = TEX_NORMAL;
		if (tile.getLevel() != null) {
			BlockState below = tile.getLevel().getBlockState(tile.getBlockPos().offset(-1, -2, -1));
			if (below.is(BlocksTC.pillarAncient.get())) texture = TEX_ANCIENT;
			if (below.is(BlocksTC.pillarEldritch.get())) texture = TEX_ELDRITCH;
		}
		float inst = tile.stability;
		float instability = Math.min(6.0f, 1.0f + (inst < 0.0f ? -inst * 0.66f : 1.0f) * (Math.min(craftCount, 50) / 50.0f));

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		poseStack.mulPose(Axis.YP.rotationDegrees(ticks % 360.0f * startup));
		poseStack.mulPose(Axis.XP.rotationDegrees(35.0f * startup));
		poseStack.mulPose(Axis.ZP.rotationDegrees(45.0f * startup));

		VertexConsumer solid = bufferSource.getBuffer(RenderType.entityCutout(texture));
		VertexConsumer glow = tile.active ? bufferSource.getBuffer(ShieldRunesFx.additive(texture)) : null;
		for (int a = 0; a < 2; a++) {
			for (int b = 0; b < 2; b++) {
				for (int c = 0; c < 2; c++) {
					float b1 = 0.0f;
					float b2 = 0.0f;
					float b3 = 0.0f;
					if (tile.active) {
						b1 = Mth.sin((ticks + a * 10) / 15.0f) * 0.01f * startup * instability;
						b2 = Mth.sin((ticks + b * 10) / 14.0f) * 0.01f * startup * instability;
						b3 = Mth.sin((ticks + c * 10) / 13.0f) * 0.01f * startup * instability;
					}
					poseStack.pushPose();
					poseStack.translate(b1 + (a == 0 ? -1 : 1) * 0.25f, b2 + (b == 0 ? -1 : 1) * 0.25f, b3 + (c == 0 ? -1 : 1) * 0.25f);
					if (a > 0) poseStack.mulPose(Axis.XP.rotationDegrees(90.0f));
					if (b > 0) poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
					if (c > 0) poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
					poseStack.scale(0.45f, 0.45f, 0.45f);
					cube.render(poseStack, solid, packedLight, packedOverlay);
					if (glow != null) {
						float alpha = (Mth.sin((ticks + a * 2 + b * 3 + c * 4) / 4.0f) * 0.1f + 0.2f) * startup;
						cubeOver.render(poseStack, glow, 0xF000F0, OverlayTexture.NO_OVERLAY,
								FastColor.ARGB32.color((int) (alpha * 255.0f), (int) (0.8f * 255.0f), (int) (0.1f * 255.0f), 255));
					}
					poseStack.popPose();
				}
			}
		}
		poseStack.popPose();

		if (tile.crafting) {
			drawHalo(craftCount, poseStack, bufferSource);
		}
	}

	/** The original's drawHalo: spikes whose turns accumulate from one to the next, seeded with java.util.Random(245). */
	private void drawHalo(int count, PoseStack poseStack, MultiBufferSource bufferSource) {
		int spikes = Minecraft.getInstance().options.graphicsMode().get() != GraphicsStatus.FAST ? 20 : 10;
		float f1 = count / 500.0f;
		float ramp = 20.0f / (Math.min(count, 50) / 50.0f);
		java.util.Random random = new java.util.Random(245L);
		VertexConsumer vc = bufferSource.getBuffer(RenderType.dragonRays());
		int apexColor = FastColor.ARGB32.color((int) (255.0f * (1.0f - f1)), 255, 255, 255);
		Vector3f apex = new Vector3f(0.0f, 0.0f, 0.0f);

		poseStack.pushPose();
		poseStack.translate(0.5, 0.5, 0.5);
		for (int i = 0; i < spikes; i++) {
			poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0f));
			poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0f));
			poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0f));
			poseStack.mulPose(Axis.XP.rotationDegrees(random.nextFloat() * 360.0f));
			poseStack.mulPose(Axis.YP.rotationDegrees(random.nextFloat() * 360.0f));
			poseStack.mulPose(Axis.ZP.rotationDegrees(random.nextFloat() * 360.0f + f1 * 360.0f));
			float length = (random.nextFloat() * 20.0f + 5.0f) / ramp;
			float width = (random.nextFloat() * 2.0f + 1.0f) / ramp;
			Vector3f v1 = new Vector3f(-0.866f * width, length, -0.5f * width);
			Vector3f v2 = new Vector3f(0.866f * width, length, -0.5f * width);
			Vector3f v3 = new Vector3f(0.0f, length, width);
			PoseStack.Pose pose = poseStack.last();
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v1).setColor(0x00FF00FF);
			vc.addVertex(pose, v2).setColor(0x00FF00FF);
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v2).setColor(0x00FF00FF);
			vc.addVertex(pose, v3).setColor(0x00FF00FF);
			vc.addVertex(pose, apex).setColor(apexColor);
			vc.addVertex(pose, v3).setColor(0x00FF00FF);
			vc.addVertex(pose, v1).setColor(0x00FF00FF);
		}
		poseStack.popPose();
	}

	@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox(TileInfusionMatrix tile) {
		// the halo reaches past the block while crafting
		return new net.minecraft.world.phys.AABB(tile.getBlockPos()).inflate(1.5);
	}
}
