package thaumcraft.client.render;

import java.util.Map;

import org.joml.Matrix4f;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.neoforged.neoforge.client.model.obj.ObjLoader;
import net.neoforged.neoforge.client.model.obj.ObjModel;
import net.neoforged.neoforge.client.model.renderable.CompositeRenderable;
import thaumcraft.Thaumcraft;
import thaumcraft.common.tiles.crafting.TileGolemBuilder;

/**
 * Renders {@link TileGolemBuilder}'s real OBJ mesh and press-ram animation - restored 2026-09-16, using the
 * same real modern OBJ-loading path {@link RenderTurretCrossbowAdvanced} already established (see that
 * class's own doc comment for the full real API details - {@link ObjLoader}/{@link
 * StandaloneGeometryBakingContext}/{@link CompositeRenderable}). Genuinely simpler than that one: only 4 real
 * named parts ({@code table}/{@code anvil}/{@code box}/{@code press}), only 1 of which animates. As the original, the
 * model is centred on the block and turned by its facing: it spans 2x2 blocks (the press, the table beside it, the
 * cauldron - "box" - and the anvil diagonally), lying over the invisible placeholders the ritual made. (An earlier
 * pass here drew it from the block corner, half a block off, and unturned.)
 *
 * <p>Real, checked-not-assumed difference from the crossbow case: {@code golembuilder.mtl} DOES already exist
 * as a real original asset (unlike {@code crossbow_advanced.obj}'s own missing one) - but its own real
 * {@code map_Ka}/{@code map_Kd} lines reference a stale absolute Windows path from the original author's own
 * machine, not a real Minecraft resource location, so this still supplies its own minimal synthetic {@code
 * .mtl} override rather than risk the real file's own stale path tripping up NeoForge's modern parser.
 *
 * <p>Real per-part animation: {@code press} bobs vertically by a real {@code sin(radians(press)) * 0.625}
 * formula, matching the true original's own exact formula, off {@link TileGolemBuilder#press} (a real
 * client-only field, computed off already-synced {@link TileGolemBuilder#cost}/{@link TileGolemBuilder#golem}
 * state, restored alongside this renderer). {@code table}/{@code anvil}/{@code box} render with no transform
 * at all (matches the true original's own real {@code renderAllExcept("press")} call exactly). A real static
 * decorative "molten glow" quad is also drawn using vanilla's own real lava particle sprite, as the original's
 * {@code renderQuadFromIcon} (same corners and UVs, lit as its brightness 200: block light 200).
 * The texture is not V-flipped: the original's loader flipped V, but this renderer flipped the texture matrix back.
 * The press's steam is spawned by {@link TileGolemBuilder#tickPress}.
 */
public class TileGolemBuilderRenderer implements BlockEntityRenderer<TileGolemBuilder> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/blocks/golembuilder.png");
	private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/block/golembuilder.obj");
	private static final ResourceLocation MTL_OVERRIDE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "models/block/golembuilder_override.mtl");

	private final CompositeRenderable renderable;

	public TileGolemBuilderRenderer(BlockEntityRendererProvider.Context context) {
		ObjModel model = ObjLoader.INSTANCE.loadModel(new ObjModel.ModelSettings(MODEL, true, true, false, true, MTL_OVERRIDE.toString()));
		// NeoForge's ObjModel#bakeRenderable turns the material into "textures/<path>.png" itself, so this is the short
		// (sprite-style) name - a full path became textures/textures/....png.png and drew the missing texture (found 2026-10-01).
		var bakingContext = StandaloneGeometryBakingContext.create(Map.of("#texture", ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "blocks/golembuilder")));
		renderable = model.bakeRenderable(bakingContext);
	}

	@Override
	public void render(TileGolemBuilder tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		poseStack.pushPose();
		poseStack.translate(0.5, 0.0, 0.5);
		switch (tile.getBlockState().getOptionalValue(thaumcraft.common.blocks.crafting.BlockGolemBuilder.FACING)
				.orElse(net.minecraft.core.Direction.NORTH)) {
			case EAST -> poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
			case WEST -> poseStack.mulPose(Axis.YP.rotationDegrees(90.0f));
			case SOUTH -> poseStack.mulPose(Axis.YP.rotationDegrees(180.0f));
			default -> { }
		}

		float bob = (float) Math.sin(Math.toRadians(tile.press)) * 0.625f;
		Matrix4f pressTransform = new Matrix4f().translate(0.0f, -bob, 0.0f);
		CompositeRenderable.Transforms transforms = CompositeRenderable.Transforms.of(ImmutableMap.of("press", pressTransform));

		renderable.render(poseStack, bufferSource, texture -> RenderType.entityCutout(texture), packedLight, packedOverlay, partialTick, transforms);

		// the glowing lava in the cauldron, in the same turned frame (the original drew it before popping)
		poseStack.pushPose();
		poseStack.translate(-0.3125, 0.625, 1.3125);
		poseStack.mulPose(Axis.XP.rotationDegrees(-90.0f));
		poseStack.scale(0.625f, 0.625f, 0.625f);

		TextureAtlasSprite icon = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(Blocks.LAVA.defaultBlockState());
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.translucent());
		int light = 200; // the original's brightness 200 (block light 200, sky 0: the same packing as 1.21)
		PoseStack.Pose pose = poseStack.last();
		addVertex(consumer, pose, 0.0f, 0.0f, icon.getU1(), icon.getV1(), light);
		addVertex(consumer, pose, 1.0f, 0.0f, icon.getU0(), icon.getV1(), light);
		addVertex(consumer, pose, 1.0f, 1.0f, icon.getU0(), icon.getV0(), light);
		addVertex(consumer, pose, 0.0f, 1.0f, icon.getU1(), icon.getV0(), light);

		poseStack.popPose();
		poseStack.popPose();
	}

	/** The model reaches a block past this one on two sides (and up one), so it must not be culled with the block. */
	@Override
	public net.minecraft.world.phys.AABB getRenderBoundingBox(TileGolemBuilder tile) {
		return new net.minecraft.world.phys.AABB(tile.getBlockPos()).inflate(1.5, 0.0, 1.5).expandTowards(0.0, 1.5, 0.0);
	}

	private static void addVertex(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float u, float v, int packedLight) {
		consumer.addVertex(pose, x, y, 0.0f)
				.setColor(0xFFFFFFFF)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(packedLight)
				.setNormal(pose, 0.0f, 0.0f, 1.0f);
	}
}
