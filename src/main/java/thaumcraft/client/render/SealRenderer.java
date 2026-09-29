package thaumcraft.client.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.common.lib.network.ClientboundSealPacket;

/**
 * The original {@code RenderEventHandler#drawSeals}: while the player holds a seal or the Golem Bell (an
 * {@link ISealDisplayer}), every placed seal within 16 blocks is drawn on its block face - its icon, a slowly turning
 * ring in the seal's colour (a shimmering grey when it has none), and for seals with a work area, corner brackets
 * marking that area - fading with distance. Sneak to see them through walls. A seal stopped by redstone is drawn
 * darker and sparks now and then. Restored 2026-09-29 (seals had been invisible once placed).
 *
 * <p>Differences from the original: the icon is a flat quad rather than {@code renderItemIn2D}'s thin extruded item,
 * and the area corners are laid out by position (the bracket's corner towards the area's corner) instead of the
 * original's hand-written rotation table - same result.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class SealRenderer {

	private static final ResourceLocation MIDDLE = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/seal_area.png");
	private static final ResourceLocation CORNER = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/frame_corner.png");

	private record Seal(BlockPos pos, Direction face, ResourceLocation icon, byte color, BlockPos area, boolean hasArea, boolean redstone) {
	}

	private static final Map<String, Seal> SEALS = new ConcurrentHashMap<>();

	private SealRenderer() {
	}

	private static String key(BlockPos pos, Direction face) {
		return pos.asLong() + ":" + face.ordinal();
	}

	public static void handle(ClientboundSealPacket packet) {
		if (packet.clearAll()) {
			SEALS.clear();
			return;
		}
		String key = key(packet.pos(), packet.face());
		if (packet.icon().isEmpty()) {
			SEALS.remove(key);
		} else {
			SEALS.put(key, new Seal(packet.pos(), packet.face(), packet.icon().get(), packet.color(), packet.area(), packet.hasArea(), packet.redstone()));
		}
	}

	@SubscribeEvent
	static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
		SEALS.clear();
	}

	private static boolean holdingDisplayer(LocalPlayer player) {
		return player.getMainHandItem().getItem() instanceof ISealDisplayer || player.getOffhandItem().getItem() instanceof ISealDisplayer;
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || SEALS.isEmpty() || !holdingDisplayer(player)) return;

		float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
		Vec3 cam = event.getCamera().getPosition();
		PoseStack pose = event.getPoseStack();
		pose.pushPose();
		pose.translate(-cam.x, -cam.y, -cam.z);

		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.disableCull();
		RenderSystem.depthMask(false);
		if (player.isShiftKeyDown()) RenderSystem.disableDepthTest();
		else RenderSystem.enableDepthTest();

		for (Seal seal : SEALS.values()) {
			double dis = player.distanceToSqr(Vec3.atCenterOf(seal.pos()));
			if (dis > 256.0) continue;
			float alpha = 1.0f - (float) (dis / 256.0);
			boolean dim = seal.redstone() && (mc.level.hasNeighborSignal(seal.pos()) || mc.level.hasNeighborSignal(seal.pos().relative(seal.face())));
			if (dim && mc.level.random.nextFloat() < partial / 12.0f) {
				Vec3 at = Vec3.atCenterOf(seal.pos()).add(Vec3.atLowerCornerOf(seal.face().getNormal()).scale(0.66));
				thaumcraft.client.fx.ClientFxHandlers.spawnSpark(mc.level, at.x, at.y, at.z, 2.0f, 0.8f - mc.level.random.nextFloat() * 0.2f, 0.0f, 0.0f, 1.0f);
				dim = false;
			}
			float r, g, b;
			if (seal.color() > 0) {
				int c = DyeColor.byId(seal.color() - 1).getTextureDiffuseColor();
				r = (c >> 16 & 255) / 255f;
				g = (c >> 8 & 255) / 255f;
				b = (c & 255) / 255f;
			} else {
				float t = player.tickCount + partial;
				r = 0.7f + Mth.sin((t + seal.pos().getX()) / 4.0f) * 0.1f;
				g = 0.7f + Mth.sin((t + seal.pos().getY()) / 5.0f) * 0.1f;
				b = 0.7f + Mth.sin((t + seal.pos().getZ()) / 6.0f) * 0.1f;
			}

			// the icon, on the face, a hair off the surface: the seal item's own model (vanilla extrudes flat item textures a
			// pixel deep - the original's renderItemIn2D look), falling back to the flat sprite
			pose.pushPose();
			faceTransform(pose, seal.pos(), seal.face(), 0.55f);
			float shade = dim ? 0.5f : 1.0f;
			RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);
			if (!drawItemModel(mc, pose, seal.icon(), shade, alpha)) {
				TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(seal.icon());
				quad(pose.last().pose(), 0.25f, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1(), shade, shade, shade, alpha);
			}
			pose.popPose();

			// the turning ring
			pose.pushPose();
			faceTransform(pose, seal.pos(), seal.face(), 0.51f);
			pose.mulPose(Axis.ZP.rotationDegrees(player.tickCount % 360 + partial));
			RenderSystem.setShaderTexture(0, MIDDLE);
			quad(pose.last().pose(), 0.45f, 0, 0, 1, 1, r, g, b, alpha * 0.8f);
			pose.popPose();

			if (seal.hasArea()) drawArea(pose, seal, r, g, b, alpha * 0.7f);
		}

		RenderSystem.enableDepthTest();
		RenderSystem.depthMask(true);
		RenderSystem.enableCull();
		RenderSystem.disableBlend();
		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		pose.popPose();
	}

	private static final Map<ResourceLocation, net.minecraft.world.item.ItemStack> ICON_ITEMS = new ConcurrentHashMap<>();

	/** The seal placer item whose seal has this icon. */
	private static net.minecraft.world.item.ItemStack iconItem(ResourceLocation icon) {
		return ICON_ITEMS.computeIfAbsent(icon, key -> {
			for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
				if (item instanceof thaumcraft.common.golems.ItemSealPlacer placer && key.equals(placer.getSealIcon())) {
					return new net.minecraft.world.item.ItemStack(item);
				}
			}
			return net.minecraft.world.item.ItemStack.EMPTY;
		});
	}

	/** Draws the seal item's baked quads, half a block across, centred - with our own colour/alpha and depth handling. */
	private static boolean drawItemModel(Minecraft mc, PoseStack pose, ResourceLocation icon, float shade, float alpha) {
		net.minecraft.world.item.ItemStack stack = iconItem(icon);
		if (stack.isEmpty()) return false;
		var model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
		net.minecraft.util.RandomSource rand = net.minecraft.util.RandomSource.create(42L);
		java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> quads = new java.util.ArrayList<>(model.getQuads(null, null, rand));
		for (Direction d : Direction.values()) quads.addAll(model.getQuads(null, d, rand));
		if (quads.isEmpty()) return false;
		pose.pushPose();
		pose.scale(0.5f, 0.5f, 0.5f);
		pose.translate(-0.5f, -0.5f, -0.5f);
		Matrix4f m = pose.last().pose();
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		for (var q : quads) {
			int[] v = q.getVertices();
			int stride = v.length / 4;
			float light = switch (q.getDirection()) { case UP, SOUTH -> 1.0f; case NORTH -> 0.8f; default -> 0.6f; }; // simple face shading
			for (int i = 0; i < 4; i++) {
				buf.addVertex(m, Float.intBitsToFloat(v[i * stride]), Float.intBitsToFloat(v[i * stride + 1]), Float.intBitsToFloat(v[i * stride + 2]))
						.setUv(Float.intBitsToFloat(v[i * stride + 4]), Float.intBitsToFloat(v[i * stride + 5]))
						.setColor(shade * light, shade * light, shade * light, alpha);
			}
		}
		BufferUploader.drawWithShader(buf.buildOrThrow());
		pose.popPose();
		return true;
	}

	/** Moves to the centre of {@code pos}, turned so local +Z points out of {@code face}, then {@code out} along it. */
	private static void faceTransform(PoseStack pose, BlockPos pos, Direction face, float out) {
		pose.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		switch (face) {
			case NORTH -> pose.mulPose(Axis.YP.rotationDegrees(180));
			case EAST -> pose.mulPose(Axis.YP.rotationDegrees(90));
			case WEST -> pose.mulPose(Axis.YP.rotationDegrees(-90));
			case UP -> pose.mulPose(Axis.XP.rotationDegrees(-90));
			case DOWN -> pose.mulPose(Axis.XP.rotationDegrees(90));
			default -> {
			}
		}
		pose.translate(0, 0, out);
	}

	private static void quad(Matrix4f m, float half, float u0, float v0, float u1, float v1, float r, float g, float b, float a) {
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		buf.addVertex(m, -half, -half, 0).setUv(u0, v1).setColor(r, g, b, a);
		buf.addVertex(m, half, -half, 0).setUv(u1, v1).setColor(r, g, b, a);
		buf.addVertex(m, half, half, 0).setUv(u1, v0).setColor(r, g, b, a);
		buf.addVertex(m, -half, half, 0).setUv(u0, v0).setColor(r, g, b, a);
		BufferUploader.drawWithShader(buf.buildOrThrow());
	}

	/**
	 * The work area's corner brackets: the area is the block in front of the seal, stretched (area-1) deeper along the
	 * face and (area-1) either way across it - the original's own box. Each of the box's 8 corner blocks gets a bracket
	 * on its 3 outward faces, its corner towards the box's corner.
	 */
	private static void drawArea(PoseStack pose, Seal seal, float r, float g, float b, float alpha) {
		Direction face = seal.face();
		BlockPos front = seal.pos().relative(face);
		int ax = seal.area().getX(), ay = seal.area().getY(), az = seal.area().getZ();
		int minX = front.getX(), minY = front.getY(), minZ = front.getZ();
		int maxX = minX, maxY = minY, maxZ = minZ;
		// deeper along the face normal (one way)
		if (face.getStepX() > 0) maxX += ax - 1; else if (face.getStepX() < 0) minX -= ax - 1; else { minX -= ax - 1; maxX += ax - 1; }
		if (face.getStepY() > 0) maxY += ay - 1; else if (face.getStepY() < 0) minY -= ay - 1; else { minY -= ay - 1; maxY += ay - 1; }
		if (face.getStepZ() > 0) maxZ += az - 1; else if (face.getStepZ() < 0) minZ -= az - 1; else { minZ -= az - 1; maxZ += az - 1; }

		RenderSystem.setShaderTexture(0, CORNER);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		Matrix4f m = pose.last().pose();
		BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		int[] xs = {minX, maxX}, ys = {minY, maxY}, zs = {minZ, maxZ};
		for (int ix = 0; ix < 2; ix++) {
			for (int iy = 0; iy < 2; iy++) {
				for (int iz = 0; iz < 2; iz++) {
					// the box's outer corner at this block
					Vector3f corner = new Vector3f(xs[ix] + ix, ys[iy] + iy, zs[iz] + iz);
					Vector3f centre = new Vector3f(xs[ix] + 0.5f, ys[iy] + 0.5f, zs[iz] + 0.5f);
					bracket(buf, m, centre, new Vector3f(ix == 0 ? -1 : 1, 0, 0), corner, r, g, b, alpha);
					bracket(buf, m, centre, new Vector3f(0, iy == 0 ? -1 : 1, 0), corner, r, g, b, alpha);
					bracket(buf, m, centre, new Vector3f(0, 0, iz == 0 ? -1 : 1), corner, r, g, b, alpha);
				}
			}
		}
		BufferUploader.drawWithShader(buf.buildOrThrow());
	}

	/** One bracket on the face of a block (centre {@code c}, outward {@code n}), texture corner (0,0) towards {@code corner}. */
	private static void bracket(BufferBuilder buf, Matrix4f m, Vector3f c, Vector3f n, Vector3f corner, float r, float g, float b, float a) {
		// the two in-plane axes
		Vector3f s = n.x != 0 ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0);
		Vector3f t = new Vector3f(n).cross(s);
		Vector3f base = new Vector3f(n).mul(0.49f).add(c);
		// which way along s and t the corner lies
		float ds = Math.signum(new Vector3f(corner).sub(c).dot(s));
		float dt = Math.signum(new Vector3f(corner).sub(c).dot(t));
		Vector3f v00 = new Vector3f(base).add(new Vector3f(s).mul(0.5f * ds)).add(new Vector3f(t).mul(0.5f * dt)); // at the corner
		Vector3f v10 = new Vector3f(base).add(new Vector3f(s).mul(-0.5f * ds)).add(new Vector3f(t).mul(0.5f * dt));
		Vector3f v11 = new Vector3f(base).add(new Vector3f(s).mul(-0.5f * ds)).add(new Vector3f(t).mul(-0.5f * dt));
		Vector3f v01 = new Vector3f(base).add(new Vector3f(s).mul(0.5f * ds)).add(new Vector3f(t).mul(-0.5f * dt));
		buf.addVertex(m, v00.x, v00.y, v00.z).setUv(0, 0).setColor(r, g, b, a);
		buf.addVertex(m, v10.x, v10.y, v10.z).setUv(1, 0).setColor(r, g, b, a);
		buf.addVertex(m, v11.x, v11.y, v11.z).setUv(1, 1).setColor(r, g, b, a);
		buf.addVertex(m, v01.x, v01.y, v01.z).setUv(0, 1).setColor(r, g, b, a);
	}
}
