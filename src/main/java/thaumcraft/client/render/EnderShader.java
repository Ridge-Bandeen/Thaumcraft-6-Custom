package thaumcraft.client.render;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import thaumcraft.Thaumcraft;

/**
 * Real port of the true original's own real {@code ender.vert}/{@code ender.frag} GLSL shader pair - a
 * procedural "swirling starfield" painted purely from view direction (camera-relative fragment position plus
 * real {@code Yaw}/{@code Pitch} uniforms), completely independent of the drawn mesh's own shape or UVs - the
 * true original's own real {@code TileHoleRenderer}/{@code TileVoidSiphonRenderer}/{@code RenderFluxRift} all
 * shared this exact same shader, just fed different geometry. Restored 2026-09-17, the first genuinely new
 * custom-shader port this whole session has needed (every earlier shader-based effect - {@code MIRROR}'s own
 * portal tunnel - reused an existing VANILLA shader wholesale; this one has no vanilla equivalent to lean on).
 *
 * <p>Real modern shader-authoring technique, verified against the real decompiled/vanilla shader sources
 * before writing anything (not guessed): a plain JSON program definition
 * ({@code assets/thaumcraft/shaders/core/ender.json}) declares samplers/uniforms, real {@code #version 150}
 * GLSL replaces the true original's own real {@code #version 120} syntax
 * ({@code varying}→{@code in}/{@code out}, {@code texture2D}→{@code texture}, {@code gl_FragColor}→an
 * explicit {@code out vec4 fragColor}) - registered via NeoForge's real {@link RegisterShadersEvent}. The
 * true original's own uniform literally named {@code texture} had to be renamed ({@code Sampler0}, matching
 * vanilla's own real naming convention) since {@code texture} is a reserved built-in function name in modern
 * GLSL. {@code Yaw}/{@code Pitch}/{@code Time} are real custom uniforms (no vanilla equivalent exists -
 * vanilla's own reserved {@code GameTime} uniform is a 0-1 fraction of the CURRENT DAY, checked directly
 * against {@link RenderSystem#setShaderGameTime} before ruling it out as unusable here) set manually each
 * draw via {@link #render}, which draws immediately (bypassing the standard {@code MultiBufferSource}
 * batching pipeline entirely) specifically because custom uniforms have no supported hook into that pipeline's
 * own {@code RenderStateShard} system - the same real technique vanilla's own sky/cloud rendering uses for
 * comparable "immediate, camera-relative, shader-driven" effects.
 *
 * <p>The shader's own real math ignores mesh UVs entirely (computed purely from view direction), so every
 * caller only ever needs to emit plain {@link DefaultVertexFormat#POSITION} geometry - no texture coordinates,
 * colour, or normals needed at all.
 */
public final class EnderShader {

	private static final ResourceLocation SHADER_LOCATION = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "ender");
	private static final ResourceLocation END_PORTAL_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/end_portal.png");

	@Nullable
	private static ShaderInstance instance;

	private EnderShader() {
	}

	public static void register(RegisterShadersEvent event) throws IOException {
		event.registerShader(new ShaderInstance(event.getResourceProvider(), SHADER_LOCATION, DefaultVertexFormat.POSITION), shader -> instance = shader);
	}

	/** Begins an immediate, position-only draw through the ender shader - reuses the true original's own real
	 * {@code textures/entity/end_portal.png} texture (the true original's own real choice too, not a
	 * Thaumcraft-specific asset). Returns {@code null} if the shader hasn't loaded yet (a resource-reload
	 * failure, or too early in startup) - callers should skip drawing entirely in that case. */
	@Nullable
	static BufferBuilder begin(VertexFormat.Mode mode) {
		return begin(mode, false);
	}

	/** As {@link #begin(VertexFormat.Mode)}, choosing the true original's own blend function: {@code additive}
	 * is its {@code glBlendFunc(GL_SRC_ALPHA, GL_ONE)} (the shader's output alpha is always 1, so that is
	 * dst+src - a soft glow that never hides what is behind it), otherwise its default
	 * {@code (GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)} (with alpha 1, a plain opaque replace). The two look
	 * completely different here, which is what makes a Flux Rift's outer layers read as a glow around its
	 * solid core. */
	@Nullable
	static BufferBuilder begin(VertexFormat.Mode mode, boolean additive) {
		if (instance == null) return null;
		var player = Minecraft.getInstance().player;
		if (player == null) return null;

		float yaw = (float) Math.toRadians(player.getYRot());
		float pitch = (float) -Math.toRadians(player.getXRot());
		float time = player.tickCount;

		if (thaumcraft.common.config.ThaumcraftConfig.client(thaumcraft.common.config.ThaumcraftConfig.DISABLE_SHADERS)) {
			// the disableShaders option: the same shapes, flat starless void
			RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionShader);
			RenderSystem.setShaderColor(0.06f, 0.03f, 0.1f, 1.0f);
		} else {
			RenderSystem.setShaderTexture(0, END_PORTAL_TEXTURE);
			RenderSystem.setShader(() -> instance);
			setUniform("Yaw", yaw);
			setUniform("Pitch", pitch);
			setUniform("Time", time);
		}
		// Every caller draws thin, camera-facing-ish quads from both sides of a wall/tunnel - the shader's
		// own output is fully opaque either way (see this class's own doc comment), so there's no reason to
		// respect whatever the ambient cull state happened to be left at by unrelated rendering just before.
		RenderSystem.disableCull();

		// Vanilla's own render types each enable depth testing for themselves and DISABLE it again on clear
		// (DepthTestStateShard), so by the time a block-entity/entity renderer runs, the ambient state is
		// "depth test off" - an immediate-mode draw like this one must turn it on itself, or it paints over
		// everything already in the world (found 2026-09-25: the void siphon's panels drew on top of its own
		// brass arches and base plate). Same LEQUAL function the true original's fixed-function pipeline used.
		RenderSystem.enableDepthTest();
		RenderSystem.depthFunc(515); // GL_LEQUAL - the literal vanilla itself uses

		RenderSystem.enableBlend();
		if (additive) {
			RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
		} else {
			RenderSystem.defaultBlendFunc();
		}

		return Tesselator.getInstance().begin(mode, DefaultVertexFormat.POSITION);
	}

	static void end(BufferBuilder builder) {
		// build() (not buildOrThrow()) - a caller can legitimately emit zero vertices (e.g. a hole with no
		// solid neighbour on any side has nothing to draw), and buildOrThrow() on an empty buffer crashes the
		// whole client. Found 2026-09-25 in the first real playtest.
		var mesh = builder.build();
		if (mesh != null) {
			BufferUploader.drawWithShader(mesh);
		}
		RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
		RenderSystem.enableCull();
		RenderSystem.disableDepthTest();
		RenderSystem.disableBlend();
		RenderSystem.defaultBlendFunc();
	}

	private static void setUniform(String name, float value) {
		if (instance == null) return;
		var uniform = instance.getUniform(name);
		if (uniform != null) {
			uniform.set(value);
		}
	}
}
