package thaumcraft.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.essentia.TileTubeBuffer;

/**
 * Renders {@link TileTubeBuffer}'s real per-side choke indicators - restored 2026-09-16, a real,
 * previously-undocumented visual gap (this tile had no renderer at all before, found during the
 * block-entity-renderer sweep - see the Custom Mob Models/Renderers TODO entry). Real, faithful translation
 * of the true original's own real {@code TileTubeBufferRenderer}: one real rod (reusing {@link
 * ModelTubeValve}, no ring) per real open, choked, and actually-connected side - blue for a soft choke
 * (partially restricted), red for a full choke (blocked) - matching the true original's own real per-side
 * loop and colour convention exactly.
 */
public class TileTubeBufferRenderer implements BlockEntityRenderer<TileTubeBuffer> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/models/valve.png");
	private static final int SOFT_CHOKE_TINT = FastColor.ARGB32.color(255, 77, 77, 255);
	private static final int FULL_CHOKE_TINT = FastColor.ARGB32.color(255, 255, 77, 77);

	private final ModelTubeValve model = new ModelTubeValve();

	public TileTubeBufferRenderer(BlockEntityRendererProvider.Context context) {
	}

	@Override
	public void render(TileTubeBuffer tile, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
		if (tile.getLevel() == null) return;
		VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityCutout(TEXTURE));

		for (Direction dir : Direction.values()) {
			int choked = tile.getChokedSide(dir);
			if (choked == 0 || !tile.isSideOpen(dir)) continue;
			if (!(tile.getLevel().getBlockEntity(tile.getBlockPos().relative(dir)) instanceof IEssentiaTransport)) continue;

			poseStack.pushPose();
			poseStack.translate(0.5, 0.5, 0.5);
			ModelTubeValve.orientToFacing(poseStack, dir.getOpposite());
			poseStack.scale(2.0f, 1.0f, 2.0f);
			poseStack.translate(0.0, -0.5, 0.0);

			model.renderRod(poseStack, consumer, packedLight, choked == 2 ? FULL_CHOKE_TINT : SOFT_CHOKE_TINT);

			poseStack.popPose();
		}
	}
}
