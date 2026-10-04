package thaumcraft.client.gui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import thaumcraft.api.crafting.Part;

/**
 * The original's blueprint picture ({@code GuiResearchPage#renderBluePrint} with its {@code BlueprintBlockAccess}): a
 * multiblock drawn in 3D - the blocks you build it from ({@code target=false}, the construct page) or, rotated a quarter
 * turn, what it becomes ({@code target=true}, the small bookmark icon). Layer 0 of a blueprint is the top. With
 * {@code slice} &gt; 0 the top {@code slice} layers are left off, which is how the page's layer toggle peels the structure.
 */
final class BlueprintRenderer {

	private final BlockState[][][] structure; // [y from bottom][x][z]

	BlueprintRenderer(Part[][][] data, boolean target) {
		int ys = data.length;
		int xs = data[0].length;
		int zs = data[0][0].length;
		Part[][][] layers = new Part[ys][][];
		for (int y = 0; y < ys; y++) layers[y] = target ? rotateRight(data[y], 3) : data[y];
		int lx = layers[0].length;
		int lz = layers[0][0].length;
		structure = new BlockState[ys][lx][lz];
		for (int y = 0; y < ys; y++) {
			for (int x = 0; x < lx; x++) {
				for (int z = 0; z < lz; z++) {
					Part p = layers[y][x][z];
					structure[ys - y - 1][x][z] = p == null ? Blocks.AIR.defaultBlockState() : (target && p.getTarget() != null ? state(p.getTarget(), p) : state(p.getSource(), null));
				}
			}
		}
	}

	int height() {
		return structure.length;
	}

	private static Part[][] rotateRight(Part[][] m, int times) {
		Part[][] r = m;
		for (int t = 0; t < times; t++) {
			int rows = r.length;
			int cols = r[0].length;
			Part[][] n = new Part[cols][rows];
			for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) n[j][rows - 1 - i] = r[i][j];
			r = n;
		}
		return r;
	}

	/** The original's convert/convertTarget: a block, a state, an item's block; a "convert to air" target keeps the source. */
	private static BlockState state(Object o, Part fallbackSource) {
		if (o instanceof Part.TagSource t) return t.shown().defaultBlockState();
		if (o instanceof BlockState s) return s;
		if (o instanceof Block b) return b.defaultBlockState();
		if (o instanceof ItemStack stack && stack.getItem() instanceof BlockItem bi) return bi.getBlock().defaultBlockState();
		if ("AIR".equals(o)) return Blocks.AIR.defaultBlockState();
		if (fallbackSource != null) return state(fallbackSource.getSource(), null);
		return Blocks.AIR.defaultBlockState();
	}

	/** Draws the structure centred at (x, y) - the original's transform: {@code scale} pixels a block, tilted 25° and
	 * turned -45°. */
	void render(GuiGraphics graphics, float x, float y, float z, float scale, float rotX, float rotY, int slice) {
		int ys = structure.length;
		int xs = structure[0].length;
		int zs = structure[0][0].length;
		PoseStack pose = graphics.pose();
		pose.pushPose();
		pose.translate(x - xs / 2f, y - (float) Math.sqrt(ys * ys + xs * xs + zs * zs) / 2f, z + Math.max(ys, Math.max(xs, zs)));
		pose.scale(scale, -scale, scale);
		pose.mulPose(Axis.XP.rotationDegrees(rotX));
		pose.mulPose(Axis.YP.rotationDegrees(rotY));
		pose.translate(zs / -2f, ys / -2f, xs / -2f);
		Lighting.setupFor3DItems();
		MultiBufferSource.BufferSource buffers = graphics.bufferSource();
		// the scale above mirrors Y, which flips face winding - draw without culling so faces don't vanish
		VertexConsumer cutout = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
		MultiBufferSource noCull = type -> cutout;
		var dispatcher = Minecraft.getInstance().getBlockRenderer();
		for (int h = 0; h < ys - slice; h++) {
			for (int l = 0; l < xs; l++) {
				for (int w = 0; w < zs; w++) {
					BlockState state = structure[h][l][w];
					if (state.isAir()) continue;
					pose.pushPose();
					pose.translate(l, h, w);
					if (state.getBlock() instanceof LiquidBlock) {
						fluidCube(pose, cutout, state);
					} else {
						dispatcher.renderSingleBlock(state, pose, noCull, 0xF000F0, OverlayTexture.NO_OVERLAY);
					}
					pose.popPose();
				}
			}
		}
		graphics.flush();
		Lighting.setupFor3DItems();
		pose.popPose();
	}

	/** Fluids have no block model; the original's block access let the fluid renderer draw its lava core. A plain cube of
	 * the fluid's still texture stands in. */
	private static void fluidCube(PoseStack pose, VertexConsumer vc, BlockState state) {
		var fluid = state.getFluidState().getType();
		if (fluid == Fluids.EMPTY) fluid = Fluids.LAVA;
		var ext = IClientFluidTypeExtensions.of(fluid);
		TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture());
		int c = ext.getTintColor();
		int r = c >> 16 & 255, g = c >> 8 & 255, b = c & 255;
		float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
		var m = pose.last();
		float[][] faces = {
				{0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0, 0, 1, 0}, // top
				{0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0}, // bottom
				{0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 0, 0, -1}, // north
				{0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1}, // south
				{0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0}, // west
				{1, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1, 0, 0}, // east
		};
		float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
		for (float[] f : faces) {
			for (int i = 0; i < 4; i++) {
				vc.addVertex(m, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).setColor(r, g, b, 255).setUv(uv[i][0], uv[i][1])
						.setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(m, f[12], f[13], f[14]);
			}
		}
	}
}
