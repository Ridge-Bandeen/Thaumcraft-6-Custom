package thaumcraft.client.lib.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.items.IArchitect;

/**
 * The original's architect overlay ({@code WandRenderingHandler#handleArchitectOverlay}, drawn from
 * {@code RenderEventHandler}): while holding an {@link IArchitect} - a caster with a Plan focus, or the Elemental Shovel
 * while sneaking - the blocks it would work on are outlined (a faint face frame, brighter corner pieces where the
 * outline turns), seen through walls, and pulsing arrows show the axes the misc caster key would change. Items that
 * use the block highlight (the shovel) replace the vanilla outline while they show anything.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ArchitectOverlay {

	private static final ResourceLocation CFRAME = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/frame_corner.png");
	private static final ResourceLocation SFRAME = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/frame_side.png");
	private static final ResourceLocation ARROWS = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/architect_arrows.png");
	/** For each face, which of the eight cube corners its four frame corners are, and how far each is turned. */
	private static final int[][] MOS = { { 4, 5, 6, 7 }, { 0, 1, 2, 3 }, { 0, 1, 4, 5 }, { 2, 3, 6, 7 }, { 0, 2, 4, 6 }, { 1, 3, 5, 7 } };
	private static final int[][] ROTMAT = { { 0, 90, 270, 180 }, { 270, 180, 0, 90 }, { 180, 90, 270, 0 }, { 0, 270, 90, 180 }, { 270, 180, 0, 90 }, { 180, 270, 90, 0 } };

	private static int lastHash;
	private static List<BlockPos> blocks = new ArrayList<>();
	private static Set<BlockPos> blockSet = new HashSet<>();
	private static final Map<BlockPos, boolean[]> CORNERS = new HashMap<>();

	private ArchitectOverlay() {
	}

	private static ItemStack heldArchitect(Player player) {
		if (player.getMainHandItem().getItem() instanceof IArchitect) return player.getMainHandItem();
		if (player.getOffhandItem().getItem() instanceof IArchitect) return player.getOffhandItem();
		return ItemStack.EMPTY;
	}

	/** The blocks for this target, recomputed when the target, side or 5-tick step changes, as the original. */
	private static List<BlockPos> blocksFor(ItemStack stack, Player player, BlockHitResult target) {
		BlockPos p = target.getBlockPos();
		int hash = (p.getX() + "" + p.getY() + "" + p.getZ() + "" + target.getDirection() + "" + player.tickCount / 5 + stack.getItem()).hashCode();
		if (hash != lastHash) {
			lastHash = hash;
			CORNERS.clear();
			List<BlockPos> found = ((IArchitect) stack.getItem()).getArchitectBlocks(stack, player.level(), p, target.getDirection(), player);
			blocks = found != null ? found : new ArrayList<>();
			blockSet = new HashSet<>(blocks);
		}
		return blocks;
	}

	@javax.annotation.Nullable
	private static BlockHitResult target(ItemStack stack, Player player) {
		HitResult hit = ((IArchitect) stack.getItem()).getArchitectMOP(stack, player.level(), player);
		return hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK ? b : null;
	}

	/** A highlighting architect (the shovel) showing blocks replaces the vanilla outline. */
	@SubscribeEvent
	static void onHighlight(RenderHighlightEvent.Block event) {
		Player player = Minecraft.getInstance().player;
		if (player == null) return;
		ItemStack stack = heldArchitect(player);
		if (stack.isEmpty() || !((IArchitect) stack.getItem()).useBlockHighlight(stack)) return;
		BlockHitResult target = target(stack, player);
		if (target != null && !blocksFor(stack, player, target).isEmpty()) event.setCanceled(true);
	}

	@SubscribeEvent
	static void onRenderLevel(RenderLevelStageEvent event) {
		if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.player;
		if (player == null || mc.level == null) return;
		ItemStack stack = heldArchitect(player);
		if (stack.isEmpty()) return;
		BlockHitResult target = target(stack, player);
		if (target == null) return;
		List<BlockPos> list = blocksFor(stack, player, target);
		if (list.isEmpty()) return;
		IArchitect architect = (IArchitect) stack.getItem();
		Vec3 cam = event.getCamera().getPosition();

		RenderSystem.disableDepthTest();
		RenderSystem.depthMask(false);
		RenderSystem.disableCull();
		RenderSystem.enableBlend();
		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

		drawAxis(target.getBlockPos(), cam, player.tickCount,
				architect.showAxis(stack, player.level(), player, target.getDirection(), IArchitect.EnumAxis.X),
				architect.showAxis(stack, player.level(), player, target.getDirection(), IArchitect.EnumAxis.Y),
				architect.showAxis(stack, player.level(), player, target.getDirection(), IArchitect.EnumAxis.Z));
		drawFrames(list, cam, false);
		drawFrames(list, cam, true);

		RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
		RenderSystem.disableBlend();
		RenderSystem.enableCull();
		RenderSystem.depthMask(true);
		RenderSystem.enableDepthTest();
	}

	private static boolean connected(BlockPos p) {
		return blockSet.contains(p);
	}

	/** The original's getConnectedSides: for each of the eight cube corners, whether the outline turns there. */
	private static boolean[] corners(BlockPos p) {
		return CORNERS.computeIfAbsent(p, pos -> new boolean[] {
				!connected(pos.offset(-1, 0, 0)) && !connected(pos.offset(0, 0, -1)) && !connected(pos.offset(0, 1, 0)),
				!connected(pos.offset(1, 0, 0)) && !connected(pos.offset(0, 0, -1)) && !connected(pos.offset(0, 1, 0)),
				!connected(pos.offset(-1, 0, 0)) && !connected(pos.offset(0, 0, 1)) && !connected(pos.offset(0, 1, 0)),
				!connected(pos.offset(1, 0, 0)) && !connected(pos.offset(0, 0, 1)) && !connected(pos.offset(0, 1, 0)),
				!connected(pos.offset(-1, 0, 0)) && !connected(pos.offset(0, 0, -1)) && !connected(pos.offset(0, -1, 0)),
				!connected(pos.offset(1, 0, 0)) && !connected(pos.offset(0, 0, -1)) && !connected(pos.offset(0, -1, 0)),
				!connected(pos.offset(-1, 0, 0)) && !connected(pos.offset(0, 0, 1)) && !connected(pos.offset(0, -1, 0)),
				!connected(pos.offset(1, 0, 0)) && !connected(pos.offset(0, 0, 1)) && !connected(pos.offset(0, -1, 0)) });
	}

	/** Every outer face of the selection: the faint side frame ({@code corner} false) or the corner pieces. */
	private static void drawFrames(List<BlockPos> list, Vec3 cam, boolean cornerPass) {
		RenderSystem.setShaderTexture(0, cornerPass ? CFRAME : SFRAME);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		PoseStack pose = new PoseStack();
		for (BlockPos pos : list) {
			boolean[] bits = corners(pos);
			for (Direction face : Direction.values()) {
				if (connected(pos.relative(face))) continue;
				pose.pushPose();
				pose.translate(pos.getX() + 0.5 - cam.x, pos.getY() + 0.5 - cam.y, pos.getZ() + 0.5 - cam.z);
				// the original's turn onto the face: 90 degrees about (-y, x, -z) of the face's direction, then out half a block
				pose.mulPose(new Quaternionf().rotationAxis(Mth.HALF_PI, -face.getStepY(), face.getStepX(), -face.getStepZ()));
				pose.translate(0.0, 0.0, face.getStepZ() < 0 ? -0.5 : 0.5);
				pose.mulPose(new Quaternionf().rotationZ(-Mth.HALF_PI));
				if (!cornerPass) {
					quad(buffer, pose.last().pose(), 1.0f, 1.0f, 1.0f, 0.1f);
				} else {
					for (int a = 0; a < 4; a++) {
						if (!bits[MOS[face.ordinal()][a]]) continue;
						pose.pushPose();
						pose.mulPose(new Quaternionf().rotationZ(ROTMAT[face.ordinal()][a] * Mth.DEG_TO_RAD));
						quad(buffer, pose.last().pose(), 1.0f, 1.0f, 1.0f, 0.66f);
						pose.popPose();
					}
				}
				pose.popPose();
			}
		}
		MeshData mesh = buffer.build();
		if (mesh != null) BufferUploader.drawWithShader(mesh);
	}

	/** The original's drawArchitectAxis: crossed arrow quads along each axis the key would change, in a slowly shifting blue. */
	private static void drawAxis(BlockPos pos, Vec3 cam, int ticks, boolean dx, boolean dy, boolean dz) {
		if (!dx && !dy && !dz) return;
		float r = Mth.sin(ticks / 4.0f + pos.getX()) * 0.2f + 0.3f;
		float g = Mth.sin(ticks / 3.0f + pos.getY()) * 0.2f + 0.3f;
		float b = Mth.sin(ticks / 2.0f + pos.getZ()) * 0.2f + 0.8f;
		RenderSystem.setShaderTexture(0, ARROWS);
		BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		PoseStack pose = new PoseStack();
		pose.translate(pos.getX() + 0.5 - cam.x, pos.getY() + 0.5 - cam.y, pos.getZ() + 0.5 - cam.z);
		pose.mulPose(new Quaternionf().rotationX(Mth.HALF_PI));
		if (dz) {
			pose.pushPose();
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.mulPose(new Quaternionf().rotationY(Mth.HALF_PI));
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.popPose();
		}
		if (dx) {
			pose.pushPose();
			pose.mulPose(new Quaternionf().rotationZ(Mth.HALF_PI));
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.mulPose(new Quaternionf().rotationY(Mth.HALF_PI));
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.popPose();
		}
		if (dy) {
			pose.pushPose();
			pose.mulPose(new Quaternionf().rotationX(Mth.HALF_PI));
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.mulPose(new Quaternionf().rotationY(Mth.HALF_PI));
			quad(buffer, pose.last().pose(), r, g, b, 1.0f);
			pose.popPose();
		}
		MeshData mesh = buffer.build();
		if (mesh != null) BufferUploader.drawWithShader(mesh);
	}

	/** The original's renderQuadCentered: a unit quad on the XY plane about the origin, the whole texture. */
	private static void quad(BufferBuilder buffer, Matrix4f m, float r, float g, float b, float a) {
		buffer.addVertex(m, -0.5f, 0.5f, 0.0f).setUv(1.0f, 1.0f).setColor(r, g, b, a);
		buffer.addVertex(m, 0.5f, 0.5f, 0.0f).setUv(1.0f, 0.0f).setColor(r, g, b, a);
		buffer.addVertex(m, 0.5f, -0.5f, 0.0f).setUv(0.0f, 0.0f).setColor(r, g, b, a);
		buffer.addVertex(m, -0.5f, -0.5f, 0.0f).setUv(0.0f, 1.0f).setColor(r, g, b, a);
	}
}
